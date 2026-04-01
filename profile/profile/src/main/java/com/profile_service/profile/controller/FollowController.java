package com.profile_service.profile.controller;

import com.profile_service.profile.dto.response.ApiResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.service.impl.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FollowController {
    private final FollowService followService;


    @PostMapping("/follow/{targetUserId}")
    ApiResponse<Void> followSomeNigga(@PathVariable String targetUserId , JwtAuthenticationToken auth){
        String userId = auth.getToken().getSubject();
             followService.follow(userId , targetUserId);
            return ApiResponse.<Void>
                    builder()
                    .message("Followed success")
                    .build();
    }

    @PostMapping("/unfollow/{targetUserId}")
    ApiResponse<Void> unfollowSomeNigga(@PathVariable String targetUserId , JwtAuthenticationToken auth){
        String userId = auth.getToken().getSubject();

        followService.unfollow(userId , targetUserId);
        return ApiResponse.<Void>
                        builder()
                .message("Unfollowed success")
                .build();
    }


    @GetMapping("/getFollower")
    ApiResponse<List<UserProfileResponse>> whoFollowThisNigga(JwtAuthenticationToken auth){
        String userId = auth.getToken().getSubject();
        return ApiResponse.<List<UserProfileResponse>>
                        builder()
                .message("User follow you list")
                .result(followService.userThatFollowedYou(userId))
                .build();
    }

    @GetMapping("/getFollowed")
    ApiResponse<List<UserProfileResponse>> whoThisNiggaFollowed(JwtAuthenticationToken auth){
        String userId = auth.getToken().getSubject();
        return ApiResponse.<List<UserProfileResponse>>
                        builder()
                .message("User that you followed")
                .result(followService.userThatYouFollowing(userId))
                .build();
    }

}
