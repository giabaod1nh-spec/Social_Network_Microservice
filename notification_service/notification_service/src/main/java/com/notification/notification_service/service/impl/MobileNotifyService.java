package com.notification.notification_service.service.impl;

import com.google.firebase.messaging.*;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.dto.response.NotificationMobileResponse;
import com.notification.notification_service.entity.UserDevices;
import com.notification.notification_service.repository.UserDeviceRepository;
import com.notification.notification_service.service.IMobileNotifyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Slf4j(topic = "MOBILE_NOTIFY_SERVICE")
public class MobileNotifyService implements IMobileNotifyService {
    UserDeviceRepository userDeviceRepository;

    @Override
    public void registerDevice(RegisterDeviceRequest request) {

        UserDevices device = UserDevices.builder()
                .userId(request.getUserId())
                .deviceToken(request.getTokenDevice())
                .build();

        userDeviceRepository.save(device);
        log.info("Register device thanh cong");
    }

    @Override
    public void sendMobileNotification(NotificationMobileRequest request) throws FirebaseMessagingException {

        List<UserDevices> userDevices
                = userDeviceRepository.findAllByUserId(request.getUserId());

        List<String> tokens = userDevices.stream().map(UserDevices::getDeviceToken)
                .distinct()
                .toList();

        Notification notification = Notification.builder()
                .setTitle(request.getTittle())
                .setBody(request.getBody())
                .build();


        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(notification)
                .build();

        BatchResponse response =
                FirebaseMessaging.getInstance()
                        .sendEachForMulticast(message);

        System.out.println("Success: " + response.getSuccessCount());
        System.out.println("Failed: " + response.getFailureCount());
    }

}