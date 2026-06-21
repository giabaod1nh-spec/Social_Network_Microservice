package com.identity_service.identity.service;

import com.identity_service.identity.dto.request.ProfileCreationRequest;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.request.VerifyEmailRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.mapper.UserMapper;
import com.identity_service.identity.mapper.UserProfileMapper;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.repository.httpclient.NotificationClient;
import com.identity_service.identity.repository.httpclient.ProfileClient;
import com.identity_service.identity.service.impl.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B2 — EQUIVALENCE CLASS PARTITIONING for UserService.createUser
 *
 * ECP classes tested:
 *   userName:  VEC-UN-1 (≥4 chars), IEC-UN-1 (<4 chars — handled by controller validation)
 *   email:     VEC-EM-1 (unique email), IEC-EM-1 (duplicate email → USER_EXISTED)
 *   userName:  IEC-UN-DUP (duplicate userName → USER_EXISTED)
 *
 * SEED-002 detection: removing existsByEmail check would break test ECP-CREATE-N2.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[B2-ECP] UserService — Equivalence Class Partitioning")
class UserCreationECPTest {

    @InjectMocks
    private UserService userService;

    // STUB: UserRepository — simulates persistence layer without DB
    @Mock private UserRepository userRepository;

    // STUB: UserMapper — converts request ↔ entity without real BCrypt
    @Mock private UserMapper userMapper;

    // STUB: UserProfileMapper — converts request to profile DTO
    @Mock private UserProfileMapper profileMapper;

    // STUB: ProfileClient — simulates Feign call to profile-service
    @Mock private ProfileClient profileClient;

    // STUB: NotificationClient — simulates Feign call to notification-service
    @Mock private NotificationClient notificationClient;

    // STUB: EmailVerifyTokenRepository — simulates email token persistence
    @Mock private EmailVerifyTokenRepository emailVerifyTokenRepository;

    private UserCreationRequest validRequest;
    private User mappedUser;
    private User savedUser;
    private UserResponse expectedResponse;

    @BeforeEach
    void setUp() {
        // VEC-UN-1: userName with 8 chars (well within valid class)
        validRequest = UserCreationRequest.builder()
                .userName("alice123")
                .password("secure99")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .build();

        mappedUser = User.builder()
                .userName("alice123")
                .password("$encoded$")
                .email("alice@example.com")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();

        savedUser = User.builder()
                .userId("uuid-alice-001")
                .userName("alice123")
                .password("$encoded$")
                .email("alice@example.com")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();

        expectedResponse = UserResponse.builder()
                .userId("uuid-alice-001")
                .userName("alice123")
                .email("alice@example.com")
                .emailVerified(false)
                .build();
    }

    /* ─────────────────────────────────────────────────────────────────────
     * VEC: Valid equivalence class — both userName and email are unique
     * ────────────────────────────────────────────────────────────────── */
    @Nested
    @DisplayName("Valid Equivalence Classes")
    class ValidEquivalenceClasses {

        @Test
        @DisplayName("ECP-CREATE-P1 | VEC-UN-1 + VEC-EM-1 → user created successfully")
        void should_createUser_when_usernameAndEmailAreUnique() {
            // STUB: no duplicates exist
            when(userRepository.existsByUserName("alice123")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            // STUB: mapper converts request to entity
            when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
            // STUB: repository save returns saved entity with generated ID
            when(userRepository.save(mappedUser)).thenReturn(savedUser);
            // STUB: email token repository persists token
            when(emailVerifyTokenRepository.save(any())).thenReturn(null);
            // STUB: notification client — no-op
            doNothing().when(notificationClient).verifyEmailUser(any(VerifyEmailRequest.class));
            // STUB: profile mapper converts to profile request
            when(profileMapper.convertFromUserCreationRequest(validRequest))
                    .thenReturn(new ProfileCreationRequest());
            // STUB: profile client — no-op
            when(profileClient.createProfile(any())).thenReturn(null);
            // STUB: userMapper converts saved entity to response
            when(userMapper.convertResponseFromUser(savedUser)).thenReturn(expectedResponse);

            UserResponse result = userService.createUser(validRequest);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("uuid-alice-001");
            assertThat(result.getUserName()).isEqualTo("alice123");
            //assertThat(result.getEmailVerified()).isFalse();

            verify(userRepository).save(mappedUser);
            verify(emailVerifyTokenRepository).save(any());
            verify(notificationClient).verifyEmailUser(any());
            verify(profileClient).createProfile(any());
        }
    }

    /* ─────────────────────────────────────────────────────────────────────
     * IEC: Invalid equivalence classes — duplicate username or email
     * ────────────────────────────────────────────────────────────────── */
    @Nested
    @DisplayName("Invalid Equivalence Classes")
    class InvalidEquivalenceClasses {

        @Test
        @DisplayName("ECP-CREATE-N1 | IEC-UN-DUP → USER_EXISTED when userName is duplicate")
        void should_throwUserExisted_when_usernameAlreadyTaken() {
            when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
            // STUB: username already exists in DB
            when(userRepository.existsByUserName("alice123")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(validRequest))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_EXISTED);

            verify(userRepository, never()).save(any());
            verify(notificationClient, never()).verifyEmailUser(any());
        }

        @Test
        @DisplayName("ECP-CREATE-N2 | IEC-EM-1 → USER_EXISTED when email is duplicate (SEED-002 guard)")
        void should_throwUserExisted_when_emailAlreadyTaken() {
            when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
            // STUB: username is unique, but email is taken
            when(userRepository.existsByUserName("alice123")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(validRequest))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_EXISTED);

            // If SEED-002 (missing existsByEmail check) is active, this assertion fails
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("ECP-CREATE-N3 | IEC: notification failure → exception propagates")
        void should_propagateException_when_notificationServiceFails() {
            when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
            when(userRepository.existsByUserName("alice123")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            when(userRepository.save(mappedUser)).thenReturn(savedUser);
            when(emailVerifyTokenRepository.save(any())).thenReturn(null);
            // STUB: notification service is down
            doThrow(new RuntimeException("Notification service unavailable"))
                    .when(notificationClient).verifyEmailUser(any());

            assertThatThrownBy(() -> userService.createUser(validRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Notification service unavailable");
        }

        @Test
        @DisplayName("ECP-CREATE-N4 | IEC: profile service failure → exception propagates")
        void should_propagateException_when_profileServiceFails() {
            when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
            when(userRepository.existsByUserName("alice123")).thenReturn(false);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            when(userRepository.save(mappedUser)).thenReturn(savedUser);
            when(emailVerifyTokenRepository.save(any())).thenReturn(null);
            doNothing().when(notificationClient).verifyEmailUser(any());
            when(profileMapper.convertFromUserCreationRequest(any())).thenReturn(new ProfileCreationRequest());
            // STUB: profile service is down
            doThrow(new RuntimeException("Profile service unavailable"))
                    .when(profileClient).createProfile(any());

            assertThatThrownBy(() -> userService.createUser(validRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Profile service unavailable");
        }
    }

    /* ─────────────────────────────────────────────────────────────────────
     * ECP Summary note (comment — see TEST_CASES.md for full table)
     *
     * Exhaustive test count without ECP: 4 (userName) × 3 (password) × 3 (email) = 36
     * With ECP: 4 representative tests cover all equivalence boundaries.
     * ────────────────────────────────────────────────────────────────── */
}
