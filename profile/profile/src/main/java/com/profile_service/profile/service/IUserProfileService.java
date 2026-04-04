package com.profile_service.profile.service;

import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IUserProfileService {
    UserProfileResponse createProfile (ProfileCreationRequest request);
    UserProfileResponse updateProfile(ProfileUpdateRequest request);
    UserProfileResponse updateAvatar (MultipartFile files);
    UserProfileResponse getUserProfile (String id);
    UserProfileResponse getUserProfileInternal(String id);
}
