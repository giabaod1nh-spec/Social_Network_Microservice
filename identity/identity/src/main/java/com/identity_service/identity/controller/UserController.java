package com.identity_service.identity.controller;

import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.response.ApiResponse;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.service.IUserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE ,  makeFinal = true)
public class UserController {
    IUserService userService;

    @PostMapping("/create")
    public ApiResponse<UserResponse> createUser(@RequestBody UserCreationRequest request){
        return ApiResponse.<UserResponse>builder()
                .message("Create user successful")
                .result(userService.createUser(request))
                .build();
    }

    @GetMapping("/getById")
    public ApiResponse<UserResponse> getUserById(@RequestParam String userId){
        return ApiResponse.<UserResponse>builder()
                .message("Get user by id")
                .result(userService.getUser(userId))
                .build();
    }

    @DeleteMapping("/delete")
    public ApiResponse<Void> deleteUserById(@RequestParam String userId){
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder()
                .message("Delete user success")
                .build();
    }
}
