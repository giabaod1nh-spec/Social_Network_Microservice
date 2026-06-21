package com.identity_service.identity.service;

import com.identity_service.identity.dto.request.LogOutRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.entity.RefreshToken;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.RefreshTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.service.impl.AuthService;
import com.identity_service.identity.service.impl.RedisTokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * B5 — CAUSE-EFFECT GRAPH for AuthService token operations
 *
 * Feature 1: AuthService.refreshTokenAfterTimeOut  (Cause-Effect graph for refresh flow)
 *   Causes:
 *     C1 = refreshToken signature is valid
 *     C2 = token type claim = "REFRESH"
 *     C3 = refresh token exists in DB
 *     C4 = user associated with token exists in DB
 *   Effects:
 *     E1 = new access + refresh tokens returned
 *     E2 = old refresh token deleted (revoked)
 *     E3 = TOKEN_INVALID thrown
 *     E4 = TOKEN_TYPE_INVALID thrown
 *     E5 = TOKEN_NOT_FOUND thrown
 *     E6 = USER_NOT_EXIST thrown
 *
 * Feature 2: AuthService.logOut (Cause-Effect graph for logout / blacklist flow)
 *   Causes:
 *     C1 = refreshToken deleted from DB
 *     C2 = Authorization header present and starts with "Bearer "
 *     C3 = access token signature is valid and not expired (ttl > 0)
 *   Effects:
 *     E1 = access token blacklisted in Redis
 *     E2 = only refresh token deleted (no blacklist action)
 *     E3 = no action on access token (expired — ttl ≤ 0)
 *
 * Decision table derived from Cause-Effect graph — each column = one test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[B5-CE] AuthService — Cause-Effect Graph")
class AuthTokenCauseEffectTest {

    private static final String SECRET_KEY = "bc4ab14dbbb049a77290ca0196a37d597d399a4fd5d8ccf2b831191d1995e84e";
    private static final String USER_ID    = "user-ce-001";
    private static final String USERNAME   = "ceuser";

    @InjectMocks
    private AuthService authService;

    // STUB: all dependencies of AuthService
    @Mock private HttpServletRequest         httpServletRequest;
    @Mock private UserRepository             userRepository;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private RefreshTokenRepository     refreshTokenRepository;
    @Mock private RedisTokenService          redisTokenService;
    @Mock private EmailVerifyTokenRepository emailVerifyTokenRepository;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .userId(USER_ID)
                .userName(USERNAME)
                .password("$encoded$")
                .emailVerified(true)
                .userStatus(UserStatus.ACTIVE)
                .build();
    }

    /* ════════════════════════════════════════════════════════════════════
     * CAUSE-EFFECT: refreshTokenAfterTimeOut
     * ════════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("CE — refreshTokenAfterTimeOut Cause-Effect columns")
    class RefreshTokenCauseEffect {

        /** CE-REFRESH-001 | C1=F → E3 (TOKEN_INVALID — invalid signature) */
        @Test
        @DisplayName("CE-REFRESH-001 | C1=F — invalid signature → TOKEN_INVALID (1007)")
        void should_throwTokenInvalid_when_refreshTokenSignatureIsInvalid() throws Exception {
            // Build token signed with WRONG key
            String badToken = buildRefreshToken(
                    "wrong-secret-key-that-is-exactly-sixty-four-bytes-long-padding!",
                    Instant.now().plus(1, ChronoUnit.DAYS), "REFRESH");

            RefreshTokenRequest req = new RefreshTokenRequest(badToken);

            assertThatThrownBy(() -> authService.refreshTokenAfterTimeOut(req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_INVALID);
        }

        /** CE-REFRESH-002 | C1=T, C2=F → E4 (TOKEN_TYPE_INVALID — type is "ACCESS") */
        @Test
        @DisplayName("CE-REFRESH-002 | C2=F — token type=ACCESS → TOKEN_TYPE_INVALID (1009)")
        void should_throwTokenTypeInvalid_when_accessTokenSubmittedAsRefreshToken() throws Exception {
            // Build valid token with type=ACCESS (not REFRESH)
            String accessToken = buildRefreshToken(SECRET_KEY,
                    Instant.now().plus(1, ChronoUnit.HOURS), "ACCESS");

            RefreshTokenRequest req = new RefreshTokenRequest(accessToken);

            assertThatThrownBy(() -> authService.refreshTokenAfterTimeOut(req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_TYPE_INVALID);
        }

        /** CE-REFRESH-003 | C1=T, C2=T, C3=F → E5 (TOKEN_NOT_FOUND — token not in DB) */
        @Test
        @DisplayName("CE-REFRESH-003 | C3=F — refresh token not in DB → TOKEN_NOT_FOUND (1010)")
        void should_throwTokenNotFound_when_refreshTokenMissingFromDatabase() throws Exception {
            String validRefresh = buildRefreshToken(SECRET_KEY,
                    Instant.now().plus(1, ChronoUnit.DAYS), "REFRESH");

            // STUB: token not persisted in DB
            when(refreshTokenRepository.findByRefreshToken(validRefresh))
                    .thenReturn(Optional.empty());

            RefreshTokenRequest req = new RefreshTokenRequest(validRefresh);

            assertThatThrownBy(() -> authService.refreshTokenAfterTimeOut(req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_NOT_FOUND);
        }

        /** CE-REFRESH-004 | C1=T, C2=T, C3=T, C4=F → E6 (USER_NOT_EXIST) */
        @Test
        @DisplayName("CE-REFRESH-004 | C4=F — user deleted after token issued → USER_NOT_EXIST (1001)")
        void should_throwUserNotExist_when_userDeletedAfterTokenIssued() throws Exception {
            String validRefresh = buildRefreshToken(SECRET_KEY,
                    Instant.now().plus(1, ChronoUnit.DAYS), "REFRESH");

            RefreshToken storedToken = RefreshToken.builder()
                    .refreshToken(validRefresh)
                    .users(activeUser)
                    .build();

            // STUB: token found in DB
            when(refreshTokenRepository.findByRefreshToken(validRefresh))
                    .thenReturn(Optional.of(storedToken));
            // STUB: user no longer in DB (deleted between token issue and refresh)
            when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.empty());

            RefreshTokenRequest req = new RefreshTokenRequest(validRefresh);

            assertThatThrownBy(() -> authService.refreshTokenAfterTimeOut(req))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_EXIST);
        }

        /**
         * CE-REFRESH-005 | C1=T, C2=T, C3=T, C4=T → E1+E2
         * Detects SEED-004: old token must be deleted (revoked).
         */
        @Test
        @DisplayName("CE-REFRESH-005 | All causes true → new tokens returned, old token revoked (SEED-004 guard)")
        void should_returnNewTokensAndRevokeOldToken_when_allCausesAreTrue() throws Exception {
            String validRefresh = buildRefreshToken(SECRET_KEY,
                    Instant.now().plus(1, ChronoUnit.DAYS), "REFRESH");

            RefreshToken storedToken = RefreshToken.builder()
                    .refreshToken(validRefresh)
                    .users(activeUser)
                    .build();

            when(refreshTokenRepository.findByRefreshToken(validRefresh))
                    .thenReturn(Optional.of(storedToken));
            when(userRepository.findByUserName(USERNAME)).thenReturn(Optional.of(activeUser));
            when(refreshTokenRepository.save(any())).thenReturn(null);

            var result = authService.refreshTokenAfterTimeOut(new RefreshTokenRequest(validRefresh));

            //assertThat(result.getAuthenticated()).isTrue();
            assertThat(result.getAccessToken()).isNotBlank();
            assertThat(result.getRefreshToken()).isNotBlank();
            assertThat(result.getRefreshToken()).isNotEqualTo(validRefresh);

            // E2: old token MUST be deleted — if SEED-004 is active, this fails
            verify(refreshTokenRepository).delete(storedToken);
        }
    }

    /* ════════════════════════════════════════════════════════════════════
     * CAUSE-EFFECT: logOut
     * ════════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("CE — logOut Cause-Effect columns")
    class LogOutCauseEffect {

        /** CE-LOGOUT-001 | C2=F (no Authorization header) → E2 (only refresh deleted) */
        @Test
        @DisplayName("CE-LOGOUT-001 | C2=F — no Authorization header → refresh deleted, no blacklist")
        void should_onlyDeleteRefreshToken_when_noAuthorizationHeaderPresent() throws Exception {
            // STUB: no Authorization header
            when(httpServletRequest.getHeader("Authorization")).thenReturn(null);

            authService.logOut(new LogOutRequest("some.refresh.token"));

            verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken("some.refresh.token");
            verifyNoInteractions(redisTokenService);
        }

        /** CE-LOGOUT-002 | C2=T, C3=T (valid, non-expired access token) → E1 (blacklisted) */
        @Test
        @DisplayName("CE-LOGOUT-002 | C2=T, C3=T — valid access token → blacklisted in Redis")
        void should_blacklistAccessToken_when_validBearerTokenPresent() throws Exception {
            String validAccess = buildRefreshToken(SECRET_KEY,
                    Instant.now().plus(1, ChronoUnit.HOURS), "ACCESS");

            // STUB: Authorization header with valid access token
            when(httpServletRequest.getHeader("Authorization"))
                    .thenReturn("Bearer " + validAccess);
            doNothing().when(redisTokenService).blackListToken(anyString(), anyLong());

            authService.logOut(new LogOutRequest("some.refresh.token"));

            verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken("some.refresh.token");
            verify(redisTokenService).blackListToken(eq(validAccess), anyLong());
        }

        /** CE-LOGOUT-003 | C2=T, C3=F (expired access token) → E3 (no blacklist — ttl ≤ 0) */
        @Test
        @DisplayName("CE-LOGOUT-003 | C3=F — expired access token → no blacklist action")
        void should_skipBlacklist_when_accessTokenIsAlreadyExpired() throws Exception {
            // Build an already-expired token
            String expiredAccess = buildRefreshToken(SECRET_KEY,
                    Instant.now().minus(5, ChronoUnit.MINUTES), "ACCESS");

            when(httpServletRequest.getHeader("Authorization"))
                    .thenReturn("Bearer " + expiredAccess);

            // Expired token: verifyToken() will throw TOKEN_EXPIRED
            // The logOut method must handle this gracefully
            assertThatThrownBy(() -> authService.logOut(new LogOutRequest("some.refresh.token")))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.TOKEN_EXPIRED);

            verify(refreshTokenRepository).deleteRefreshTokenByRefreshToken("some.refresh.token");
            verifyNoInteractions(redisTokenService);
        }
    }

    /* ────────────────────────────────────────────────────────────────────
     * Helper: build a signed JWT for testing
     * ────────────────────────────────────────────────────────────────── */
    private String buildRefreshToken(String secret, Instant expiry, String tokenType) throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(USER_ID)
                .expirationTime(Date.from(expiry))
                .issuer("baoxdev.com")
                .issueTime(new Date())
                .claim("tokenType", tokenType)
                .claim("userName", USERNAME)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        JWSSigner signer = new MACSigner(secret.getBytes());
        jwt.sign(signer);
        return jwt.serialize();
    }
}
