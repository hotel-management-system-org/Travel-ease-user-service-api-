package com.travel_ease.hotel_system.service;

import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;

public interface UserAvatarService {
    void createSystemUserAvatar(MultipartFile avatar, String email) throws SQLException;
}