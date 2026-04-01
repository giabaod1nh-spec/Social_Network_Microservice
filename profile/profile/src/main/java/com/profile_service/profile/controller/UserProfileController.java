package com.profile_service.profile.controller;

import com.profile_service.profile.dto.response.ApiResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.service.impl.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@RequestMapping("/info")
public class UserProfileController {
    UserProfileService userProfileService;

    @GetMapping("/getProfile/{id}")
    ApiResponse<UserProfileResponse> getProfileById(@PathVariable String id){

        return ApiResponse.<UserProfileResponse>builder()
                .message("Get profile user")
                .result(userProfileService.getUserProfile(id))
                .build();
    }
}
