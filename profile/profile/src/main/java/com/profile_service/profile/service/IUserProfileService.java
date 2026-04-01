package com.profile_service.profile.service;

import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.response.UserProfileResponse;

public interface IUserProfileService {
    UserProfileResponse createProfile (ProfileCreationRequest request);
    UserProfileResponse getUserProfile (String id);
    UserProfileResponse getUserProfileInternal(String id);
}
