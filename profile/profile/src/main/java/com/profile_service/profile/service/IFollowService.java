package com.profile_service.profile.service;

import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.entity.UserProfile;

import java.util.List;

public interface IFollowService {
    void follow(String currentUserId , String targetUserId);
    void unfollow(String currentUserId , String targetUserId);
    List<UserProfileResponse>  userThatFollowedYou(String userId);
    List<UserProfileResponse> userThatYouFollowing(String userId);
}
