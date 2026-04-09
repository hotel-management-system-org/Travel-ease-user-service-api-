package com.travel_ease.hotel_system.event;

import com.travel_ease.hotel_system.dto.event.KafkaEvent;
import com.travel_ease.hotel_system.dto.event.UserEventTypes;
import com.travel_ease.hotel_system.dto.event.UserRegisteredData;
import com.travel_ease.hotel_system.dto.event.UserSendOtpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.send-otp}")
    private String sendUserOtp;

    public void publishUserSendOtp(UserSendOtpEvent user) {
        KafkaEvent<UserRegisteredData> event =
                new KafkaEvent<>(
                        UserEventTypes.USER_OTP,
                        LocalDateTime.now(),
                        new UserRegisteredData(
                                user.getUser_id(),
                                user.getEmail(),
                                user.getFirst_name(),
                                user.getLast_name(),
                                String.valueOf(user.getOtp())
                        )
                );
        System.out.println("User kafka  publisher first name " + event.data().firstName());

        sendEvent(sendUserOtp,user.getUser_id(),event);
    }

    private void sendEvent(
            String topic,
            String key,
            Object event
    ) {

        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, key, event);



        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info(
                        "Event sent | topic={} | partition={} | offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            } else {
                log.error("❌ Failed to send event to topic={}", topic, ex);
            }
        });
    }

}
