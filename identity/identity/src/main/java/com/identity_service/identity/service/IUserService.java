package com.identity_service.identity.service;

import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.response.UserResponse;

public interface IUserService {
    UserResponse createUser(UserCreationRequest request);
    UserResponse getUser (String userId);
    void deleteUser (String userId);
}
