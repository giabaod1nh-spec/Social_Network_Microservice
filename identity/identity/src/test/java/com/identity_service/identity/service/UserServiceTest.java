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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource("/test.properties")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_USER_ID = "user-uuid-1";
    private static final String USERNAME      = "testuser";
    private static final String EMAIL         = "test@example.com";

    @InjectMocks private UserService userService;

    @Mock private UserMapper                  userMapper;
    @Mock private UserRepository              userRepository;
    @Mock private UserProfileMapper           profileMapper;
    @Mock private ProfileClient               profileClient;
    @Mock private NotificationClient          notificationClient;
    @Mock private EmailVerifyTokenRepository  emailVerifyTokenRepository;

    private UserCreationRequest   validRequest;
    private User                  mappedUser;
    private User                  savedUser;
    private UserResponse          mappedResponse;
    private ProfileCreationRequest profileRequest;

    @BeforeEach
    void setUp() {
        validRequest = UserCreationRequest.builder()
                .userName(USERNAME).email(EMAIL).password("password123")
                .firstName("Test").lastName("User").gender("Male")
                .dob(LocalDate.of(1990, 1, 1))
                .address("123 Test St").phone("0912345678").avatar("")
                .build();

        mappedUser = User.builder()
                .userName(USERNAME).email(EMAIL).password("encoded")
                .emailVerified(false).userStatus(UserStatus.INACTIVE).build();

        savedUser = User.builder()
                .userId(VALID_USER_ID).userName(USERNAME).email(EMAIL)
                .password("encoded").emailVerified(false)
                .userStatus(UserStatus.INACTIVE).build();

        mappedResponse = UserResponse.builder()
                .userId(VALID_USER_ID).userName(USERNAME)
                .email(EMAIL).emailVerified(false).build();

        profileRequest = ProfileCreationRequest.builder()
                .userName(USERNAME).firstName("Test").lastName("User").build();
    }

    // ══════════════════════════════════════════════════════════════════
    //  createUser(UserCreationRequest)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {

        // ── Decision Table ──────────────────────────────────────────
        @Nested
        @DisplayName("Decision Table — C1:usernameExists × C2:emailExists")
        class DecisionTableTests {

            // Technique : Decision Table
            // TC ID     : TC-createUser-DT-01
            // Covers    : R1 — C1=T, C2=T (short-circuit: C2 never evaluated) → USER_EXISTED
            @Test
            void givenUsernameTakenAndEmailTaken_whenCreateUser_thenThrowsUserExisted() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(true);
                // C2 short-circuited by || → do NOT stub existsByEmail

                AppException ex = assertThrows(AppException.class,
                        () -> userService.createUser(validRequest));

                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_EXISTED);
                verify(userRepository, never()).save(any());
                verify(emailVerifyTokenRepository, never()).save(any());
                verify(notificationClient, never()).verifyEmailUser(any());
                verify(profileClient, never()).createProfile(any());
            }

            // Technique : Decision Table
            // TC ID     : TC-createUser-DT-02
            // Covers    : R2 — C1=T, C2=F (C2 short-circuited) → USER_EXISTED
            @Test
            void givenUsernameTakenEmailFree_whenCreateUser_thenThrowsUserExisted() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(true);

                AppException ex = assertThrows(AppException.class,
                        () -> userService.createUser(validRequest));

                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_EXISTED);
                verify(userRepository, never()).save(any());
            }

            // Technique : Decision Table
            // TC ID     : TC-createUser-DT-03
            // Covers    : R3 — C1=F, C2=T → USER_EXISTED
            @Test
            void givenUsernameFreeEmailTaken_whenCreateUser_thenThrowsUserExisted() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(false);
                when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

                AppException ex = assertThrows(AppException.class,
                        () -> userService.createUser(validRequest));

                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_EXISTED);
                verify(userRepository, never()).save(any());
            }

            // Technique : Decision Table
            // TC ID     : TC-createUser-DT-04
            // Covers    : R4 — C1=F, C2=F → success: save user, email token, notify, profile
            @Test
            void givenBothUsernameAndEmailFree_whenCreateUser_thenReturnsUserResponse() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(false);
                when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
                when(userRepository.save(mappedUser)).thenReturn(savedUser);
                when(profileMapper.convertFromUserCreationRequest(validRequest)).thenReturn(profileRequest);
                when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

                UserResponse result = userService.createUser(validRequest);

                assertThat(result).isEqualTo(mappedResponse);
                verify(userRepository).save(mappedUser);
                verify(emailVerifyTokenRepository).save(any(EmailVerifyToken.class));
                verify(notificationClient).verifyEmailUser(any(VerifyEmailRequest.class));
                verify(profileClient).createProfile(any(ProfileCreationRequest.class));
            }
        }

        // ── Cause-Effect Graph ───────────────────────────────────────
        @Nested
        @DisplayName("Cause-Effect Graph — downstream service wiring")
        class CauseEffectTests {

            // Technique : Cause-Effect Graph
            // TC ID     : TC-createUser-CE-01
            // Covers    : C1=F ∧ C2=F → E1(save) ∧ E2(emailToken) ∧ E3(notify) ∧ E4(profile) all fire
            @Test
            void givenAllConditionsFree_whenCreateUser_thenAllDownstreamEffectsOccur() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(false);
                when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
                when(userRepository.save(mappedUser)).thenReturn(savedUser);
                when(profileMapper.convertFromUserCreationRequest(validRequest)).thenReturn(profileRequest);
                when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

                userService.createUser(validRequest);

                // E1: user persisted
                verify(userRepository).save(mappedUser);

                // E2: email verify token saved, linked to the persisted user
                ArgumentCaptor<EmailVerifyToken> tokenCaptor =
                        ArgumentCaptor.forClass(EmailVerifyToken.class);
                verify(emailVerifyTokenRepository).save(tokenCaptor.capture());
                assertThat(tokenCaptor.getValue().getUsers()).isEqualTo(savedUser);
                assertThat(tokenCaptor.getValue().getEmailVerifyToken()).isNotBlank();

                // E3: notification called with token that matches the email token
                ArgumentCaptor<VerifyEmailRequest> notifyCaptor =
                        ArgumentCaptor.forClass(VerifyEmailRequest.class);
                verify(notificationClient).verifyEmailUser(notifyCaptor.capture());
                assertThat(notifyCaptor.getValue().getUserEmail()).isEqualTo(EMAIL);
                assertThat(notifyCaptor.getValue().getToken())
                        .isEqualTo(tokenCaptor.getValue().getEmailVerifyToken());

                // E4: profile created with the saved user's ID
                ArgumentCaptor<ProfileCreationRequest> profileCaptor =
                        ArgumentCaptor.forClass(ProfileCreationRequest.class);
                verify(profileClient).createProfile(profileCaptor.capture());
                assertThat(profileCaptor.getValue().getUserId()).isEqualTo(VALID_USER_ID);
            }

            // Technique : Cause-Effect Graph
            // TC ID     : TC-createUser-CE-02
            // Covers    : ¬C1 (username taken) → E5(exception) fires, E1–E4 all suppressed
            @Test
            void givenUsernameTaken_whenCreateUser_thenOnlyExceptionEffectFires() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(true);

                assertThrows(AppException.class, () -> userService.createUser(validRequest));

                verify(userRepository, never()).save(any());              // E1 suppressed
                verify(emailVerifyTokenRepository, never()).save(any());  // E2 suppressed
                verify(notificationClient, never()).verifyEmailUser(any()); // E3 suppressed
                verify(profileClient, never()).createProfile(any());       // E4 suppressed
            }

            // Technique : Cause-Effect Graph
            // TC ID     : TC-createUser-CE-03
            // Covers    : ¬C2 (email taken, username free) → E5 fires, E1–E4 suppressed
            @Test
            void givenEmailTaken_whenCreateUser_thenOnlyExceptionEffectFires() {
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(false);
                when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

                assertThrows(AppException.class, () -> userService.createUser(validRequest));

                verify(userRepository, never()).save(any());
                verify(emailVerifyTokenRepository, never()).save(any());
                verify(notificationClient, never()).verifyEmailUser(any());
                verify(profileClient, never()).createProfile(any());
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-createUser-BR-01
            // Covers    : branch — (existsByUserName || existsByEmail) evaluates to TRUE → throw
            @Test
            void givenUsernameDuplicate_whenCreateUser_thenConditionTrueBranchThrows() {
                // covers branch: if (existsByUserName || ...) → true
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(true);

                assertThrows(AppException.class, () -> userService.createUser(validRequest));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-createUser-BR-02
            // Covers    : branch — (existsByUserName || existsByEmail) evaluates to FALSE → fall-through to save
            @Test
            void givenNoDuplicates_whenCreateUser_thenConditionFalseBranchSavesUser() {
                // covers branch: if (existsByUserName || existsByEmail) → false
                when(userMapper.convertUserFromRequest(validRequest)).thenReturn(mappedUser);
                when(userRepository.existsByUserName(USERNAME)).thenReturn(false);
                when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
                when(userRepository.save(mappedUser)).thenReturn(savedUser);
                when(profileMapper.convertFromUserCreationRequest(validRequest)).thenReturn(profileRequest);
                when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

                assertDoesNotThrow(() -> userService.createUser(validRequest));
                verify(userRepository).save(any());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  getUser(String userId)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getUser()")
    class GetUserTests {

        // ── Equivalent Class ─────────────────────────────────────────
        @Nested
        @DisplayName("Equivalent Class")
        class EquivalentClassTests {

            // Technique : Equivalent Class (Positive)
            // TC ID     : TC-getUser-EC-01
            // Covers    : EC1 — valid userId that exists in DB → returns mapped UserResponse
            @ParameterizedTest(name = "[{index}] userId={0} (EC1 valid)")
            @CsvSource({
                    "user-uuid-1, representative valid UUID",
                    "user-uuid-2, another valid UUID in DB"
            })
            void givenExistingUserId_whenGetUser_thenReturnsMappedResponse(
                    String userId, String description) {
                when(userRepository.findById(userId)).thenReturn(Optional.of(savedUser));
                when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

                UserResponse result = userService.getUser(userId);

                assertThat(result).isEqualTo(mappedResponse);
                verify(userMapper).convertResponseFromUser(savedUser);
            }

            // Technique : Equivalent Class (Negative)
            // TC ID     : TC-getUser-EC-02
            // Covers    : EC2 — userId formatted correctly but not present in DB → USER_NOT_EXIST
            @ParameterizedTest(name = "[{index}] userId={0} (EC2 invalid — not in DB)")
            @CsvSource({
                    "missing-id-aaa,  valid format but absent from DB",
                    "ghost-user-bbb,  another absent ID"
            })
            void givenAbsentUserId_whenGetUser_thenThrowsUserNotExist(
                    String userId, String description) {
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.getUser(userId));

                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
                verify(userMapper, never()).convertResponseFromUser(any());
            }
        }

        // ── Boundary Value ────────────────────────────────────────────
        @Nested
        @DisplayName("Boundary Value — userId string boundaries")
        class BoundaryValueTests {

            // Technique : Boundary Value Analysis
            // TC ID     : TC-getUser-BV-01
            // Covers    : BV min-1 — userId is null (below minimum valid input)
            @Test
            void givenNullUserId_whenGetUser_thenThrowsUserNotExist() {
                when(userRepository.findById(null)).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.getUser(null));
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            }

            // Technique : Boundary Value Analysis
            // TC ID     : TC-getUser-BV-02
            // Covers    : BV min — userId is empty string (length = 0, minimum non-null)
            @Test
            void givenEmptyStringUserId_whenGetUser_thenThrowsUserNotExist() {
                when(userRepository.findById("")).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.getUser(""));
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            }

            // Technique : Boundary Value Analysis
            // TC ID     : TC-getUser-BV-03
            // Covers    : BV min+1 — userId is single character (just above empty boundary)
            @Test
            void givenSingleCharUserId_whenGetUser_thenThrowsUserNotExist() {
                when(userRepository.findById("x")).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.getUser("x"));
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            }

            // Technique : Boundary Value Analysis
            // TC ID     : TC-getUser-BV-04
            // Covers    : BV max+1 — userId is 256-char string (exceeds practical UUID max)
            @Test
            void givenOversizedUserId_whenGetUser_thenThrowsUserNotExist() {
                String longId = "u".repeat(256);
                when(userRepository.findById(longId)).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.getUser(longId));
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-getUser-BR-01
            // Covers    : branch — findById returns Optional.empty() → orElseThrow fires → exception
            @Test
            void givenUserNotInDb_whenGetUser_thenOrElseThrowBranchFires() {
                // covers branch: findById → empty → throw AppException
                when(userRepository.findById(VALID_USER_ID)).thenReturn(Optional.empty());

                assertThrows(AppException.class, () -> userService.getUser(VALID_USER_ID));
                verify(userMapper, never()).convertResponseFromUser(any());
            }

            // Technique : Branch Coverage
            // TC ID     : TC-getUser-BR-02
            // Covers    : branch — findById returns Optional.of(user) → proceeds to mapper call
            @Test
            void givenUserInDb_whenGetUser_thenPresentBranchCallsMapper() {
                // covers branch: findById → present → map to response
                when(userRepository.findById(VALID_USER_ID)).thenReturn(Optional.of(savedUser));
                when(userMapper.convertResponseFromUser(savedUser)).thenReturn(mappedResponse);

                UserResponse result = userService.getUser(VALID_USER_ID);

                assertThat(result).isNotNull();
                verify(userMapper).convertResponseFromUser(savedUser);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  deleteUser(String userId)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        // ── Equivalent Class ─────────────────────────────────────────
        @Nested
        @DisplayName("Equivalent Class")
        class EquivalentClassTests {

            // Technique : Equivalent Class (Positive)
            // TC ID     : TC-deleteUser-EC-01
            // Covers    : EC1 — userId present in DB → userStatus set to DELETE
            @ParameterizedTest(name = "[{index}] userId={0} exists in DB")
            @CsvSource({
                    "user-uuid-1, primary user",
                    "user-uuid-2, secondary user"
            })
            void givenExistingUserId_whenDeleteUser_thenStatusBecomesDelete(
                    String userId, String description) {
                User user = User.builder().userId(userId)
                        .userStatus(UserStatus.ACTIVE).build();
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));

                userService.deleteUser(userId);

                assertThat(user.getUserStatus()).isEqualTo(UserStatus.DELETE);
            }

            // Technique : Equivalent Class (Negative)
            // TC ID     : TC-deleteUser-EC-02
            // Covers    : EC2 — userId not in DB → USER_NOT_EXIST, no status change
            @ParameterizedTest(name = "[{index}] userId={0} absent from DB")
            @CsvSource({
                    "ghost-id-aaa, non-existent user",
                    "ghost-id-bbb, another non-existent user"
            })
            void givenAbsentUserId_whenDeleteUser_thenThrowsUserNotExist(
                    String userId, String description) {
                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.deleteUser(userId));
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            }
        }

        // ── State Transition ──────────────────────────────────────────
        @Nested
        @DisplayName("State Transition — UserStatus")
        class StateTransitionTests {

            // Technique : State Transition
            // TC ID     : TC-deleteUser-ST-01
            // Covers    : S2(ACTIVE) + deleteUser → S3(DELETE) — valid transition
            @Test
            void givenActiveUser_whenDeleteUser_thenTransitionToDeleteState() {
                // initial state: S2 = ACTIVE
                User user = User.builder()
                        .userId(VALID_USER_ID)
                        .userStatus(UserStatus.ACTIVE)
                        .build();
                when(userRepository.findById(VALID_USER_ID)).thenReturn(Optional.of(user));

                userService.deleteUser(VALID_USER_ID);

                // final state: S3 = DELETE
                assertThat(user.getUserStatus()).isEqualTo(UserStatus.DELETE);
            }

            // Technique : State Transition
            // TC ID     : TC-deleteUser-ST-02
            // Covers    : S1(INACTIVE) + deleteUser → S3(DELETE) — valid transition from unverified state
            @Test
            void givenInactiveUser_whenDeleteUser_thenTransitionToDeleteState() {
                // initial state: S1 = INACTIVE
                User user = User.builder()
                        .userId(VALID_USER_ID)
                        .userStatus(UserStatus.INACTIVE)
                        .build();
                when(userRepository.findById(VALID_USER_ID)).thenReturn(Optional.of(user));

                userService.deleteUser(VALID_USER_ID);

                // final state: S3 = DELETE
                assertThat(user.getUserStatus()).isEqualTo(UserStatus.DELETE);
            }

            // Technique : State Transition
            // TC ID     : TC-deleteUser-ST-03
            // Covers    : Invalid transition — userId not found → exception, no state change
            @Test
            void givenNonExistentUser_whenDeleteUser_thenTransitionRejectedWithException() {
                when(userRepository.findById("no-such-id")).thenReturn(Optional.empty());

                AppException ex = assertThrows(AppException.class,
                        () -> userService.deleteUser("no-such-id"));

                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-deleteUser-BR-01
            // Covers    : branch — findById returns empty → orElseThrow fires
            @Test
            void givenMissingUser_whenDeleteUser_thenOrElseThrowBranchFires() {
                // covers branch: findById → empty → throw
                when(userRepository.findById("missing")).thenReturn(Optional.empty());

                assertThrows(AppException.class, () -> userService.deleteUser("missing"));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-deleteUser-BR-02
            // Covers    : branch — findById returns user → setUserStatus(DELETE) executes
            @Test
            void givenExistingUser_whenDeleteUser_thenPresentBranchSetsDeleteStatus() {
                // covers branch: findById → present → user.setUserStatus(DELETE)
                User user = User.builder()
                        .userId(VALID_USER_ID)
                        .userStatus(UserStatus.ACTIVE)
                        .build();
                when(userRepository.findById(VALID_USER_ID)).thenReturn(Optional.of(user));

                userService.deleteUser(VALID_USER_ID);

                assertThat(user.getUserStatus()).isEqualTo(UserStatus.DELETE);
            }
        }
    }

    // ==================== COVERAGE SUMMARY ====================
    // Total test methods : 23
    // Techniques applied : Decision Table, Cause-Effect Graph,
    //                      Equivalent Class, Boundary Value,
    //                      State Transition, Branch Coverage
    // Branches covered   : 6 / 6
    //   createUser  — if (existsByUserName || existsByEmail) → true / false   [2 branches]
    //   getUser     — findById → empty / present                              [2 branches]
    //   deleteUser  — findById → empty / present                              [2 branches]
    // V(G) complexity    : createUser=2, getUser=2, deleteUser=2  →  6 total min test cases
    // ==========================================================
}
