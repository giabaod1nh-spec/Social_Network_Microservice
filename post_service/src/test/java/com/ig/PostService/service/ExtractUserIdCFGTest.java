package com.ig.PostService.service;

import com.ig.PostService.config.R2Config;
import com.ig.PostService.mapper.Mapper;
import com.ig.PostService.repo.CommentRepo;
import com.ig.PostService.repo.PostLikeRepo;
import com.ig.PostService.repo.PostRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C2 — McCABE CYCLOMATIC COMPLEXITY / WHITE-BOX for
 *      PostService.extractUserIdFromAuthorizationHeader(String)
 *
 * CFG analysis (see docs/test/CFG_PostService_extractUserId.md):
 *   V(G) = E − N + 2P = 15 − 11 + 2 = 6
 *   → 6 independent test paths required
 *
 * PATH-1: N1→N2       — null/blank header → RuntimeException "Missing Authorization header"
 * PATH-2: N1→N3→N4→N6→N8→N10 — valid Bearer JWT with sub present → returns userId
 * PATH-3: N1→N3→N5→N6→N8→N10 — raw token (no Bearer) with sub present → returns userId
 * PATH-4: N1→N3→N4→N6→N7     — token has < 2 parts → RuntimeException "Invalid JWT format"
 * PATH-5: N1→N3→N4→N6→N8→N9  — sub claim null/blank → RuntimeException "Token does not contain user id"
 * PATH-6: N1→N3→N4→N6→N8→N11 — bad base64 payload → RuntimeException "Invalid Authorization token"
 *
 * SEED-005 detection:
 *   If tokenParts[0] is used instead of tokenParts[1], the JWT header is decoded.
 *   The header is {"alg":"HS512"} which has no "sub" field → PATH-2 would throw
 *   "Token does not contain user id" instead of returning userId.
 *
 * Statement coverage:  12/12 = 100%
 * Branch coverage:     10/10 = 100%
 * Condition coverage:   6/6  = 100%
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[C2-CFG] PostService.extractUserIdFromAuthorizationHeader — McCabe Paths")
class ExtractUserIdCFGTest {

    @Spy
    @InjectMocks
    private PostService postService;

    // STUB: required by @InjectMocks but not used by extractUserIdFromAuthorizationHeader
    @Mock private PostRepo            postRepo;
    @Mock private PostLikeRepo        postLikeRepo;
    @Mock private CommentRepo         commentRepo;
    @Mock private Mapper              mapper;
    @Mock private CacheManager        cacheManager;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private S3Client            r2Client;
    @Mock private R2Config            r2Config;

    private static final String USER_ID = "cfg-post-user-001";

    /**
     * Builds a valid Bearer JWT header with the given userId as the "sub" claim.
     * The token is NOT cryptographically signed (signature is a dummy value) because
     * extractUserIdFromAuthorizationHeader only base64-decodes — it does NOT verify signatures.
     */
    private String buildBearerToken(String userId) {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS512\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + userId + "\",\"userName\":\"testuser\"}").getBytes());
        return "Bearer " + header + "." + payload + ".dummy-signature";
    }

    private String buildRawToken(String userId) {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS512\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + userId + "\"}").getBytes());
        return header + "." + payload + ".dummy-sig";
    }

    private String invokeExtractUserId(String header) {
        return (String) ReflectionTestUtils.invokeMethod(
                postService, "extractUserIdFromAuthorizationHeader", header);
    }

    /* ─── PATH-1: null or blank header ─────────────────────────────────── */

    @ParameterizedTest(name = "CFG-PATH-1 | header=''{0}''")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("CFG-PATH-1 | D1=T — null/blank header → RuntimeException (covers N1→N2)")
    void should_throw_when_authorizationHeaderIsNullOrBlank(String header) {
        assertThatThrownBy(() -> invokeExtractUserId(header))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    /* ─── PATH-2: valid Bearer JWT with sub ────────────────────────────── */

    @Test
    @DisplayName("CFG-PATH-2 | D1=F, D2=T, D3=F, D4=F → returns userId (covers N1→N3→N4→N6→N8→N10)")
    void should_returnUserId_when_validBearerTokenWithSubClaim() {
        // SEED-005 guard: if tokenParts[0] used instead of [1], this test fails
        String bearerToken = buildBearerToken(USER_ID);

        String result = invokeExtractUserId(bearerToken);

        assertThat(result).isEqualTo(USER_ID);
        // SEED-005: if wrong index → result would be null or different (JWT header has no "sub")
    }

    /* ─── PATH-3: raw token (no "Bearer " prefix) ───────────────────────── */

    @Test
    @DisplayName("CFG-PATH-3 | D2=F (no Bearer prefix) — raw token with sub → returns userId (N3→N5→N6→N8→N10)")
    void should_returnUserId_when_tokenWithoutBearerPrefix() {
        String rawToken = buildRawToken(USER_ID);

        String result = invokeExtractUserId(rawToken);

        assertThat(result).isEqualTo(USER_ID);
    }

    /* ─── PATH-4: JWT with fewer than 2 parts ───────────────────────────── */

    @Test
    @DisplayName("CFG-PATH-4 | D3=T — token has < 2 parts → RuntimeException (N6→N7)")
    void should_throwInvalidJwtFormat_when_jwtHasFewerThanTwoParts() {
        // Only one segment — no "." separator
        String onePartToken = "Bearer " + Base64.getUrlEncoder()
                .withoutPadding().encodeToString("onlyone".getBytes());

        assertThatThrownBy(() -> invokeExtractUserId(onePartToken))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid JWT format");
    }

    @Test
    @DisplayName("CFG-PATH-4b | D3=T — completely malformed token → Invalid JWT format")
    void should_throwInvalidJwtFormat_when_tokenHasNoDots() {
        String malformed = "Bearer nodotsatall";

        assertThatThrownBy(() -> invokeExtractUserId(malformed))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid JWT format");
    }

    /* ─── PATH-5: sub claim is null or blank ───────────────────────────── */

    @Test
    @DisplayName("CFG-PATH-5 | D4=T — sub claim missing → RuntimeException (N8→N9)")
    void should_throwMissingSubClaim_when_subClaimIsAbsent() {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS512\"}".getBytes());
        // Payload without "sub" claim
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"userName\":\"testuser\",\"exp\":9999999999}".getBytes());
        String tokenWithoutSub = "Bearer " + header + "." + payload + ".sig";

        assertThatThrownBy(() -> invokeExtractUserId(tokenWithoutSub))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token does not contain user id");
    }

    @Test
    @DisplayName("CFG-PATH-5b | D4=T — sub claim is blank string → RuntimeException (N8→N9)")
    void should_throwMissingSubClaim_when_subClaimIsBlank() {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS512\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"\"}".getBytes());
        String tokenWithBlankSub = "Bearer " + header + "." + payload + ".sig";

        assertThatThrownBy(() -> invokeExtractUserId(tokenWithBlankSub))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Token does not contain user id");
    }

    /* ─── PATH-6: invalid base64 payload (catch block) ─────────────────── */

    @Test
    @DisplayName("CFG-PATH-6 | EX — base64 decode fails → RuntimeException (N8→N11)")
    void should_throwInvalidToken_when_base64PayloadIsInvalid() {
        // Use a payload part that is not valid base64url
        String invalidBase64Payload = "NOT!VALID!BASE64!=====";
        String tokenWithBadPayload = "Bearer validheader." + invalidBase64Payload + ".sig";

        assertThatThrownBy(() -> invokeExtractUserId(tokenWithBadPayload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid Authorization token");
    }

    /* ─── SEED-005 explicit detection test ──────────────────────────────── */

    @Test
    @DisplayName("SEED-005 guard | Wrong index (tokenParts[0]) would decode header, not payload")
    void should_notReturnNullUserId_when_correctPayloadIndexIsUsed() {
        // If SEED-005 is present: tokenParts[0] is the JWT header = {"alg":"HS512"}
        // The header has no "sub" field → result would throw "Token does not contain user id"
        // This test PASSES with correct code, FAILS with SEED-005 active.
        String bearerToken = buildBearerToken(USER_ID);

        String result = invokeExtractUserId(bearerToken);

        // Must be the userId from payload, not null/error from header
        assertThat(result)
                .isNotNull()
                .isNotBlank()
                .isEqualTo(USER_ID);
    }

    /* ─── Full coverage summary ─────────────────────────────────────────── */

    /*
     * CFG-PATH-1  → covers N1→N2 (D1=T both sides of OR)
     * CFG-PATH-2  → covers N1→N3→N4→N6→N8→N10 (D1=F, D2=T, D3=F, D4=F)
     * CFG-PATH-3  → covers N1→N3→N5→N6→N8→N10 (D1=F, D2=F, D3=F, D4=F)
     * CFG-PATH-4  → covers N1→N3→N4→N6→N7     (D1=F, D2=T, D3=T)
     * CFG-PATH-5  → covers N1→N3→N4→N6→N8→N9  (D1=F, D2=T, D3=F, D4=T)
     * CFG-PATH-6  → covers N1→N3→N4→N6→N8→N11 (IOException/IllegalArgEx catch)
     *
     * Statement:  12/12 = 100%
     * Branch:     10/10 = 100%
     * Condition:   6/6  = 100%
     */
}
