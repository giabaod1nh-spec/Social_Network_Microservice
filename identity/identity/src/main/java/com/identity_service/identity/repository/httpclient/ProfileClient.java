package com.identity_service.identity.repository.httpclient;

import com.identity_service.identity.configuration.AuthenticationRequestInterceptor;
import com.identity_service.identity.dto.request.ProfileCreationRequest;
import com.identity_service.identity.dto.response.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.awt.*;

@FeignClient(name = "profile-service" , url = "${app.services.profile}" ,
        configuration ={AuthenticationRequestInterceptor.class} )
public interface ProfileClient {
    @PostMapping(value = "/internal/create" , produces = MediaType.APPLICATION_JSON_VALUE)
    UserProfileResponse createProfile(
            @RequestBody ProfileCreationRequest request);
}
