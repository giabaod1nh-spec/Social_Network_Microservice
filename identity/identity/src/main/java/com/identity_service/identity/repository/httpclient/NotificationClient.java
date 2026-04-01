package com.identity_service.identity.repository.httpclient;

import com.identity_service.identity.dto.request.VerifyEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.awt.*;

@FeignClient(name = "notification-service" , url = "${app.services.notify}" )
public interface NotificationClient {
    @PostMapping(value = "/internal/verify_email" , produces = MediaType.APPLICATION_JSON_VALUE)
    void verifyEmailUser(@RequestBody VerifyEmailRequest request);
}
