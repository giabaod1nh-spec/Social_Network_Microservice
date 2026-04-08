package com.profile_service.profile.service;

import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.ProfileFullInfoResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IUserProfileService {
    UserProfileResponse createProfile (ProfileCreationRequest request);
    UserProfileResponse updateProfile(ProfileUpdateRequest request);
    UserProfileResponse updateAvatar (MultipartFile files);
    ProfileFullInfoResponse getUserProfile (String id);
    UserProfileResponse getUserProfileInternal(String id);

    List<UserProfileResponse> searchUserByUserName (String keyword);
}
