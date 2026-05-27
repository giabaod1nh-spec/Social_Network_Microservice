package com.identity_service.identity.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.identity_service.identity.dto.request.AuthRequest;
import com.identity_service.identity.dto.request.IntroSpectRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.dto.response.AuthResponse;
import com.identity_service.identity.dto.response.IntroSpectResponse;
import com.identity_service.identity.service.IAuthService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
@Slf4j
public class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthService authService;
    private AuthRequest authRequest;
    private IntroSpectRequest introSpectRequest;
    private RefreshTokenRequest refreshTokenRequest;
    private AuthResponse authResponse;
    private IntroSpectResponse introspectResponse;

    @BeforeEach
    void initData(){

        authRequest = AuthRequest.builder()
                .userName("anbatokum")
                .password("123456")
                .build();

        introSpectRequest = IntroSpectRequest.builder()
                .token("token_uuid")
                .build();

        refreshTokenRequest = RefreshTokenRequest.builder()
                .refreshToken("refresh_token_uuid")
                .build();

        authResponse = AuthResponse.builder()
                .authenticated(true)
                .accessToken("access_token_uuid")
                .refreshToken("refresh_token_uuid")
                .build();

        introspectResponse = IntroSpectResponse.builder()
                .isValid(true)
                .build();
    }

    @Test
    void login_success() throws Exception {
        //GIVEN
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(authRequest);

        //Khi request den AuthController , thay vi goi den Service
        Mockito.when(authService.authenticateUser(ArgumentMatchers.any()))
                .thenReturn(authResponse);
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                .post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000));
    }

    @Test
    void login_userNameInvalid() throws Exception {
        //GIVEN
        authRequest.setUserName("");
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(authRequest);

        //Khi request den AuthController , thay vi goi den Service
        Mockito.when(authService.authenticateUser(ArgumentMatchers.any()))
                .thenReturn(authResponse);
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1017));
    }

    @Test
    void login_passwordInvalid() throws Exception {
        //GIVEN
        authRequest.setPassword("");
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(authRequest);

        //Khi request den AuthController , thay vi goi den Service
        Mockito.when(authService.authenticateUser(ArgumentMatchers.any()))
                .thenReturn(authResponse);
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1017));
    }

    @Test
    void introspectToken_success() throws Exception {
        //GIVEN
        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(introSpectRequest);

        //Khi request den AuthController , thay vi goi den Service
        Mockito.when(authService.introspectToken(ArgumentMatchers.any()))
                .thenReturn(introspectResponse);
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code").value(1000));
    }

}
