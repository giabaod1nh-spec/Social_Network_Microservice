package com.identity_service.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.service.IUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B3 — BOUNDARY VALUE ANALYSIS for UserController POST /user/create
 *
 * BVA targets:
 *   userName — @Size(min=4): boundary points min-1=3, min=4, min+1=5
 *   password — @Size(min=6): boundary points min-1=5, min=6, min+1=7
 *
 * Note: max boundary (255) is tested for DB truncation awareness but
 * is not enforced by the current @Size annotation.
 */
@WebMvcTest(UserController.class)
@DisplayName("[B3-BVA] UserController — Boundary Value Analysis")
class UserCreationBVATest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // STUB: IUserService — simulates service without DB
    @MockBean
    private IUserService userService;

    private static final String VALID_EMAIL    = "test@example.com";
    private static final String VALID_PASSWORD = "secure99";
    private static final String VALID_USERNAME = "alice123";

    private String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    private UserCreationRequest buildRequest(String userName, String password) {
        return UserCreationRequest.builder()
                .userName(userName)
                .password(password)
                .email(VALID_EMAIL)
                .firstName("Test")
                .lastName("User")
                .build();
    }

    /* ═══════════════════════════════════════════════════════════════════
     * userName BVA  (min = 4)
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("userName BVA — @Size(min=4)")
    class UserNameBoundary {

        @Test
        @DisplayName("BVA-UN-001 | min-1 = 3 chars → 400 INVALID (below minimum)")
        void should_rejectRequest_when_usernameLengthIsThree() throws Exception {
            UserCreationRequest req = buildRequest("abc", VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("BVA-UN-002 | min = 4 chars → 200 OK (exact minimum — valid)")
        void should_acceptRequest_when_usernameLengthIsFour() throws Exception {
            // STUB: service accepts request at exact boundary
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-001").userName("abcd").email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest("abcd", VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.userName").value("abcd"));
        }

        @Test
        @DisplayName("BVA-UN-003 | min+1 = 5 chars → 200 OK (just above minimum)")
        void should_acceptRequest_when_usernameLengthIsFive() throws Exception {
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-002").userName("abcde").email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest("abcde", VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("BVA-UN-004 | nominal = 8 chars → 200 OK")
        void should_acceptRequest_when_usernameLengthIsNominal() throws Exception {
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-003").userName("alice123").email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest("alice123", VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("BVA-UN-005 | max-1 = 254 chars → 200 OK")
        void should_acceptRequest_when_usernameLengthIs254() throws Exception {
            String longName = repeat('a', 254);
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-004").userName(longName).email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest(longName, VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("BVA-UN-006 | max = 255 chars → 200 OK (practical DB limit)")
        void should_acceptRequest_when_usernameLengthIs255() throws Exception {
            String maxName = repeat('a', 255);
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-005").userName(maxName).email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest(maxName, VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("BVA-UN-007 | max+1 = 256 chars → service receives oversized input (DB truncation risk)")
        void should_documentRisk_when_usernameLengthIs256() throws Exception {
            // No @Size(max) declared — request passes validation, but DB varchar(255) may reject it.
            // This test documents the boundary gap: a @Size(max=255) constraint should be added.
            String overMax = repeat('a', 256);
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-006").userName(overMax).email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest(overMax, VALID_PASSWORD);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    // Currently passes through to service (no max constraint) — documents a gap
                    .andExpect(status().isOk());
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * password BVA  (min = 6)
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("password BVA — @Size(min=6)")
    class PasswordBoundary {

        @Test
        @DisplayName("BVA-PW-001 | min-1 = 5 chars → 400 INVALID (below minimum)")
        void should_rejectRequest_when_passwordLengthIsFive() throws Exception {
            UserCreationRequest req = buildRequest(VALID_USERNAME, "12345");

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("BVA-PW-002 | min = 6 chars → 200 OK (exact minimum — valid)")
        void should_acceptRequest_when_passwordLengthIsSix() throws Exception {
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-007").userName(VALID_USERNAME).email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest(VALID_USERNAME, "123456");

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("BVA-PW-003 | min+1 = 7 chars → 200 OK (just above minimum)")
        void should_acceptRequest_when_passwordLengthIsSeven() throws Exception {
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-008").userName(VALID_USERNAME).email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest(VALID_USERNAME, "1234567");

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("BVA-PW-004 | nominal = 12 chars → 200 OK")
        void should_acceptRequest_when_passwordLengthIsNominal() throws Exception {
            UserResponse stubResp = UserResponse.builder()
                    .userId("u-009").userName(VALID_USERNAME).email(VALID_EMAIL).emailVerified(false).build();
            when(userService.createUser(any())).thenReturn(stubResp);

            UserCreationRequest req = buildRequest(VALID_USERNAME, "myStrongPass");

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest(name = "BVA-PW-NEG | length={0} → 400")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5})
        @DisplayName("BVA-PW-NEG | all lengths below min=6 → 400")
        void should_rejectRequest_when_passwordIsBelowMinimum(int length) throws Exception {
            String shortPwd = length == 0 ? "" : repeat('x', length);
            UserCreationRequest req = buildRequest(VALID_USERNAME, shortPwd);

            mockMvc.perform(post("/user/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }
}
