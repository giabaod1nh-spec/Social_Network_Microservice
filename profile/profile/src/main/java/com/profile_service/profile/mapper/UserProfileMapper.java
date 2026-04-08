package com.profile_service.profile.mapper;

import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.entity.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class UserProfileMapper {
    public UserProfile convertUserProfileFromRequest(ProfileCreationRequest request){
        return UserProfile.builder()
                .userId(request.getUserId())
                .userName(request.getUserName())
                .avatar(request.getAvatar())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .gender(request.getGender())
                .dob(request.getDob())
                .address(request.getAddress())
                .phone(request.getPhone())
                .build();
    }

    public UserProfileResponse convertResponseFromUserProfile(UserProfile userProfile) {
        System.out.println("DEBUG: Entity từ DB -> " + userProfile.getUserId() + " | " + userProfile.getFirstName());
        return UserProfileResponse.builder()
                .userId(userProfile.getUserId())
                .userName(userProfile.getUserName())
                .avatar(userProfile.getAvatar())
                .firstName(userProfile.getFirstName())
                .lastName(userProfile.getLastName())
                .gender(userProfile.getGender())
                .dob(userProfile.getDob())
                .address(userProfile.getAddress())
                .phone(userProfile.getPhone())
                .avatar(userProfile.getAvatar())
                .build();
    }
}
