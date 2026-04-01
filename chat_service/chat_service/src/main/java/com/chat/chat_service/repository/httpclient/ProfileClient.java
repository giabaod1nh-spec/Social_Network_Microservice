package com.chat.chat_service.repository.httpclient;

import com.chat.chat_service.dto.response.ApiResponse;
import com.chat.chat_service.dto.response.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "profile-service" , url = "${app.service.profile}")
public interface ProfileClient {
    @GetMapping("/internal/profile/{userId}")
    ApiResponse<UserProfileResponse> getUserProfileById(@PathVariable String userId);
}
