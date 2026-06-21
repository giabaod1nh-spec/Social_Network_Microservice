package com.identity_service.identity.service;

import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.RefreshTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.service.impl.AuthService;
import com.identity_service.identity.service.impl.RedisTokenService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C2 — McCABE CYCLOMATIC COMPLEXITY / WHITE-BOX for AuthService.verifyToken
 *
 * CFG analysis (see docs/test/CFG_AuthService_verifyToken.md):
 *   V(G) = E − N + 2P = 7 − 5 + 2 = 4
 *   → 4 independent test paths required
 *
 * PATH-1: N1→N3→N5  — valid token, not expired  → returns SignedJWT
 * PATH-2: N1→N2     — invalid signature          → TOKEN_INVALID (1007)
 * PATH-3: N1→N3→N4  — valid signature, expired   → TOKEN_EXPIRED (1008)
 * PATH-4: N1→(parse exception propagates)        — malformed string → ParseException
 *
 * Statement coverage:  7/7  = 100%
 * Branch coverage:     4/4  = 100%
 * Condition coverage:  2/2  = 100%
 *
 * SEED-001 detection:
 *   If expiry check is inverted (after instead of before), PATH-1 will throw
 *   TOKEN_EXPIRED for a valid non-expired token, and PATH-3 will NOT throw
 *   for an expired token — both tests fail, revealing the seed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[C2-CFG] AuthService.verifyToken — McCabe Paths")
class AuthVerifyTokenCFGTest {

    private static final String CORRECT_SECRET =
            "bc4ab14dbbb049a77290ca0196a37d597d399a4fd5d8ccf2b831191d1995e84e";
    private static final String WRONG_SECRET =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String USER_ID  = "cfg-user-001";
    private static final String USERNAME = "cfguser";

    @InjectMocks
    private AuthService authService;

    // STUB: dependencies required by constructor but not used by verifyToken
    @Mock private HttpServletRequest         httpServletRequest;
    @Mock private UserRepository             userRepository;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private RefreshTokenRepository     refreshTokenRepository;
    @Mock private RedisTokenService          redisTokenService;
    @Mock private EmailVerifyTokenRepository emailVerifyTokenRepository;

    /* ─── PATH-1: N1→N3→N5 — valid, non-expired ─────────────────────────── */

    @Test
    @DisplayName("CFG-PATH-1 | Valid token, not expired → returns SignedJWT (covers B1=F, B2=F)")
    void should_returnSignedJWT_when_tokenIsValidAndNotExpired() throws Exception {
        // Statement coverage: S1, S2, S3 (B1=F), S5, S7
        // Branch: B1-false, B2-false
        String token = buildToken(CORRECT_SECRET, Instant.now().plus(1, ChronoUnit.HOURS), "ACCESS");

        SignedJWT result = authService.verifyToken(token);

        assertThat(result).isNotNull();
        assertThat(result.getJWTClaimsSet().getSubject()).isEqualTo(USER_ID);
    }

    /* ─── PATH-2: N1→N2 — invalid signature ─────────────────────────────── */

    @Test
    @DisplayName("CFG-PATH-2 | Invalid signature → TOKEN_INVALID (covers B1=T) [SEED-001 guard]")
    void should_throwTokenInvalid_when_signatureIsInvalid() {
        // Statement coverage: S1, S2, S3 (B1=T), S4
        // Branch: B1-true
        // SEED-001: if expiry check is inverted, this test still passes (B1 path unchanged)
        String tokenSignedWithWrongKey;
        try {
            tokenSignedWithWrongKey = buildToken(
                    WRONG_SECRET + "12345678901234567890123456789012",
                    Instant.now().plus(1, ChronoUnit.HOURS), "ACCESS");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThatThrownBy(() -> authService.verifyToken(tokenSignedWithWrongKey))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    /* ─── PATH-3: N1→N3→N4 — valid signature but expired ────────────────── */

    @Test
    @DisplayName("CFG-PATH-3 | Expired token → TOKEN_EXPIRED (covers B1=F, B2=T) [SEED-001 guard]")
    void should_throwTokenExpired_when_tokenIsExpired() throws Exception {
        // Statement coverage: S1, S2, S3 (B1=F), S5 (B2=T), S6
        // Branch: B1-false, B2-true
        // SEED-001: if expiry check inverted → this test FAILS (expired token not rejected)
        String expiredToken = buildToken(CORRECT_SECRET, Instant.now().minus(5, ChronoUnit.MINUTES), "ACCESS");

        assertThatThrownBy(() -> authService.verifyToken(expiredToken))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    /* ─── PATH-4: Parse exception propagation ────────────────────────────── */

    @Test
    @DisplayName("CFG-PATH-4 | Malformed token string → ParseException propagated")
    void should_propagateParseException_when_tokenIsMalformed() {
        // Statement coverage: S1, S2 throws ParseException
        String garbage = "this.is.not.a.real.jwt.token";

        assertThatThrownBy(() -> authService.verifyToken(garbage))
                .isInstanceOfAny(ParseException.class, AppException.class);
    }

    /* ─── Additional path: boundary — token expires exactly at now ───────── */

    @Test
    @DisplayName("CFG-PATH-5 | Token expired exactly at boundary (now-1ms) → TOKEN_EXPIRED")
    void should_throwTokenExpired_when_tokenExpiresAtExactBoundary() throws Exception {
        // Edge-case boundary: expiry 1 ms in the past
        String boundaryToken = buildToken(CORRECT_SECRET,
                Instant.now().minusMillis(1), "ACCESS");

        assertThatThrownBy(() -> authService.verifyToken(boundaryToken))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    /* ─── Full Coverage Summary (documented here for JaCoCo cross-reference) ─ */

    /*
     * | Statement     | 7/7   = 100% |
     * | Branch        | 4/4   = 100% |
     * | Condition     | 2/2   = 100% |
     *
     * Covered by: PATH-1 (S1,S2,S3,S5,S7), PATH-2 (S4), PATH-3 (S6), PATH-4 (S2)
     */

    /* ─── Helper ──────────────────────────────────────────────────────────── */
    private String buildToken(String secret, Instant expiry, String tokenType) throws Exception {
        User user = User.builder().userId(USER_ID).userName(USERNAME).build();
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getUserId())
                .expirationTime(Date.from(expiry))
                .issuer("baoxdev.com")
                .issueTime(new Date())
                .claim("tokenType", tokenType)
                .claim("userName", user.getUserName())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        JWSSigner signer = new MACSigner(secret.getBytes());
        jwt.sign(signer);
        return jwt.serialize();
    }
}
