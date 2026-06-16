package com.travel_ease.hotel_system.service.impl;

import com.travel_ease.hotel_system.config.KeycloakConfig;
import com.travel_ease.hotel_system.dto.request.LoginRequestDto;
import com.travel_ease.hotel_system.dto.request.PasswordRequestDto;
import com.travel_ease.hotel_system.dto.request.UserRequestDto;
import com.travel_ease.hotel_system.dto.request.UserUpdateRequestDto;
import com.travel_ease.hotel_system.dto.response.LoginResponseDto;
import com.travel_ease.hotel_system.dto.response.UserResponseDto;
import com.travel_ease.hotel_system.entity.Otp;
import com.travel_ease.hotel_system.entity.User;
import com.travel_ease.hotel_system.entity.UserAvatar;
import com.travel_ease.hotel_system.enums.UserRole;
import com.travel_ease.hotel_system.enums.UserStatus;
import com.travel_ease.hotel_system.event.UserEventPublisher;
import com.travel_ease.hotel_system.exception.*;
import com.travel_ease.hotel_system.repository.OtpRepository;
import com.travel_ease.hotel_system.repository.UserRepository;
import com.travel_ease.hotel_system.service.KeycloakService;
import com.travel_ease.hotel_system.service.UserService;
import com.travel_ease.hotel_system.util.FileDataExtractor;
import com.travel_ease.hotel_system.util.ObjectMapper;
import com.travel_ease.hotel_system.util.OtpGenerator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.token-uri}")
    private String keycloakApiUrl;

    @Value("${spring.security.oauth2.resourceserver.jwt.token-uri}")
    private String keycloakApiTokenUri;

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final KeycloakConfig keycloakConfig;
    private final KeycloakService keycloakService;
    private final UserEventPublisher eventPublisher;
    private final RestTemplate restTemplate;
    private final OtpGenerator otpGenerator;
    private final FileDataExtractor fileDataExtractor;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createUser(UserRequestDto dto) {
        log.info("Registering user with email: {}", dto.getEmail());


        if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
            throw new BadRequestException("First name is required");
        }
        if (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) {
            throw new BadRequestException("Last name is required");
        }
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new BadRequestException("Email is required");
        }

        Keycloak masterAdminClient = keycloakConfig.getKeycloakInstance();
        UsersResource usersResource = masterAdminClient.realm(realm).users();

        List<UserRepresentation> existingUsers = usersResource.search(dto.getEmail());

        if (existingUsers != null && !existingUsers.isEmpty()) {
            Optional<User> selectedUserFromUserService = userRepository.findByEmail(dto.getEmail());

            if (selectedUserFromUserService.isEmpty()) {
                log.warn("User existed in Keycloak but missing in DB. Purging Keycloak user: {}", dto.getEmail());
                usersResource.delete(existingUsers.get(0).getId());
            } else {
                throw new DuplicateEntryException("Email already exists");
            }
        } else {
            Optional<User> selectedUserFromUserService = userRepository.findByEmail(dto.getEmail());
            if (selectedUserFromUserService.isPresent()) {
                Optional<Otp> selectedOtp = otpRepository.findByUserId(selectedUserFromUserService.get().getId());
                selectedOtp.ifPresent(otp -> otpRepository.deleteById(otp.getId()));
                userRepository.deleteById(selectedUserFromUserService.get().getId());
            }
        }

        UserRepresentation userRepresentation = objectMapper.mapUserRepo(dto, false, false);
        Response response = usersResource.create(userRepresentation);

        if (response.getStatus() != HttpStatus.CREATED.value()) {
            if (response.getStatus() == HttpStatus.CONFLICT.value()) {
                throw new DuplicateEntryException("User already exists in Keycloak");
            }
            throw new InternalServerError("Failed to create user in Identity Provider. Status: " + response.getStatus());
        }

        String keycloakUserId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
        log.info("User created successfully in Keycloak with ID: {}", keycloakUserId);

        try {
            RoleRepresentation userRole = masterAdminClient.realm(realm).roles().get("CUSTOMER").toRepresentation();
            usersResource.get(keycloakUserId).roles().realmLevel().add(Collections.singletonList(userRole));

            UserRepresentation createdKeycloakUser = usersResource.get(keycloakUserId).toRepresentation();

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .keycloakId(createdKeycloakUser.getId())
                    .email(dto.getEmail())
                    .firstName(dto.getFirstName())
                    .lastName(dto.getLastName())
                    .contact(dto.getContact())
                    .status(UserStatus.PENDING)
                    .role(UserRole.CUSTOMER)
                    .isActive(false)
                    .isAccountNonExpired(true)
                    .isAccountNonLocked(true)
                    .isCredentialsNonExpired(true)
                    .isEnabled(false)
                    .isEmailVerified(false)
                    .build();

            User savedUser = userRepository.save(user);
            Otp createOtp = Otp.builder()
                    .id(UUID.randomUUID())
                    .code(otpGenerator.generateOtp(5))
                    .attempts(0)
                    .isVerified(false)
                    .user(savedUser)
                    .build();
            otpRepository.save(createOtp);
            
            try {
                eventPublisher.publishUserSendOtp(objectMapper.toCreateEvent(savedUser, Integer.parseInt(createOtp.getCode())));
            } catch (Exception e) {
                log.error("Failed to publish user created event to Kafka", e);
            }

        } catch (Exception e) {
            log.error("Database or role assignment transaction failed. Rolling back Keycloak user: {}", keycloakUserId, e);
            try {
                usersResource.get(keycloakUserId).remove();
            } catch (Exception rollbackEx) {
                log.error("Critical failure: Failed to rollback/delete Keycloak user during cascade error", rollbackEx);
            }
            throw new InternalServerError("Failed to finalize user registration workflow");
        }
    }

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", clientId);
        requestBody.add("client_secret", clientSecret);
        requestBody.add("grant_type", OAuth2Constants.PASSWORD);
        requestBody.add("username", dto.getEmail());
        requestBody.add("password", dto.getPassword());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    keycloakApiTokenUri,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> tokenResponse = response.getBody();

            LoginResponseDto loginResponse = LoginResponseDto.builder()
                    .accessToken((String) tokenResponse.get("access_token"))
                    .refreshToken((String) tokenResponse.get("refresh_token"))
                    .expiresIn(((Number) tokenResponse.get("expires_in")).longValue())
                    .tokenType((String) tokenResponse.get("token_type"))
                    .build();

            Map<String, Object> decodeToken = keycloakService.decodeToken(loginResponse.getAccessToken());
            List<String> roles = objectMapper.extractRoles(decodeToken);
            String keycloakUserId = (String) decodeToken.get("sub");

            if (roles.contains("SUPER_ADMIN")) {
                log.info("SUPER_ADMIN login detected for Keycloak user: {}", keycloakUserId);
                User user = provisionSuperAdminIfNotExists(keycloakUserId, decodeToken);
                loginResponse.setUser(objectMapper.mapToUserResponse(user));
            } else {
                User user = validateRegularUserLogin(dto.getEmail());
                loginResponse.setUser(objectMapper.mapToUserResponse(user));
            }

            log.info("Login successful for: {}", dto.getEmail());
            return loginResponse;

        } catch (HttpClientErrorException ex) {
            log.error("Login failed: {}", ex.getMessage(), ex);
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new UnAuthorizedException("Invalid email or password");
            }
            throw new KeycloakException("Authentication service unavailable: " + ex.getMessage(), ex);
        }
    }

    private User validateRegularUserLogin(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found. Please register first."));

        if (!user.isEmailVerified()) {
            throw new UnAuthorizedException("Please verify your email before logging in");
        }

        if (user.getStatus() == UserStatus.BLOCKED || user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnAuthorizedException("Your account has been " + user.getStatus());
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }

        return user;
    }

    @Override
    public void resend(String email, String type) {
        try {
            Optional<User> selectedUser = userRepository.findByEmail(email);
            if (selectedUser.isEmpty()) {
                throw new EntryNotFoundException("Unable to find any users associated with the provided email address");
            }

            User systemUser = selectedUser.get();

            if (type.equalsIgnoreCase("SIGNUP") && systemUser.isEmailVerified()) {
                throw new DuplicateEntryException("The email is already activated");
            }

            Otp selectedOtpObj = systemUser.getOtp();
            String code = otpGenerator.generateOtp(5);

            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            otpRepository.save(selectedOtpObj);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void forgotPasswordSendVerificationCode(String email) {
        try {
            Optional<User> selectedUser = userRepository.findByEmail(email);
            if (selectedUser.isEmpty()) {
                throw new EntryNotFoundException("Unable to find any users associated with the provided email address");
            }

            User systemUser = selectedUser.get();
            Keycloak keycloak = keycloakConfig.getKeycloakInstance();

            UserRepresentation existingUser = keycloak.realm(realm).users().search(email)
                    .stream().findFirst()
                    .orElseThrow(() -> new EntryNotFoundException("Unable to find any users associated with the provided email address"));

            Otp selectedOtpObj = systemUser.getOtp();
            String code = otpGenerator.generateOtp(5);

            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            otpRepository.save(selectedOtpObj);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean verifyReset(String otp, String email) {
        Optional<User> selectedUser = userRepository.findByEmail(email);
        if (selectedUser.isEmpty()) {
            throw new EntryNotFoundException("Unable to find any users associated with the provided email address");
        }

        User systemUserOb = selectedUser.get();
        Otp otpOb = systemUserOb.getOtp();

        if (otpOb.getAttempts() >= 5) {
            throw new BadRequestException("Maximum attempts reached. Please request a new verification code.");
        }

        if (otpOb.getCode().equals(otp)) {
            otpOb.setAttempts(0);
            otpOb.setIsVerified(true);
            otpRepository.save(otpOb);
            return true;
        } else {
            int newAttempts = otpOb.getAttempts() + 1;
            otpOb.setAttempts(newAttempts);
            otpRepository.save(otpOb);

            if (newAttempts >= 5) {
                throw new BadRequestException("Account locked due to too many invalid OTP attempts. Please resend a new code.");
            }
            return false;
        }
    }

    @Override
    public boolean passwordReset(PasswordRequestDto dto) {
        Optional<User> selectedUserObj = userRepository.findByEmail(dto.getEmail());
        if (selectedUserObj.isPresent()) {
            User systemUser = selectedUserObj.get();
            Otp otpObj = systemUser.getOtp();
            Keycloak keycloak = keycloakConfig.getKeycloakInstance();

            List<UserRepresentation> keyCloakUsers = keycloak.realm(realm).users().search(systemUser.getEmail());
            if (!keyCloakUsers.isEmpty() && otpObj.getCode().equals(dto.getCode())) {
                UserRepresentation keyCloakUser = keyCloakUsers.get(0);
                UserResource userResource = keycloak.realm(realm).users().get(keyCloakUser.getId());

                CredentialRepresentation newPass = new CredentialRepresentation();
                newPass.setType(CredentialRepresentation.PASSWORD);
                newPass.setValue(dto.getPassword());
                newPass.setTemporary(false);
                userResource.resetPassword(newPass);

                userRepository.save(systemUser);
                return true;
            }
            throw new BadRequestException("Try again");
        }
        throw new EntryNotFoundException("Unable to find user!");
    }

    @Override
    @Transactional
    public boolean verifyEmail(String otp, String email) {
        Optional<User> selectedUserObj = userRepository.findByEmail(email);
        if (selectedUserObj.isEmpty()) {
            throw new EntryNotFoundException("Can't find the associated user");
        }
        User user = selectedUserObj.get();
        Otp otpObj = user.getOtp();

        if (otpObj.getIsVerified()) {
            throw new BadRequestException("This OTP has been used");
        }

        if (otpObj.getAttempts() >= 5) {
            resend(email, "SIGNUP");
            return false;
        }

        if (otpObj.getCode().equals(otp)) {
            Keycloak keycloak = keycloakConfig.getKeycloakInstance();
            UserRepresentation keycloakUser = keycloak.realm(realm).users().search(email)
                    .stream().findFirst()
                    .orElseThrow(() -> new EntryNotFoundException("User not found in Identity Provider"));

            keycloakUser.setEmailVerified(true);
            keycloakUser.setEnabled(true);

            keycloak.realm(realm).users().get(keycloakUser.getId()).update(keycloakUser);

            user.setEmailVerified(true);
            user.setIsEnabled(true);
            user.setStatus(UserStatus.ACTIVE);
            user.setActive(true);

            userRepository.save(user);

            otpObj.setIsVerified(true);
            otpObj.setAttempts(otpObj.getAttempts() + 1);
            otpRepository.save(otpObj);

            return true;
        } else {
            otpObj.setAttempts(otpObj.getAttempts() + 1);
            otpRepository.save(otpObj);
        }
        return false;
    }

    @Override
    public void updateUserDetails(String email, UserUpdateRequestDto data) {
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found");
        }

        User systemUser = byEmail.get();
        Keycloak keycloak = keycloakConfig.getKeycloakInstance();
        List<UserRepresentation> keyCloakUsers = keycloak.realm(realm).users().search(systemUser.getEmail());

        if (!keyCloakUsers.isEmpty()) {
            UserRepresentation keyCloakUser = keyCloakUsers.get(0);
            keyCloakUser.setFirstName(data.getFirstName());
            keyCloakUser.setLastName(data.getLastName());

            keycloak.realm(realm).users().get(keyCloakUser.getId()).update(keyCloakUser);

            systemUser.setFirstName(data.getFirstName());
            systemUser.setLastName(data.getLastName());
            userRepository.save(systemUser);
        }
    }

    @Override
    public UserResponseDto getUserDetails(String email) {
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isEmpty()) {
            throw new EntryNotFoundException("User was not found");
        }

        UserAvatar userAvatar = byEmail.get().getUserAvatar();

        return UserResponseDto.builder()
                .email(byEmail.get().getEmail())
                .firstName(byEmail.get().getFirstName())
                .lastName(byEmail.get().getLastName())
                .avatarUrl(userAvatar != null ? fileDataExtractor.byteArrayToString(userAvatar.getResourceUrl()) : null)
                .build();
    }

    @Override
    public Map generateNewAccessToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("formData.add(\"client_secret\", clientSecret);", clientSecret);
        formData.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(keycloakApiUrl, request, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
    }

    @Transactional
    public User provisionSuperAdminIfNotExists(String keycloakUserId, Map<String, Object> tokenData) {
        log.info("Checking SUPER_ADMIN provisioning for Keycloak user: {}", keycloakUserId);

        return userRepository.findByKeycloakId(keycloakUserId)
                .orElseGet(() -> {
                    log.info("SUPER_ADMIN not found in database. Auto-provisioning...");

                    String email = (String) tokenData.get("email");
                    String firstName = (String) tokenData.getOrDefault("given_name", "Super");
                    String lastName = (String) tokenData.getOrDefault("family_name", "Admin");

                    User superAdmin = User.builder()
                            .id(UUID.randomUUID())
                            .keycloakId(keycloakUserId)
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(email)
                            .contact("")
                            .role(UserRole.SUPER_ADMIN)
                            .isEmailVerified(true)
                            .status(UserStatus.ACTIVE)
                            .isAccountNonExpired(true)
                            .isAccountNonLocked(true)
                            .isCredentialsNonExpired(true)
                            .isEnabled(true)
                            .isEmailVerified(true)
                            .build();

                    User savedUser = userRepository.save(superAdmin);
                    log.info("SUPER_ADMIN auto-provisioned successfully: {}", savedUser.getEmail());
                    return savedUser;
                });
    }
}