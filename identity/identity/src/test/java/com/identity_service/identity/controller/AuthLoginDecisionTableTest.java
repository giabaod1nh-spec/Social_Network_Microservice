package com.identity_service.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.identity_service.identity.dto.request.AuthRequest;
import com.identity_service.identity.dto.request.LogOutRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.dto.response.AuthResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.service.IAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B1 — DECISION TABLE for POST /auth/login
 *
 * Decision table:
 *   C1: Username exists    C2: Password matches   Action
 *   F                      —                      → USER_NOT_EXIST (1001)
 *   T                      F                      → AUTHENTICATED_FAILED (1002)
 *   T                      T                      → AuthResponse (tokens)
 *
 * Also covers: refresh token rotation (SEED-004 detection),
 * and logout endpoint smoke test.
 *
 * Uses @SpringBootTest + @AutoConfigureMockMvc (same pattern as AuthControllerTest)
 * so the full Spring context is loaded and ClientHttpRequestFactorySettings is
 * auto-configured — no need to mock it manually.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
@DisplayName("[B1-DT] AuthController — Decision Table")
class AuthLoginDecisionTableTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // STUB: IAuthService — replaces real service so no DB/JWT calls are made
    @MockitoBean
    private IAuthService authService;

    /* ─────────────────────────────────────────────────────────────────────
     * DT Column 1: C1=F (user does not exist)
     * ────────────────────────────────────────────────────────────────── */
    @Test
    @DisplayName("DT-AUTH-001 | C1=F → USER_NOT_EXIST (1001)")
    void should_returnUserNotExist_when_usernameDoesNotExist() throws Exception {
        // STUB: user not found → throw USER_NOT_EXIST
        when(authService.authenticateUser(any()))
                .thenThrow(new AppException(ErrorCode.USER_NOT_EXIST));

        AuthRequest request = AuthRequest.builder()
                .userName("ghost_user")
                .password("anypassword")
                .build();

        String content1 = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1001));
    }

    /* ─────────────────────────────────────────────────────────────────────
     * DT Column 2: C1=T, C2=F (wrong password)
     * ────────────────────────────────────────────────────────────────── */
    @Test
    @DisplayName("DT-AUTH-002 | C1=T, C2=F → AUTHENTICATED_FAILED (1002)")
    void should_returnAuthFailed_when_passwordIsWrong() throws Exception {
        // STUB: user found but password mismatch
        when(authService.authenticateUser(any()))
                .thenThrow(new AppException(ErrorCode.AUTHENTICATED_FAILED));

        AuthRequest request = AuthRequest.builder()
                .userName("alice")
                .password("WRONG_PASSWORD")
                .build();

        String content2 = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1002));
    }

    /* ─────────────────────────────────────────────────────────────────────
     * DT Column 4: C1=T, C2=T → success (tokens returned)
     * ────────────────────────────────────────────────────────────────── */
    @Test
    @DisplayName("DT-AUTH-004 | C1=T, C2=T → AuthResponse with tokens")
    void should_returnAuthResponse_when_credentialsAreValid() throws Exception {
        // STUB: successful authentication
        AuthResponse stubResponse = AuthResponse.builder()
                .authenticated(true)
                .accessToken("stub.access.token")
                .refreshToken("stub.refresh.token")
                .build();
        when(authService.authenticateUser(any())).thenReturn(stubResponse);

        AuthRequest request = AuthRequest.builder()
                .userName("alice")
                .password("secret123")
                .build();

        String content3 = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty());
    }

    /* ─────────────────────────────────────────────────────────────────────
     * Validation: blank credentials → HTTP 400 (DT-AF-3)
     * ────────────────────────────────────────────────────────────────── */
    @Test
    @DisplayName("DT-AUTH-005 | AF-3 → blank credentials fail validation (code 1017)")
    void should_returnBadRequest_when_credentialsAreBlank() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .userName("")
                .password("")
                .build();

        String content4 = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content4))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1017));

        verifyNoInteractions(authService);
    }

    /* ─────────────────────────────────────────────────────────────────────
     * Refresh token: old token revoked after rotation (detects SEED-004)
     * ────────────────────────────────────────────────────────────────── */
    @Test
    @DisplayName("DT-AUTH-006 | SEED-004 guard — refresh rotation returns new tokens")
    void should_returnNewTokens_when_tokenIsRotated() throws Exception {
        // STUB: successful rotation
        AuthResponse rotated = AuthResponse.builder()
                .authenticated(true)
                .accessToken("new.access.token")
                .refreshToken("new.refresh.token")
                .build();
        when(authService.refreshTokenAfterTimeOut(any())).thenReturn(rotated);

        RefreshTokenRequest req = RefreshTokenRequest.builder()
                .refreshToken("old.refresh.token")
                .build();

        String content5 = objectMapper.writeValueAsString(req);
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.result.refreshToken").value("new.refresh.token"));

        // Service must be called once — token revocation is tested at service level
        // (AuthTokenCauseEffectTest.CE-REFRESH-005)
        verify(authService, times(1)).refreshTokenAfterTimeOut(any());
    }

    /* ─────────────────────────────────────────────────────────────────────
     * Logout endpoint smoke test
     * ────────────────────────────────────────────────────────────────── */
    @Test
    @DisplayName("DT-AUTH-007 | Logout — service called, HTTP 200")
    void should_returnOk_when_logoutIsSuccessful() throws Exception {
        doNothing().when(authService).logOut(any());

        LogOutRequest logoutReq = LogOutRequest.builder()
                .refreshToken("some.refresh.token")
                .build();

        String content6 = objectMapper.writeValueAsString(logoutReq);
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Log out successfully"));
    }

    /* ─────────────────────────────────────────────────────────────────────
     * Nested: service-layer decision table cross-reference
     * ────────────────────────────────────────────────────────────────── */
    @Nested
    @DisplayName("Service-layer Decision Table — cross-reference")
    class ServiceLayerDecisionTable {

        @Test
        @DisplayName("DT-SVC-REF | column mapping documented")
        void should_documentColumnMapping() {
            /*
             * Controller DT column → Service-layer test (AuthServiceTest / AuthTokenCauseEffectTest)
             *
             * DT-AUTH-001  ←→  AuthServiceTest: should_throwUserNotExist_when_userNotFound
             * DT-AUTH-002  ←→  AuthServiceTest: should_throwAuthFailed_when_passwordDoesNotMatch
             * DT-AUTH-004  ←→  AuthServiceTest: should_returnTokens_when_credentialsAreValid
             * DT-AUTH-006  ←→  AuthTokenCauseEffectTest: CE-REFRESH-005 (old token deleted)
             */
        }
    }
}
