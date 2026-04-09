package com.travel_ease.hotel_system.repository;

import com.travel_ease.hotel_system.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
}
