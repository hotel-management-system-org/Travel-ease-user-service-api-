package com.travel_ease.hotel_system.api;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.travel_ease.hotel_system.service.UserAvatarService;
import com.travel_ease.hotel_system.service.impl.JwtService;
import com.travel_ease.hotel_system.util.StandardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.sql.SQLException;

@RestController
@RequestMapping("/user-service/api/v1/avatars")
@RequiredArgsConstructor
public class SystemAvatarController {

    private final UserAvatarService avatarService;
    private final JwtService jwtService;

    @PostMapping(value = "/user/manage-avatar",consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPER_ADMIN')")
    public ResponseEntity<StandardResponseDto> manageAvatar(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestParam("avatar") MultipartFile avatar) throws SQLException, JsonProcessingException {
        String token = tokenHeader.replace("Bearer ", "");
        String email = jwtService.getEmail(token);
/*
        ObjectMapper objectMapper = new ObjectMapper();
        RequestSystemUserAvatarDto dto = objectMapper.readValue(data, RequestSystemUserAvatarDto.class);*/
        avatarService.createSystemUserAvatar(avatar,email);
        return new ResponseEntity<>(
                new StandardResponseDto(
                        201, "Avatar was Updated", null
                ),
                HttpStatus.CREATED
        );
    }
}