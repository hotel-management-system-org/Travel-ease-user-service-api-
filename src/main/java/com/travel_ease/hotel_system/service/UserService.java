package com.travel_ease.hotel_system.service;

import com.travel_ease.hotel_system.dto.request.LoginRequestDto;
import com.travel_ease.hotel_system.dto.request.PasswordRequestDto;
import com.travel_ease.hotel_system.dto.request.UserRequestDto;
import com.travel_ease.hotel_system.dto.request.UserUpdateRequestDto;
import com.travel_ease.hotel_system.dto.response.LoginResponseDto;
import com.travel_ease.hotel_system.dto.response.UserResponseDto;


public interface UserService {
    public void createUser(UserRequestDto dto);
    public LoginResponseDto login(LoginRequestDto dto);
    public void resend(String email, String type);
    public void forgotPasswordSendVerificationCode(String email);
    public boolean verifyReset(String otp, String email);
    public boolean passwordReset(PasswordRequestDto dto);
    public boolean verifyEmail(String otp, String email);
    public void updateUserDetails(String email, UserUpdateRequestDto data);
    public UserResponseDto getUserDetails(String email);
}
