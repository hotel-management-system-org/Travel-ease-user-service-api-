package com.travel_ease.hotel_system.api;

import com.travel_ease.hotel_system.dto.request.LoginRequestDto;
import com.travel_ease.hotel_system.dto.request.PasswordRequestDto;
import com.travel_ease.hotel_system.dto.request.UserRequestDto;
import com.travel_ease.hotel_system.dto.request.UserUpdateRequestDto;
import com.travel_ease.hotel_system.dto.response.UserResponseDto;
import com.travel_ease.hotel_system.service.UserService;
import com.travel_ease.hotel_system.service.impl.JwtService;
import com.travel_ease.hotel_system.util.StandardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService systemUserService;
    private final JwtService jwtService;

    @PostMapping("/visitors/signup")
    public ResponseEntity<StandardResponseDto> createUser(
                @RequestBody UserRequestDto dto
    ){
        systemUserService.createUser(dto);
        return new ResponseEntity<>(
                new StandardResponseDto(201,"user account was created",null),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/visitors/resend")
    public ResponseEntity<StandardResponseDto> resend(
            @RequestParam String email,
            @RequestParam String type
    ){
        systemUserService.resend(email, type);
        return new ResponseEntity<>(
                new StandardResponseDto(200,"please check you email",null),
                HttpStatus.OK
        );
    }

    @PostMapping("/visitors/forgot-password-request-code")
    public ResponseEntity<StandardResponseDto> forgotPasswordRequest(
            @RequestParam String email
    ){
        systemUserService.forgotPasswordSendVerificationCode(email);
        return new ResponseEntity<>(
                new StandardResponseDto(200,"please check you email",null),
                HttpStatus.OK
        );
    }

    @PostMapping("/visitors/verify-reset")
    public ResponseEntity<StandardResponseDto> verifyReset(
            @RequestParam String email,
            @RequestParam String otp
    ){
        boolean isVerified = systemUserService.verifyReset(otp,email);
        return new ResponseEntity<>(
                new StandardResponseDto(isVerified?200:400,isVerified?"Verified":"try Again",isVerified),
                isVerified?HttpStatus.OK:HttpStatus.BAD_REQUEST
        );
    }

    @PostMapping("/visitors/reset-password")
    public ResponseEntity<StandardResponseDto> resetPassword(
            @RequestBody PasswordRequestDto dto
    ){
        boolean isChanged = systemUserService.passwordReset(dto);
        return new ResponseEntity<>(
                new StandardResponseDto(isChanged?201:400,isChanged?"CHANGED":"try Again",isChanged),
                isChanged?HttpStatus.CREATED:HttpStatus.BAD_REQUEST
        );
    }

    @PostMapping("/visitors/verify-email")
    public ResponseEntity<StandardResponseDto> verifyEmail(
            @RequestParam String email,
            @RequestParam String otp
    ){
        boolean isVerified = systemUserService.verifyEmail(otp,email);
        return new ResponseEntity<>(
                new StandardResponseDto(isVerified?200:400,isVerified?"Verified":"try Again",isVerified),
                isVerified?HttpStatus.OK:HttpStatus.BAD_REQUEST
        );
    }

    @PostMapping("/visitors/login")
    public ResponseEntity<StandardResponseDto> login(
            @RequestBody LoginRequestDto dto
    ){
        return new ResponseEntity<>(
                new StandardResponseDto(200,"success",systemUserService.login(dto)),
                HttpStatus.OK
        );
    }

    @PostMapping("/visitors/refresh-token")
    public ResponseEntity<StandardResponseDto> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return new ResponseEntity<>(
                    new StandardResponseDto(400, "Refresh Token is required", null),
                    HttpStatus.BAD_REQUEST
            );
        }
        Map<String, String> tokens = systemUserService.generateNewAccessToken(refreshToken);
        return new ResponseEntity<>(
                new StandardResponseDto(200, "Token refreshed successfully", tokens),
                HttpStatus.OK
        );
    }


    @GetMapping("/get-user-details")
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPER_ADMIN')")
    public ResponseEntity<StandardResponseDto> getUserDetails(
            @RequestHeader("Authorization") String tokenHeader
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        String email = jwtService.getEmail(token);

        UserResponseDto userDetails = systemUserService.getUserDetails(email);

        return new ResponseEntity<>(
                new StandardResponseDto(200,
                        "user details!", userDetails),
                HttpStatus.OK
        );
    }

    @PostMapping("/update-user-details")
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPER_ADMIN')")
    public ResponseEntity<StandardResponseDto> updateUserDetails(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestBody UserUpdateRequestDto dto
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        String email = jwtService.getEmail(token);

        systemUserService.updateUserDetails(email,dto);

        return new ResponseEntity<>(
                new StandardResponseDto(201,
                        "user details updated!", null),
                HttpStatus.CREATED
        );
    }

}
