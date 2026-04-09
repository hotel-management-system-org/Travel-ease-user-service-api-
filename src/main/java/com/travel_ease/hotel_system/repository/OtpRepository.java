package com.travel_ease.hotel_system.repository;

import com.travel_ease.hotel_system.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OtpRepository extends JpaRepository<Otp, UUID> {
    Optional<Otp> findByUserId(UUID userId);
}
