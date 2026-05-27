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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService unit tests: decision table for duplicate checks, ECP/BVA on userId.
 */
@TestPropertySource("/test.properties")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String USER_ID = "user-uuid-1";
    private static final String USERNAME = "kingmoans";
    private static final String EMAIL = "sigmafreaky@gmail.com";

    @InjectMocks
    private UserService userService;

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private ProfileClient profileClient;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private EmailVerifyTokenRepository emailVerifyTokenRepository;

    private UserCreationRequest creationRequest;
    private User mappedUser;
    private User savedUser;
    private UserResponse mappedResponse;
    private ProfileCreationRequest profileRequest;

    @BeforeEach
    void setUp() {
        creationRequest = UserCreationRequest.builder()
                .userName(USERNAME)
                .avatar("")
                .password("12345678")
                .email(EMAIL)
                .firstName("John")
                .lastName("Doe")
                .gender("Male")
                .dob(LocalDate.of(1990, 1, 1))
                .address("135 Nguyen Van Thinh")
                .phone("0982345654")
                .build();

        mappedUser = User.builder()
                .userName(USERNAME)
                .email(EMAIL)
                .password("encoded")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();

        savedUser = User.builder()
                .userId(USER_ID)
                .userName(USERNAME)
                .email(EMAIL)
                .password("encoded")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();

        mappedResponse = UserResponse.builder()
                .userId(USER_ID)
                .userName(USERNAME)
                .email(EMAIL)
                .emailVerified(false)
                .build();

        profileRequest = ProfileCreationRequest.builder()
                .userName(USERNAME)
                .firstName("John")
                .lastName("Doe")
                .build();
    }

    /**
     * Decision table: existsByUserName | existsByEmail -> outcome.
     */
    @ParameterizedTest(name = "createUser_{3}")
    @CsvSource({
            "true, true, USER_EXISTED, bothExist_throwsUserExisted",
            "true, false, USER_EXISTED, usernameExists_throwsUserExisted",
            "false, true, USER_EXISTED, emailExists_throwsUserExisted",
            "false, false, SUCCESS, bothFree_persistsUser",
    })
    void createUser_duplicateDecisionTable(
            boolean usernameTaken,
            boolean emailTaken,
            String expectedOutcome,
            String scenario) {
        when(userMapper.convertUserFromRequest(creationRequest)).thenReturn(mappedUser);
        when(userRepository.existsByUserName(USERNAME)).thenReturn(usernameTaken);
        if (!usernameTaken) {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(emailTaken);
        }

        if ("SUCCESS".equals(expectedOutcome)) {
            when(userRepository.save(mappedUser)).thenReturn(savedUser);
            when(profileMapper.convertFromUserCreationRequest(creationRequest)).thenReturn(profileRequest);
            when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

            UserResponse result = userService.createUser(creationRequest);

            assertThat(result).isEqualTo(mappedResponse);
            verify(userRepository).save(mappedUser);
            verify(emailVerifyTokenRepository).save(any(EmailVerifyToken.class));
            verify(notificationClient).verifyEmailUser(any(VerifyEmailRequest.class));
            verify(profileClient).createProfile(any(ProfileCreationRequest.class));
        } else {
            AppException ex = assertThrows(AppException.class, () -> userService.createUser(creationRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_EXISTED);
            verify(userRepository, never()).save(any());
            verify(emailVerifyTokenRepository, never()).save(any());
            verify(notificationClient, never()).verifyEmailUser(any());
            verify(profileClient, never()).createProfile(any());
        }
    }

    @Test
    void createUser_bothFree_wiresVerifyEmailAndProfile() {
        when(userMapper.convertUserFromRequest(creationRequest)).thenReturn(mappedUser);
        when(userRepository.existsByUserName(USERNAME)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(mappedUser)).thenReturn(savedUser);
        when(profileMapper.convertFromUserCreationRequest(creationRequest)).thenReturn(profileRequest);
        when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

        userService.createUser(creationRequest);

        ArgumentCaptor<EmailVerifyToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerifyToken.class);
        verify(emailVerifyTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUsers()).isEqualTo(savedUser);

        ArgumentCaptor<VerifyEmailRequest> notifyCaptor = ArgumentCaptor.forClass(VerifyEmailRequest.class);
        verify(notificationClient).verifyEmailUser(notifyCaptor.capture());
        assertThat(notifyCaptor.getValue().getToken()).isEqualTo(tokenCaptor.getValue().getEmailVerifyToken());

        ArgumentCaptor<ProfileCreationRequest> profileCaptor = ArgumentCaptor.forClass(ProfileCreationRequest.class);
        verify(profileClient).createProfile(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @ParameterizedTest(name = "getUser_{1}")
    @CsvSource({
            "user-uuid-1, FOUND, validId_returnsMappedResponse",
            "missing-id, NOT_FOUND, unknownId_throwsUserNotExist",
    })
    void getUser_userIdEquivalencePartition(String userId, String partition, String scenario) {
        if ("FOUND".equals(partition)) {
            when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
            when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

            UserResponse result = userService.getUser(userId);

            assertThat(result).isEqualTo(mappedResponse);
            verify(userMapper).convertResponseFromUser(savedUser);
        } else {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> userService.getUser(userId));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            verify(userMapper, never()).convertResponseFromUser(any());
        }
    }

    @ParameterizedTest(name = "getUser_{0}_throwsUserNotExist")
    @NullAndEmptySource
    void getUser_boundaryEmptyId_throwsUserNotExist(String userId) {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> userService.getUser(userId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
    }

    @ParameterizedTest(name = "deleteUser_{1}")
    @CsvSource({
            "user-uuid-1, FOUND, validId_setsStatusDelete",
            "ghost-id, NOT_FOUND, unknownId_throwsUserNotExist",
    })
    void deleteUser_userIdPartition(String userId, String partition, String scenario) {
        if ("FOUND".equals(partition)) {
            User user = new User();
            user.setUserId(userId);
            user.setUserStatus(UserStatus.ACTIVE);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            userService.deleteUser(userId);

            assertThat(user.getUserStatus()).isEqualTo(UserStatus.DELETE);
        } else {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            AppException ex = assertThrows(AppException.class, () -> userService.deleteUser(userId));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
        }
    }
}
