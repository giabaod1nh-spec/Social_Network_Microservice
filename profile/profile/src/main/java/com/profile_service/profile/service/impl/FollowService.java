package com.profile_service.profile.service.impl;

import com.profile_service.profile.dto.response.UserProfileResponse;
import com.profile_service.profile.entity.UserProfile;
import com.profile_service.profile.exception.AppException;
import com.profile_service.profile.exception.ErrorCode;
import com.profile_service.profile.mapper.UserProfileMapper;
import com.profile_service.profile.repository.FollowRepository;
import com.profile_service.profile.repository.UserProfileRepository;
import com.profile_service.profile.service.IFollowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@RequiredArgsConstructor
@Slf4j(topic = "FOLLOW_SERVICE")
public class FollowService implements IFollowService {
    FollowRepository followRepository;
    UserProfileMapper userProfileMapper;
    UserProfileRepository userProfileRepository;

    @Override
    public void follow(String currentUserId, String targetUserId) {

        if(currentUserId.equals(targetUserId)){
            throw new AppException(ErrorCode.CANT_FOLLOW_YOURSELF);
        }

       if(followRepository.isFollowing(currentUserId , targetUserId)){
           throw new AppException(ErrorCode.ALREADY_FOLLOWED);
       }

       boolean result = followRepository.createFollow(currentUserId , targetUserId);
       if(!result){
           //throw new AppException(ErrorCode.ERROR_WHEN_FOLLOW);
       }
    }

    @Override
    public void unfollow(String currentUserId, String targetUserId) {
        if(!followRepository.isFollowing(currentUserId , targetUserId)){
            throw new AppException(ErrorCode.ALREADY_FOLLOWED);
        }
        long result = followRepository.unfollow(currentUserId, targetUserId);
        log.info("Result after unfollow:{} " , result);
    }

    @Override
    public List<UserProfileResponse> userThatFollowedYou(String userId) {
        List<UserProfile> userProfiles = userProfileRepository.findFollowersByUserId(userId);

        return userProfiles.stream().map(userProfileMapper::convertResponseFromUserProfile).toList();
    }

    @Override
    public List<UserProfileResponse> userThatYouFollowing(String userId) {
        List<UserProfile> userProfiles = userProfileRepository.findFollowingByUserId(userId);

        return userProfiles.stream().map(userProfileMapper::convertResponseFromUserProfile).toList();
    }
}
