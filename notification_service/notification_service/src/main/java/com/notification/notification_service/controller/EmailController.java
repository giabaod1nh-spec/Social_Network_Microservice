package com.notification.notification_service.controller;

import com.notification.notification_service.dto.ApiResponse;
import com.notification.notification_service.dto.request.VerifyEmailRequest;
import com.notification.notification_service.service.impl.EmailService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class EmailController {
    EmailService emailService;

    @PostMapping("/internal/verify_email")
    ApiResponse<Void> verifyEmailUser (@RequestBody VerifyEmailRequest request) throws IOException {
        emailService.emailVerfication(request.getUserEmail(), request.getUserName() , request.getToken());
        return ApiResponse.<Void>
                builder()
                .build();
    }

}
