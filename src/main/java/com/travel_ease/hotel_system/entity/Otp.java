package com.travel_ease.hotel_system.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Otp {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "property_id",columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "code", length = 80, nullable = false)
    private String code;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "attempts")
    private Integer attempts;

    @OneToOne()
    @JoinColumn(name = "user_id",nullable = false,unique = true)
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
