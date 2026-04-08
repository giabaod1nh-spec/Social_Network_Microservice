package com.notification.notification_service.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.notification.notification_service.dto.ApiResponse;
import com.notification.notification_service.dto.request.NotificationMobileRequest;
import com.notification.notification_service.dto.request.RegisterDeviceRequest;
import com.notification.notification_service.dto.response.NotificationMobileResponse;
import com.notification.notification_service.service.impl.MobileNotifyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@RequiredArgsConstructor
public class MobileNotifyController {
    MobileNotifyService mobileNotifyService;

    @PostMapping("/register")
    ApiResponse<Void> registerDevices(@RequestBody RegisterDeviceRequest request){
        mobileNotifyService.registerDevice(request);
        return ApiResponse.<Void>
                builder()
                .message("Register device thanh cong")
                .build();
    }

    @PostMapping("/notify")
    ApiResponse<Void> sendMobileNotification(@RequestBody NotificationMobileRequest request) throws FirebaseMessagingException {
        mobileNotifyService.sendMobileNotification(request);
        return ApiResponse.<Void> builder()
                .message("Send notification success")
                .build();
    }
}
