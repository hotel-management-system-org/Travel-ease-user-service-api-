package com.travel_ease.hotel_system.dto.event;

import java.time.LocalDateTime;

public record KafkaEvent<T>(
        String event_type,
        LocalDateTime occurred_at,
        T data
) {}

