package com.identity_service.identity.service.impl;

import com.identity_service.identity.dto.request.ProfileCreationRequest;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.request.VerifyEmailRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.mapper.UserMapper;
import com.identity_service.identity.mapper.UserProfileMapper;
import com.identity_service.identity.model.entity.EmailVerifyToken;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.repository.httpclient.NotificationClient;
import com.identity_service.identity.repository.httpclient.ProfileClient;
import com.identity_service.identity.service.IUserService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Slf4j(topic = "USER_SERVICE")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class UserService implements IUserService {
    UserMapper userMapper;
    UserRepository userRepository;
    UserProfileMapper profileMapper;
    ProfileClient profileClient;
    NotificationClient notificationClient;
    EmailVerifyTokenRepository emailVerifyTokenRepository;
    @Override
    @Transactional
    public UserResponse createUser(UserCreationRequest request) {

        User user = userMapper.convertUserFromRequest(request);
        if(userRepository.existsByUserName(request.getUserName()) || userRepository.existsByEmail(request.getEmail())){
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        var u = userRepository.save(user);

        //Verify user bang email

        String emailToken = UUID.randomUUID().toString();

        EmailVerifyToken token = EmailVerifyToken.builder()
                .emailVerifyToken(emailToken)
                .users(u)
                .expiredAt(Instant.now().plus(1 , ChronoUnit.HOURS))
                .build();
        //Save verifyToken
        emailVerifyTokenRepository.save(token);

        //Goi den notification service de verify
        VerifyEmailRequest req = VerifyEmailRequest.builder()
                .userEmail(user.getEmail())
                .userName(user.getUserName())
                .token(emailToken)
                .build();

        notificationClient.verifyEmailUser(req);

        //Goi den profile service
        ProfileCreationRequest profileRequest = profileMapper.convertFromUserCreationRequest(request);
        profileRequest.setUserId(u.getUserId());

        profileClient.createProfile(profileRequest);

        return userMapper.convertResponseFromUser(u) ;
    }

    @Override
    public UserResponse getUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        return userMapper.convertResponseFromUser(user);
    }

    @Override
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        user.setUserStatus(UserStatus.DELETE);
    }


}
