package com.notification.notification_service.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.dto.response.NotificationMobileResponse;

public interface IMobileNotifyService {
    void registerDevice(RegisterDeviceRequest request);
    void sendMobileNotification(NotificationMobileRequest request) throws FirebaseMessagingException;
}
