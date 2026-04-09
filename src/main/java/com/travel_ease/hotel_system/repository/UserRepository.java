package com.travel_ease.hotel_system.repository;

import com.travel_ease.hotel_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByKeycloakId(String keycloakUserId);

}
