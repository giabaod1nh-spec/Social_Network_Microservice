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

@TestPropertySource("/test.properties")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** Same value as {@link AuthService} static secret (must verify tokens the service accepts). */
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

    @Test
    void authenticateUser_whenUserMissing_throwsUserNotExist() {
        when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.empty());

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.authenticateUser(authRequest()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void authenticateUser_whenPasswordMismatch_throwsAuthenticatedFailed() {
        when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, activeUser.getPassword())).thenReturn(false);

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.authenticateUser(authRequest()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATED_FAILED);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void authenticateUser_whenValid_returnsTokensAndPersistsRefresh() throws Exception {
        when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, activeUser.getPassword())).thenReturn(true);

        AuthResponse response = authService.authenticateUser(authRequest());

        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();

        SignedJWT access = SignedJWT.parse(response.getAccessToken());
        assertThat(access.getJWTClaimsSet().getStringClaim("tokenType")).isEqualTo("ACCESS");

        SignedJWT refresh = SignedJWT.parse(response.getRefreshToken());
        assertThat(refresh.getJWTClaimsSet().getStringClaim("tokenType")).isEqualTo("REFRESH");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUsers()).isEqualTo(activeUser);
        assertThat(captor.getValue().getRefreshToken()).isEqualTo(response.getRefreshToken());
    }

    @Test
    void introspectToken_whenMalformed_throwsTokenInvalid() {
        IntroSpectRequest request = IntroSpectRequest.builder().token("not-a-jwt").build();

        AppException ex = assertThrows(AppException.class, () -> authService.introspectToken(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    void introspectToken_whenValid_returnsValid() throws Exception {
        String jwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", futureExpiry());

        IntroSpectResponse response =
                authService.introspectToken(IntroSpectRequest.builder().token(jwt).build());

        assertThat(response.isValid()).isTrue();
    }

    @Test
    void introspectToken_whenExpired_throwsTokenExpired() throws Exception {
        String jwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", pastExpiry());

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.introspectToken(IntroSpectRequest.builder().token(jwt).build()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void refreshToken_whenTokenTypeNotRefresh_throwsTokenTypeInvalid() throws Exception {
        String accessJwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", futureExpiry());
        RefreshTokenRequest request = RefreshTokenRequest.builder().refreshToken(accessJwt).build();

        AppException ex = assertThrows(
                AppException.class, () -> authService.refreshTokenAfterTimeOut(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_TYPE_INVALID);
        verify(refreshTokenRepository, never()).delete(any());
    }

    @Test
    void refreshToken_whenNotInDatabase_throwsTokenNotFound() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry());
        when(refreshTokenRepository.findByRefreshToken(refreshJwt)).thenReturn(Optional.empty());

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.refreshTokenAfterTimeOut(
                        RefreshTokenRequest.builder().refreshToken(refreshJwt).build()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_NOT_FOUND);
    }

    @Test
    void refreshToken_whenUserMissing_throwsUserNotExist() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry());
        RefreshToken stored = RefreshToken.builder()
                .tokenId(1L)
                .refreshToken(refreshJwt)
                .users(activeUser)
                .build();
        when(refreshTokenRepository.findByRefreshToken(refreshJwt)).thenReturn(Optional.of(stored));
        when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.empty());

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.refreshTokenAfterTimeOut(
                        RefreshTokenRequest.builder().refreshToken(refreshJwt).build()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_EXIST);
    }

    @Test
    void refreshToken_whenValid_returnsNewTokensAndDeletesOld() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry());
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
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotEqualTo(refreshJwt);

        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void logOut_whenNoAuthorizationHeader_skipsRedisBlacklist() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry());
        when(httpServletRequest.getHeader("Authorization")).thenReturn(null);

        authService.logOut(LogOutRequest.builder().refreshToken(refreshJwt).build());

        verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken(refreshJwt);
        verify(redisTokenService, never()).blackListToken(anyString(), anyLong());
    }

    @Test
    void logOut_whenBearerAccessTokenValid_blacklistsWithPositiveTtl() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry());
        String accessJwt = signHs512Jwt(USER_ID, USERNAME, "ACCESS", futureExpiry());
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + accessJwt);

        authService.logOut(LogOutRequest.builder().refreshToken(refreshJwt).build());

        verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken(refreshJwt);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisTokenService).blackListToken(eq(accessJwt), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isPositive();
    }

    @Test
    void logOut_whenBearerTokenExpired_doesNotBlacklist() throws Exception {
        String refreshJwt = signHs512Jwt(USER_ID, USERNAME, "REFRESH", futureExpiry());
        String expiredAccess = signHs512Jwt(USER_ID, USERNAME, "ACCESS", pastExpiry());
        when(httpServletRequest.getHeader("Authorization")).thenReturn("Bearer " + expiredAccess);

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.logOut(LogOutRequest.builder().refreshToken(refreshJwt).build()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken(refreshJwt);
        verify(redisTokenService, never()).blackListToken(anyString(), anyLong());
    }

    @Test
    void verifyEmail_whenTokenUnknown_throwsVerifyEmailTokenInvalid() {
        when(emailVerifyTokenRepository.findByEmailVerifyToken("bad-token")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.verifyEmail("bad-token"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VERIFY_EMAIL_TOKEN_INVALID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_whenValid_updatesUserAndDeletesToken() {
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

    private static Date futureExpiry() {
        return new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli());
    }

    private static Date pastExpiry() {
        return new Date(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli());
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
