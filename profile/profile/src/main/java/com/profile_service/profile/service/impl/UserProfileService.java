package com.profile_service.profile.service.impl;

import com.profile_service.profile.configuration.R2Config;
import com.profile_service.profile.dto.request.ProfileCreationRequest;
import com.profile_service.profile.dto.request.ProfileUpdateRequest;
import com.profile_service.profile.dto.response.ProfileFullInfoResponse;
import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.entity.UserProfile;
import com.profile_service.profile.exception.AppException;
import com.profile_service.profile.exception.ErrorCode;
import com.profile_service.profile.mapper.UserProfileMapper;
import com.profile_service.profile.repository.FollowRepository;
import com.profile_service.profile.repository.UserProfileRepository;
import com.profile_service.profile.service.IUserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Slf4j(topic = "PROFILE_SERVICE")
public class UserProfileService  implements IUserProfileService {
    S3Client r2Client;
    UserProfileMapper userProfileMapper;
    UserProfileRepository userProfileRepository;
    R2Config r2Config;
    FollowRepository followRepository;
    @Override
    public UserProfileResponse createProfile(ProfileCreationRequest request) {

        UserProfile userProfile = userProfileMapper.convertUserProfileFromRequest(request);

        return userProfileMapper.convertResponseFromUserProfile(userProfileRepository.save(userProfile));
    }

    @Override
    public UserProfileResponse updateProfile(ProfileUpdateRequest request) {

        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("UserId:" + userId );
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        userProfile.setFirstName(request.getFirstName());
        userProfile.setLastName(request.getLastName());
        userProfile.setDob(request.getDob());
        userProfile.setAddress(request.getAddress());

        return  userProfileMapper.convertResponseFromUserProfile(userProfileRepository.save(userProfile));
    }

    @Override
    public UserProfileResponse updateAvatar(MultipartFile files) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("UserId cua user khi update avatar:" + userId );
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        userProfile.setAvatar(uploadMediaToS3(userId , files));
        return userProfileMapper.convertResponseFromUserProfile(userProfileRepository.save(userProfile));
    }

    @Override
    public ProfileFullInfoResponse getUserProfile(String id) {

        UserProfile userProfile = userProfileRepository.findByUserId(id).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
         return  ProfileFullInfoResponse.builder()
                .userId(userProfile.getUserId())
                .userName(userProfile.getUserName())
                .avatar(userProfile.getAvatar())
                .firstName(userProfile.getFirstName())
                .lastName(userProfile.getLastName())
                .gender(userProfile.getGender())
                .dob(userProfile.getDob())
                .address(userProfile.getAddress())
                .phone(userProfile.getPhone())
                .follower(followRepository.countFollowers(id))
                .followed(followRepository.countFollowing(id))
                .build();
    }

    @Override
    public UserProfileResponse getUserProfileInternal(String userId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return userProfileMapper.convertResponseFromUserProfile(userProfile);
    }

    @Override
    public List<UserProfileResponse> searchUserByUserName(String keyword) {
        List<UserProfile> lists = userProfileRepository.findUserByUserName(keyword);

        return lists.stream().map(userProfileMapper::convertResponseFromUserProfile).toList();
    }


    private String uploadMediaToS3(String userId, MultipartFile media)  {
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString() + media.getName();
        String dirName = String.format("post/%s/%s", userId, fileName);

        PutObjectRequest request = PutObjectRequest.builder().bucket(r2Config.getBucketName())
                .key(dirName)
                .contentType(media.getContentType())
                .contentLength(media.getSize())
                .build();
        try {
            r2Client.putObject(request, RequestBody.fromInputStream(media.getInputStream(), media.getSize()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return r2Config.getPublicUrl() + "/" + dirName;
    }

}
