package com.travel_ease.hotel_system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel_ease.hotel_system.exception.KeycloakException;
import com.travel_ease.hotel_system.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakServiceImpl implements KeycloakService {

    private final ObjectMapper objectMapper;

    public Map<String, Object> decodeToken(String accessToken) {
        try {

            String[] parts = accessToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));

            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);

            log.debug("Decoded token claims: {}", claims);
            return claims;

        } catch (Exception ex) {
            log.error("Error decoding token: {}", ex.getMessage(), ex);
            throw new KeycloakException("Failed to decode JWT token", ex);
        }
    }
}
