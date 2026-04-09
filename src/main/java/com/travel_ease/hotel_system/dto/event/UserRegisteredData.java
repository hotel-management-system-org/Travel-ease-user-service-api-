package com.travel_ease.hotel_system.dto.event;

public record UserRegisteredData(
        String userId,
        String email,
        String firstName,
        String lastName,
        String otp
) {}