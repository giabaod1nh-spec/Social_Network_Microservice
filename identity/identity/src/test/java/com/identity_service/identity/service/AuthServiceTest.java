package com.identity_service.identity.service;

import com.identity_service.identity.dto.request.AuthRequest;
import com.identity_service.identity.dto.request.IntroSpectRequest;
import com.identity_service.identity.dto.request.LogOutRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.dto.response.AuthResponse;
import com.identity_service.identity.dto.response.IntroSpectResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.entity.EmailVerifyToken;
import com.identity_service.identity.model.entity.RefreshToken;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.RefreshTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.service.impl.AuthService;
import com.identity_service.identity.service.impl.RedisTokenService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService unit tests: ECP per parameter, BVA on token expiry, decision-table rows for logOut booleans.
 */
@TestPropertySource("/test.properties")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET_KEY =
            "bc4ab14dbbb049a77290ca0196a37d597d399a4fd5d8ccf2b831191d1995e84e";

    private static final String USER_ID = "user-1";
    private static final String USERNAME = "john";
    private static final String RAW_PASSWORD = "secret-pass";

    @InjectMocks
    private AuthService authService;

    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RedisTokenService redisTokenService;
    @Mock
    private EmailVerifyTokenRepository emailVerifyTokenRepository;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .userId(USER_ID)
                .userName(USERNAME)
                .email("john@example.com")
                .password("encoded-hash")
                .emailVerified(true)
                .userStatus(UserStatus.ACTIVE)
                .build();
    }

    // --- authenticateUser: ECP(userName) x ECP(password) x passwordMatch ---

    @ParameterizedTest(name = "authenticateUser_{3}")
    @CsvSource({
            "ghost, secret-pass, false, userNotFound_throwsUserNotExist",
            "john, wrong-pass, false, passwordMismatch_throwsAuthenticatedFailed",
    })
    void authenticateUser_invalidCredentials_throws(
            String userName, String password, boolean passwordMatches, String scenario) {
        when(userRepository.findByUserName(userName)).thenReturn(
                "ghost".equals(userName) ? Optional.empty() : Optional.of(activeUser));
        if (!"ghost".equals(userName)) {
            when(passwordEncoder.matches(password, activeUser.getPassword())).thenReturn(passwordMatches);
        }

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.authenticateUser(AuthRequest.builder().userName(userName).password(password).build()));

        ErrorCode expected =
                "ghost".equals(userName) ? ErrorCode.USER_NOT_EXIST : ErrorCode.AUTHENTICATED_FAILED;
        assertThat(ex.getErrorCode()).isEqualTo(expected);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void authenticateUser_passwordMatches_returnsTokensAndPersistsRefresh() throws Exception {
        when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, activeUser.getPassword())).thenReturn(true);

        AuthResponse response = authService.authenticateUser(authRequest());

        assertThat(response.isAuthenticated()).isTrue();
        SignedJWT access = SignedJWT.parse(response.getAccessToken());
        assertThat(access.getJWTClaimsSet().getStringClaim("tokenType")).isEqualTo("ACCESS");
        SignedJWT refresh = SignedJWT.parse(response.getRefreshToken());
        assertThat(refresh.getJWTClaimsSet().getStringClaim("tokenType")).isEqualTo("REFRESH");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUsers()).isEqualTo(activeUser);
    }

    // --- introspectToken: BVA on expiry boundary ---

    @ParameterizedTest(name = "introspectToken_{2}")
    @CsvSource({
            "not-a-jwt, TOKEN_INVALID, malformed_throwsTokenInvalid",
    })
    void introspectToken_invalidTokenPartition_throws(String token, ErrorCode expected, String scenario) {
        AppException ex = assertThrows(
                AppException.class,
                () -> authService.introspectToken(IntroSpectRequest.builder().token(token).build()));
        assertThat(ex.getErrorCode()).isEqualTo(expected);
    }

    @Test
    void introspectToken_validFutureExpiry_returnsValid() throws Exception {
        String jwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", futureExpiry(1, ChronoUnit.HOURS));

        IntroSpectResponse response =
                authService.introspectToken(IntroSpectRequest.builder().token(jwt).build());

        assertThat(response.isValid()).isTrue();
    }

    @ParameterizedTest(name = "introspectToken_{1}")
    @CsvSource({
            "PAST, TOKEN_EXPIRED, justOutsidePast_throwsTokenExpired",
            "FUTURE, VALID, justInsideFuture_returnsValid",
    })
    void introspectToken_expiryBoundary_analysis(String boundary, String outcome, String scenario)
            throws Exception {
        Date expiry = "PAST".equals(boundary)
                ? pastExpiry(1, ChronoUnit.SECONDS)
                : futureExpiry(5, ChronoUnit.MINUTES);
        String jwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", expiry);

        if ("TOKEN_EXPIRED".equals(outcome)) {
            AppException ex = assertThrows(
                    AppException.class,
                    () -> authService.introspectToken(IntroSpectRequest.builder().token(jwt).build()));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        } else {
            IntroSpectResponse response =
                    authService.introspectToken(IntroSpectRequest.builder().token(jwt).build());
            assertThat(response.isValid()).isTrue();
        }
    }

    // --- refreshToken: decision paths ---

    @ParameterizedTest(name = "refreshTokenAfterTimeOut_{2}")
    @CsvSource({
            "ACCESS, TOKEN_TYPE_INVALID, wrongTokenType_throwsTokenTypeInvalid",
    })
    void refreshTokenAfterTimeOut_invalidTokenType_throws(
            String tokenType, ErrorCode expected, String scenario) throws Exception {
        String jwt = signHs512Jwt(USER_ID, USERNAME, tokenType, futureExpiry(1, ChronoUnit.HOURS));
        AppException ex = assertThrows(
                AppException.class,
                () -> authService.refreshTokenAfterTimeOut(
                        RefreshTokenRequest.builder().refreshToken(jwt).build()));
        assertThat(ex.getErrorCode()).isEqualTo(expected);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @ParameterizedTest(name = "refreshTokenAfterTimeOut_{1}")
    @CsvSource({
            "TOKEN_NOT_FOUND, tokenMissingInDb_throwsTokenNotFound",
            "USER_NOT_EXIST, userMissingInDb_throwsUserNotExist",
    })
    void refreshTokenAfterTimeOut_repositoryPartitions_throws(
            ErrorCode expected, String scenario) throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry(1, ChronoUnit.HOURS));
        RefreshToken stored = RefreshToken.builder()
                .tokenId(1L)
                .refreshToken(refreshJwt)
                .users(activeUser)
                .build();

        if (expected == ErrorCode.TOKEN_NOT_FOUND) {
            when(refreshTokenRepository.findByRefreshToken(refreshJwt)).thenReturn(Optional.empty());
        } else {
            when(refreshTokenRepository.findByRefreshToken(refreshJwt)).thenReturn(Optional.of(stored));
            when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.empty());
        }

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.refreshTokenAfterTimeOut(
                        RefreshTokenRequest.builder().refreshToken(refreshJwt).build()));
        assertThat(ex.getErrorCode()).isEqualTo(expected);
    }

    @Test
    void refreshTokenAfterTimeOut_validRefresh_returnsNewTokensAndDeletesOld() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry(1, ChronoUnit.HOURS));
        RefreshToken stored = RefreshToken.builder()
                .tokenId(1L)
                .refreshToken(refreshJwt)
                .users(activeUser)
                .build();
        when(refreshTokenRepository.findByRefreshToken(refreshJwt)).thenReturn(Optional.of(stored));
        when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.of(activeUser));

        AuthResponse response = authService.refreshTokenAfterTimeOut(
                RefreshTokenRequest.builder().refreshToken(refreshJwt).build());

        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getRefreshToken()).isNotEqualTo(refreshJwt);
        verify(refreshTokenRepository).delete(stored);
    }

    // --- logOut: decision table (headerPresent x bearerPrefix x ttlPositive) ---

    /**
     * Decision table for logOut access-token blacklist:
     * | headerPresent | startsWithBearer | ttl>0 | blacklist |
     * | F             | -                | -     | no        |
     * | T             | F                | -     | no        |
     * | T             | T                | T     | yes       |
     * | T             | T                | F     | no (expired throws) |
     */
    @ParameterizedTest(name = "logOut_{1}")
    @CsvSource({
            "NULL, headerNull_skipsBlacklist",
            "BASIC, headerNotBearer_skipsBlacklist",
            "BEARER_VALID, bearerValid_blacklistsToken",
    })
    void logOut_authorizationDecisionTable(String headerKind, String scenario) throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry(1, ChronoUnit.HOURS));

        switch (headerKind) {
            case "NULL" -> when(httpServletRequest.getHeader("Authorization")).thenReturn(null);
            case "BASIC" -> when(httpServletRequest.getHeader("Authorization")).thenReturn("Basic abc");
            case "BEARER_VALID" -> {
                String accessJwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", futureExpiry(1, ChronoUnit.HOURS));
                when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + accessJwt);
                authService.logOut(LogOutRequest.builder().refreshToken(refreshJwt).build());
                verify(redisTokenService).blackListToken(eq(accessJwt), anyLong());
                verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken(refreshJwt);
                return;
            }
            default -> throw new IllegalArgumentException(headerKind);
        }

        authService.logOut(LogOutRequest.builder().refreshToken(refreshJwt).build());
        verify(redisTokenService, never()).blackListToken(anyString(), anyLong());
        verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken(refreshJwt);
    }

    @Test
    void logOut_bearerExpiredAccess_throwsTokenExpiredAndSkipsBlacklist() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry(1, ChronoUnit.HOURS));
        String expiredAccess = signHs512Jwt(USER_ID, USERNAME, "ACCESS", pastExpiry(1, ChronoUnit.HOURS));
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + expiredAccess);

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.logOut(LogOutRequest.builder().refreshToken(refreshJwt).build()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        verify(redisTokenService, never()).blackListToken(anyString(), anyLong());
    }

    // --- verifyEmail ---

    @ParameterizedTest(name = "verifyEmail_{1}")
    @NullAndEmptySource
    @ValueSource(strings = {"bad-token", "unknown"})
    void verifyEmail_invalidTokenPartition_throws(String token) {
        when(emailVerifyTokenRepository.findByEmailVerifyToken(token)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.verifyEmail(token));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VERIFY_EMAIL_TOKEN_INVALID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_validToken_updatesUserAndDeletesToken() {
        User pending = User.builder()
                .userId(USER_ID)
                .userName(USERNAME)
                .email("john@example.com")
                .password("hash")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();
        EmailVerifyToken token = EmailVerifyToken.builder()
                .id("evt-1")
                .emailVerifyToken("verify-me")
                .users(pending)
                .expiredAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
        when(emailVerifyTokenRepository.findByEmailVerifyToken("verify-me")).thenReturn(Optional.of(token));

        authService.verifyEmail("verify-me");

        assertThat(pending.getEmailVerified()).isTrue();
        assertThat(pending.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(pending);
        verify(emailVerifyTokenRepository).delete(token);
    }

    private static AuthRequest authRequest() {
        return AuthRequest.builder().userName(USERNAME).password(RAW_PASSWORD).build();
    }

    private static Date futureExpiry(long amount, ChronoUnit unit) {
        return new Date(Instant.now().plus(amount, unit).toEpochMilli());
    }

    private static Date pastExpiry(long amount, ChronoUnit unit) {
        return new Date(Instant.now().minus(amount, unit).toEpochMilli());
    }

    private static String signHs512Jwt(String userId, String userName, String tokenType, Date expiry)
            throws JOSEException, ParseException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .expirationTime(expiry)
                .issuer("baoxdev.com")
                .issueTime(new Date(Instant.now().toEpochMilli()))
                .claim("tokenType", tokenType)
                .claim("userName", userName)
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        JWSSigner signer = new MACSigner(SECRET_KEY.getBytes());
        jwt.sign(signer);
        return jwt.serialize();
    }
}
