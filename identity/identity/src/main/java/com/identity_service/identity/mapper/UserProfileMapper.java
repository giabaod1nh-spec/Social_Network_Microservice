package com.identity_service.identity.mapper;

import com.identity_service.identity.dto.request.ProfileCreationRequest;
import com.identity_service.identity.dto.request.UserCreationRequest;
import org.springframework.stereotype.Component;

@Component
public class UserProfileMapper {
    public ProfileCreationRequest  convertFromUserCreationRequest(UserCreationRequest request){
        return ProfileCreationRequest.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .gender(request.getGender())
                .dob(request.getDob())
                .address(request.getAddress())
                .phone(request.getPhone())
                .build();
    }
}
