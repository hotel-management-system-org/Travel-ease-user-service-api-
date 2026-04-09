package com.travel_ease.hotel_system.dto.event;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSendOtpEvent {
    private String user_id;
    private int otp;
    private String email;
    private String first_name;
    private String last_name;
}
