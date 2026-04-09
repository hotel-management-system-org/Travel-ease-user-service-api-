package com.travel_ease.hotel_system.util;

import com.travel_ease.hotel_system.dto.event.UserSendOtpEvent;
import com.travel_ease.hotel_system.dto.request.UserRequestDto;
import com.travel_ease.hotel_system.dto.response.UserResponseDto;
import com.travel_ease.hotel_system.entity.User;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ObjectMapper {

    private final FileDataExtractor fileDataExtractor;


    public UserRepresentation mapUserRepo(UserRequestDto dto, boolean isEmailVerified , boolean isEnable) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setUsername(dto.getEmail());
        user.setEnabled(isEnable);
        user.setEmailVerified(isEmailVerified);
        List<CredentialRepresentation> credList = new ArrayList<>();
        CredentialRepresentation cred = new CredentialRepresentation();
        cred.setTemporary(false);
        cred.setValue(dto.getPassword());
        credList.add(cred);
        user.setCredentials(credList);
        return user;
    }

  public List<String> extractRoles(Map<String, Object> tokenData) {
       Map<String, Object> realmAccess = (Map<String, Object>) tokenData.get("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            return (List<String>) realmAccess.get("roles");
        }
        return List.of();
    }

    public UserResponseDto mapToUserResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .contact(user.getContact())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .status(user.getStatus())
                .avatarUrl(user.getUserAvatar() != null ? fileDataExtractor.byteArrayToString(user.getUserAvatar().getResourceUrl()) : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }


    public UserSendOtpEvent toCreateEvent(User user, int otp) {
        System.out.println("Method level user first name " + user.getFirstName() );
        return UserSendOtpEvent.builder()
                .user_id(user.getId().toString())
                .otp(otp)
                .email(user.getEmail())
                .first_name(user.getFirstName())
                .last_name(user.getLastName())
                .build();
    }
}
