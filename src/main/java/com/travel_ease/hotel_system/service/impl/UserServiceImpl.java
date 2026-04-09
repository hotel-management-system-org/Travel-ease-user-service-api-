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
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
    private final OtpGenerator otpGenerator;
    private final FileDataExtractor fileDataExtractor;
    private final ObjectMapper objectMapper;

    @Override
    public void createUser(UserRequestDto dto) {
        if (dto.getFirstName() == null || dto.getFirstName().trim().isEmpty()) {
            throw new BadRequestException("First name is required");
        }

        if (dto.getLastName() == null || dto.getLastName().trim().isEmpty()) {
            throw new BadRequestException("Last name is required");
        }

        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new BadRequestException("Email is required");
        }

        String userId;
        Keycloak keycloak;
        UserRepresentation existingUser;

        keycloak = keycloakConfig.getKeycloakInstance();

        existingUser = keycloak.realm(realm).users().search(dto.getEmail()).stream()
                .findFirst().orElse(null);

        if (existingUser != null) {
            Optional<User> selectedUserFromUserService = userRepository.findByEmail(dto.getEmail());

            if (selectedUserFromUserService.isEmpty()) {
                keycloak.realm(realm).users().delete(dto.getEmail());
            } else {
                throw new DuplicateEntryException("Email already exists");
            }
        } else {
            Optional<User> selectedUserFromUserService = userRepository.findByEmail(dto.getEmail());

            if (selectedUserFromUserService.isPresent()) {
                Optional<Otp> selectedOtp = otpRepository.findByUserId(selectedUserFromUserService.get().getId());

                selectedOtp
                        .ifPresent(otp -> otpRepository.deleteById(otp.getId()));

                userRepository.deleteById(selectedUserFromUserService.get().getId());

            }
        }

        UserRepresentation userRepresentation = objectMapper.mapUserRepo(dto, false, false);
        Response response = keycloak.realm(realm).users().create(userRepresentation);
        if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
            RoleRepresentation userRole = keycloak.realm(realm).roles().get("CUSTOMER").toRepresentation();
            userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Arrays.asList(userRole));
            UserRepresentation createUser = keycloak.realm(realm).users().get(userId).toRepresentation();

            User user = User.builder()
                    .id(UUID.randomUUID())
                    .keycloakId(createUser.getId())
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

            //send email user email service

            try{
                eventPublisher.publishUserSendOtp(objectMapper.toCreateEvent(savedUser,Integer.parseInt(createOtp.getCode())));
            }catch (Exception e){
                log.error("Failed to publish user created event", e);

            }

        }


    }

    @Override
    @Transactional
    public LoginResponseDto login(LoginRequestDto dto) {
       /* User selectedUser = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));*/

       /* if (!selectedUser.isEmailVerified()) {
            resend(dto.getEmail(), "SIGNUP");
            throw new UnAuthorizedException("Please verify your email");
        }*/



        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("client_id", clientId);
        requestBody.add("client_secret", clientSecret);
        requestBody.add("grant_type", OAuth2Constants.PASSWORD);
        requestBody.add("username", dto.getEmail());
        requestBody.add("password", dto.getPassword());


        RestTemplate restTemplate = new RestTemplate();

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
            }else{
                User user = validateRegularUserLogin(dto.getEmail());
                loginResponse.setUser(objectMapper.mapToUserResponse(user));
            }

            log.info("Login successful for: {}", dto.getEmail());
            return loginResponse;


        } catch (Exception ex) {
            log.error("Login failed: {}", ex.getMessage(), ex);
            throw new KeycloakException("Login failed: " + ex.getMessage(), ex);
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
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }

            User systemUser = selectedUser.get();

            if (type.equalsIgnoreCase("SIGNUP")) {
                if (systemUser.isEmailVerified()) {
                    throw new DuplicateEntryException("The email is already activated");
                }
            }

            Otp selectedOtpObj = systemUser.getOtp();
            String code = otpGenerator.generateOtp(5);

/*
            send email user email service
            emailService.sendUserSignupVerificationCode(systemUser.getEmail(), "verify your email", code, systemUser.getFirstName());
*/

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
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }

            User systemUser = selectedUser.get();

            Keycloak keycloak = null;
            keycloak = keycloakConfig.getKeycloakInstance();
            UserRepresentation existingUser =
                    keycloak.realm(realm).users().search(email).stream().findFirst().orElse(null);

            if (existingUser == null) {
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }


            Otp selectedOtpObj = systemUser.getOtp();
            String code = otpGenerator.generateOtp(5);


            selectedOtpObj.setAttempts(0);
            selectedOtpObj.setCode(code);
            selectedOtpObj.setIsVerified(false);
            otpRepository.save(selectedOtpObj);

            //send email use email service
            /* emailService.sendUserSignupVerificationCode(systemUser.getEmail(), "verify your email to reset the password", code, systemUser.getFirstName());*/
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean verifyReset(String otp, String email) {
        try {
            Optional<User> selectedUser = userRepository.findByEmail(email);
            if (selectedUser.isEmpty()) {
                throw new EntryNotFoundException("unable to find any users associated with the provided email address");
            }

            User systemUserOb = selectedUser.get();
            Otp otpOb = systemUserOb.getOtp();

            if (otpOb.getCode().equals(otp)) {
                //otpRepo.deleteById(otpOb.getPropertyId());
                otpOb.setAttempts(otpOb.getAttempts() + 1);
                otpOb.setIsVerified(true);
                otpRepository.save(otpOb);
                return true;
            } else {

                if (otpOb.getAttempts() >= 5) {
                    resend(email, "PASSWORD");
                    throw new BadRequestException("you have a new verification code");

                }

                otpOb.setAttempts(otpOb.getAttempts() + 1);
                otpRepository.save(otpOb);
                return false;
            }

        } catch (Exception e) {
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
            throw new BadRequestException("try again");
        }
        throw new EntryNotFoundException("unable to find!");
    }

    @Override
    @Transactional
    public boolean verifyEmail(String otp, String email) {
        Optional<User> selectedUserObj = userRepository.findByEmail(email);
        if (selectedUserObj.isEmpty()) {
            throw new EntryNotFoundException("cant find the associated user");
        }
        User user = selectedUserObj.get();
        Otp otpObj = user.getOtp();

        if (otpObj.getIsVerified()) {
            throw new BadRequestException("this otp has been used");
        }

        if (otpObj.getAttempts() >= 5) {
            resend(email, "SIGNUP");
            return false;
        }

        if (otpObj.getCode().equals(otp)) {
            UserRepresentation keycloakUser = keycloakConfig.getKeycloakInstance().realm(realm)
                    .users()
                    .search(email)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new EntryNotFoundException("user not found"));

            keycloakUser.setEmailVerified(true);
            keycloakUser.setEnabled(true);

            keycloakConfig.getKeycloakInstance().realm(realm)
                    .users().get(keycloakUser.getId()).update(keycloakUser);

            user.setEmailVerified(true);
            user.setIsEnabled(true);
            user.setStatus(UserStatus.ACTIVE);
            user.setActive(true);

            userRepository.save(user);

            otpObj.setIsVerified(true);
            otpObj.setAttempts(otpObj.getAttempts() + 1);



            return true;
        } else {
            otpObj.setAttempts(otpObj.getAttempts() + 1);

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
            byEmail.get().setFirstName(data.getFirstName());
            byEmail.get().setLastName(data.getLastName());
            System.out.println("Keycloak user " + keyCloakUser.getFirstName());
            userRepository.save(systemUser);
        }

}

        @Override
        public UserResponseDto getUserDetails(String email){
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
                                .isEmailVerified(true) // Auto-verified
                                .status(UserStatus.ACTIVE)
                                .isAccountNonExpired(true)
                                .isAccountNonLocked(true)
                                .isCredentialsNonExpired(true)
                                .isEnabled(true)
                                .build();

                        User savedUser = userRepository.save(superAdmin);
                        /*createUserProfile(savedUser); use user profile service */

                        // Publish Kafka event
                      /*  kafkaProducerService.publishUserCreatedEvent(Map.of(
                                "userId", savedUser.getId().toString(),
                                "email", savedUser.getEmail(),
                                "role", "SUPER_ADMIN",
                                "autoProvisioned", true,
                                "timestamp", LocalDateTime.now().toString()
                        ));*/

                        log.info("SUPER_ADMIN auto-provisioned successfully: {}", savedUser.getEmail());
                        return savedUser;
                    });
        }
}
/*

    @Transactional
    public UserAvatar createUserProfile(User user) {
        log.info("Creating default profile for user: {}", user.getId());

        UserAvatar profile = UserAvatar.builder()
                .id(UUID.randomUUID())
                .user(user)
                .build();

        UserAvatar savedProfile = userAvatarRepository.save(profile);
        log.info("Default profile created for user: {}", user.getId());
        return savedProfile;
    }
*/


