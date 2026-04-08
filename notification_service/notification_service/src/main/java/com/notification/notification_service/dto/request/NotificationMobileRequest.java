package com.notification.notification_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class NotificationMobileRequest {
    String userId;
    String tittle;
    String body;
    String message;
    String userNameSendMessage;
}
