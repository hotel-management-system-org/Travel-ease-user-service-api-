package com.travel_ease.hotel_system.service;

import java.util.Map;

public interface KeycloakService {
    public Map<String, Object> decodeToken(String accessToken);
}
