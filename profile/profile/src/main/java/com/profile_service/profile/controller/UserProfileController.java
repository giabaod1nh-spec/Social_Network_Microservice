package com.profile_service.profile.controller;

import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.ApiResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.service.impl.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.neo4j.cypherdsl.core.Use;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping("/updateInfo")
    ApiResponse<UserProfileResponse> updateInfoUser(@RequestBody ProfileUpdateRequest request) {

        return ApiResponse.<UserProfileResponse>builder()
                .message("Update info user")
                .result(userProfileService.updateProfile(request))
                .build();
    }

    @PostMapping(value = "/updateAvatar" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<UserProfileResponse> updateAvatarUser(@RequestPart("file") MultipartFile file){

        return  ApiResponse.<UserProfileResponse>
                builder()
                .message("Update avatar success")
                .result(userProfileService.updateAvatar(file))
                .build();
    }


}
