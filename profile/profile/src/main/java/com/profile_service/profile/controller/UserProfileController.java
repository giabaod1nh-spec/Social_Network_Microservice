package com.profile_service.profile.controller;

import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.ApiResponse;
import com.profile_service.profile.dto.response.ProfileFullInfoResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.service.impl.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.neo4j.cypherdsl.core.Use;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@RequestMapping("/info")
public class UserProfileController {
    UserProfileService userProfileService;

    @GetMapping("/getProfile")
    ApiResponse<ProfileFullInfoResponse> getProfileById(){
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        return ApiResponse.<ProfileFullInfoResponse>builder()
                .message("Get profile user")
                .result(userProfileService.getUserProfile(userId))
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

    @GetMapping("/search/{userName}")
    ApiResponse<List<UserProfileResponse>> getUserSearchByUserName(@PathVariable String userName){
        return  ApiResponse.<List<UserProfileResponse>>
                builder()
                .message("User searched")
                .result(userProfileService.searchUserByUserName(userName))
                .build();
    }

}
