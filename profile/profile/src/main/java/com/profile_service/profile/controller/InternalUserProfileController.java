package com.profile_service.profile.controller;

import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.response.ApiResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.service.impl.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@RequestMapping("/info")
public class InternalUserProfileController {
    UserProfileService userProfileService;
    @PostMapping(value = "/internal/create")
    ApiResponse<UserProfileResponse> createProfile(@RequestBody ProfileCreationRequest request ){

        return ApiResponse.<UserProfileResponse>builder()
                .message("Create profile success ")
                .result(userProfileService.createProfile(request ))
                .build();
    }

    @GetMapping("/internal/profile/{userId}")
    ApiResponse<UserProfileResponse> getUserProfileById(@PathVariable String userId){

        return ApiResponse.<UserProfileResponse>
                builder()
                .message("Get user profile success")
                .result(userProfileService.getUserProfileInternal(userId))
                .build();
    }

}
