package com.identity_service.identity.mapper;

import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final PasswordEncoder passwordEncoder;
    public User convertUserFromRequest (UserCreationRequest request){
        return User.builder()
                .userName(request.getUserName())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();
    }

    public UserResponse convertResponseFromUser(User user){
        return  UserResponse.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .emailVerified(user.getEmailVerified())
                .build();
    }
}
