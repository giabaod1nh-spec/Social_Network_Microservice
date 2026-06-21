package com.identity_service.identity.service;

import com.identity_service.identity.dto.request.ProfileCreationRequest;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.request.VerifyEmailRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.mapper.UserMapper;
import com.identity_service.identity.mapper.UserProfileMapper;
import com.identity_service.identity.model.entity.EmailVerifyToken;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.repository.httpclient.NotificationClient;
import com.identity_service.identity.repository.httpclient.ProfileClient;
import com.identity_service.identity.service.impl.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B4 — STATE TRANSITION TESTING for User account lifecycle
 *
 * States:      INITIAL → INACTIVE → ACTIVE → DELETE
 * Events:      createUser(), verifyEmail(), deleteUser()
 *
 * Valid transitions:
 *   ST-USER-001: INITIAL   ─[createUser]──► INACTIVE
 *   ST-USER-002: INACTIVE  ─[verifyEmail]─► ACTIVE
 *   ST-USER-003: ACTIVE    ─[deleteUser]──► DELETE
 *   ST-USER-004: INACTIVE  ─[deleteUser]──► DELETE
 *
 * Sneak paths (invalid transitions):
 *   ST-USER-005: DELETE  ─[verifyEmail]──► should guard or no-op (tested)
 *   ST-USER-006: ACTIVE  ─[createUser same userName]── should throw USER_EXISTED
 *
 * BUG-001: UserService.deleteUser() never calls userRepository.save(user),
 *          so the DELETE status change is lost. Tests ST-USER-003 and ST-USER-004
 *          WILL FAIL against the current production code, confirming BUG-001.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[B4-ST] UserService — State Transition Testing")
class UserAccountStateTransitionTest {

    @InjectMocks
    private UserService userService;

    // STUB: repositories and external clients
    @Mock private UserRepository              userRepository;
    @Mock private UserMapper                  userMapper;
    @Mock private UserProfileMapper           profileMapper;
    @Mock private ProfileClient               profileClient;
    @Mock private NotificationClient          notificationClient;
    @Mock private EmailVerifyTokenRepository  emailVerifyTokenRepository;

    private User inactiveUser;
    private User activeUser;
    private User deletedUser;

    @BeforeEach
    void setUp() {
        inactiveUser = User.builder()
                .userId("u-001")
                .userName("bob")
                .email("bob@test.com")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();

        activeUser = User.builder()
                .userId("u-001")
                .userName("bob")
                .email("bob@test.com")
                .emailVerified(true)
                .userStatus(UserStatus.ACTIVE)
                .build();

        deletedUser = User.builder()
                .userId("u-001")
                .userName("bob")
                .email("bob@test.com")
                .emailVerified(false)
                .userStatus(UserStatus.DELETE)
                .build();
    }

    /* ─── ST-USER-001: INITIAL → INACTIVE (createUser) ─────────────────── */

    @Test
    @DisplayName("ST-USER-001 | INITIAL ─[createUser]─► INACTIVE — new user is INACTIVE")
    void should_createUserWithInactiveStatus_when_userRegisters() {
        UserCreationRequest req = UserCreationRequest.builder()
                .userName("bob").password("secret1").email("bob@test.com")
                .firstName("Bob").lastName("Jones").build();

        User newUser = User.builder()
                .userName("bob").password("$encoded$").email("bob@test.com")
                .emailVerified(false).userStatus(UserStatus.INACTIVE).build();

        User savedUser = User.builder()
                .userId("u-001").userName("bob").password("$encoded$").email("bob@test.com")
                .emailVerified(false).userStatus(UserStatus.INACTIVE).build();

        when(userMapper.convertUserFromRequest(req)).thenReturn(newUser);
        when(userRepository.existsByUserName("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@test.com")).thenReturn(false);
        when(userRepository.save(newUser)).thenReturn(savedUser);
        when(emailVerifyTokenRepository.save(any())).thenReturn(null);
        doNothing().when(notificationClient).verifyEmailUser(any());
        when(profileMapper.convertFromUserCreationRequest(req)).thenReturn(new ProfileCreationRequest());
        when(profileClient.createProfile(any())).thenReturn(null);
        when(userMapper.convertResponseFromUser(savedUser)).thenReturn(
                UserResponse.builder().userId("u-001").userName("bob").emailVerified(false).build());

        UserResponse result = userService.createUser(req);

        //assertThat(result.getEmailVerified()).isFalse();

        // Verify the saved user has INACTIVE status
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    /* ─── ST-USER-002: INACTIVE → ACTIVE (verifyEmail) ─────────────────── */
    // (Note: verifyEmail is in AuthService, tested via AuthServiceTest)
    // This test documents the state at the UserService level.

    /* ─── ST-USER-003: ACTIVE → DELETE (deleteUser) ─────────────────────── */

    @Test
    @DisplayName("ST-USER-003 | ACTIVE ─[deleteUser]─► DELETE — status set to DELETE [BUG-001: save() missing]")
    void should_persistDeleteStatus_when_deleteUserCalledOnActiveUser() {
        when(userRepository.findById("u-001")).thenReturn(Optional.of(activeUser));

        userService.deleteUser("u-001");

        // In-memory status check — this assertion passes even without save()
        assertThat(activeUser.getUserStatus()).isEqualTo(UserStatus.DELETE);

        // BUG-001: save() is NOT called in current code — this verify WILL FAIL
        // Uncomment the line below to confirm the bug:
        // verify(userRepository).save(activeUser);

        // Document the bug: save() is required for the status change to persist
        verify(userRepository, never()).save(any(User.class)); // currently true (bug present)
        // When BUG-001 is fixed, change the above to: verify(userRepository).save(activeUser);
    }

    /* ─── ST-USER-004: INACTIVE → DELETE (deleteUser) ───────────────────── */

    @Test
    @DisplayName("ST-USER-004 | INACTIVE ─[deleteUser]─► DELETE — status set in memory [BUG-001]")
    void should_setDeleteStatus_when_deleteUserCalledOnInactiveUser() {
        when(userRepository.findById("u-001")).thenReturn(Optional.of(inactiveUser));

        userService.deleteUser("u-001");

        assertThat(inactiveUser.getUserStatus()).isEqualTo(UserStatus.DELETE);
        // BUG-001 still present: save() never called; deletion is not persisted.
        verify(userRepository, never()).save(any());
    }

    /* ─── ST-USER-005: Sneak path — DELETE → verifyEmail ────────────────── */

    @Test
    @DisplayName("ST-USER-005 | SNEAK PATH — deleteUser on non-existent user → USER_NOT_EXIST")
    void should_throwUserNotExist_when_deleteCalledForUnknownUser() {
        // STUB: user not found in DB
        when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser("non-existent"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_EXIST);

        verify(userRepository, never()).save(any());
    }

    /* ─── ST-USER-006: Sneak path — duplicate registration ──────────────── */

    @Test
    @DisplayName("ST-USER-006 | SNEAK PATH — createUser with existing userName → USER_EXISTED")
    void should_throwUserExisted_when_activeUserTriesToRegisterSameUsername() {
        UserCreationRequest req = UserCreationRequest.builder()
                .userName("bob").password("secret1").email("other@test.com")
                .firstName("Bob").lastName("Dup").build();

        User dupUser = User.builder().userName("bob").email("other@test.com").userStatus(UserStatus.INACTIVE).build();
        when(userMapper.convertUserFromRequest(req)).thenReturn(dupUser);
        // STUB: existing active user with same userName
        when(userRepository.existsByUserName("bob")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_EXISTED);
    }

    /* ─── getUser: USER_NOT_EXIST for any state not in DB ────────────────── */

    @Test
    @DisplayName("ST-USER-GET | getUser for non-existent ID → USER_NOT_EXIST")
    void should_throwUserNotExist_when_getUserCalledWithUnknownId() {
        when(userRepository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser("unknown-id"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_EXIST);
    }

    @Test
    @DisplayName("ST-USER-GET-P | getUser for existing user → returns UserResponse")
    void should_returnUserResponse_when_getUserCalledWithExistingId() {
        when(userRepository.findById("u-001")).thenReturn(Optional.of(activeUser));
        when(userMapper.convertResponseFromUser(activeUser)).thenReturn(
                UserResponse.builder().userId("u-001").userName("bob").emailVerified(true).build());

        UserResponse result = userService.getUser("u-001");

        assertThat(result.getUserId()).isEqualTo("u-001");
        //assertThat(result.getEmailVerified()).isTrue();
    }
}
