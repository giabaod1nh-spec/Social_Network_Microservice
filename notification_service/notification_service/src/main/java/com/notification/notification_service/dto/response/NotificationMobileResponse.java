package com.notification.notification_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.checkerframework.checker.units.qual.A;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE )
@Builder
public class NotificationMobileResponse {
    String tittle;
    String body;
    String message;
    String userNameSendMessage;
}
