package com.profile_service.profile.service.impl;

import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.entity.UserProfile;
import com.profile_service.profile.exception.AppException;
import com.profile_service.profile.exception.ErrorCode;
import com.profile_service.profile.mapper.UserProfileMapper;
import com.profile_service.profile.repository.UserProfileRepository;
import com.profile_service.profile.service.IUserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class UserProfileService  implements IUserProfileService {
    UserProfileMapper userProfileMapper;
    UserProfileRepository userProfileRepository;
    @Override
    public UserProfileResponse createProfile(ProfileCreationRequest request) {

        UserProfile userProfile = userProfileMapper.convertUserProfileFromRequest(request);

        return userProfileMapper.convertResponseFromUserProfile(userProfileRepository.save(userProfile));
    }

    @Override
    public UserProfileResponse getUserProfile(String id) {
        UserProfile userProfile = userProfileRepository.findById(id).orElseThrow(() -> new RuntimeException());
        return  userProfileMapper.convertResponseFromUserProfile(userProfile) ;
    }

    @Override
    public UserProfileResponse getUserProfileInternal(String userId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return userProfileMapper.convertResponseFromUserProfile(userProfile);
    }
}
