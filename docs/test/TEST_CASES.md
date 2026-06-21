# Test Case Specification
## Social Network Microservice — Identity & Post Services

---

## PART A — USE-CASE BASED TEST CASES

### UC-1: User Login (`AuthService.authenticateUser`)

**Main Flow:**
1. Client sends `POST /auth/login` with `{userName, password}`.
2. System looks up the user by username.
3. System verifies the password with BCrypt.
4. System generates access token (1 h) and refresh token (1 d).
5. System persists the refresh token.
6. System returns `AuthResponse {authenticated, accessToken, refreshToken}`.

**Alternative / Exception Flows:**
- AF-1: Username does not exist → `USER_NOT_EXIST (1001)`
- AF-2: Password does not match → `AUTHENTICATED_FAILED (1002)`
- AF-3: Request body missing required field → validation error

| TC-ID | Use Case | Flow Type | Preconditions | Input | Steps | Expected Result | Actual Result | Pass/Fail |
|-------|----------|-----------|---------------|-------|-------|-----------------|---------------|-----------|
| TC-UC1-P1 | User Login | Main (Positive) | User "alice" exists, password "secret123" encoded in DB | `{userName:"alice", password:"secret123"}` | POST /auth/login | HTTP 200, `authenticated=true`, non-null `accessToken`, non-null `refreshToken` | TBD | — |
| TC-UC1-N1 | User Login | AF-1 (Negative) | No user with username "ghost" | `{userName:"ghost", password:"any"}` | POST /auth/login | HTTP 404, code 1001 | TBD | — |
| TC-UC1-N2 | User Login | AF-2 (Negative) | User "alice" exists | `{userName:"alice", password:"WRONG"}` | POST /auth/login | HTTP 404, code 1002 | TBD | — |
| TC-UC1-N3 | User Login | AF-3 (Negative) | n/a | `{userName:"", password:""}` | POST /auth/login | HTTP 400, validation error | TBD | — |

---

### UC-2: User Registration (`UserService.createUser`)

**Main Flow:**
1. Client sends `POST /user/create` with full `UserCreationRequest`.
2. System checks username and email uniqueness.
3. System saves user (INACTIVE status).
4. System generates email-verify token and persists it.
5. System calls Notification service to send verification email.
6. System calls Profile service to create profile.
7. System returns `UserResponse`.

**Alternative / Exception Flows:**
- AF-1: Username already taken → `USER_EXISTED (1000)`
- AF-2: Email already taken → `USER_EXISTED (1000)`
- AF-3: `userName` shorter than 4 chars → validation error `USERNAME_INVALID`
- AF-4: `password` shorter than 6 chars → validation error `INVALID_PASSWORD`

| TC-ID | Use Case | Flow Type | Preconditions | Input | Steps | Expected Result | Actual Result | Pass/Fail |
|-------|----------|-----------|---------------|-------|-------|-----------------|---------------|-----------|
| TC-UC2-P1 | Registration | Main (Positive) | No existing user | Valid `UserCreationRequest` | POST /user/create | HTTP 200, returns `UserResponse` with userId | TBD | — |
| TC-UC2-N1 | Registration | AF-1 (Negative) | User "bob" exists | `{userName:"bob", ...}` | POST /user/create | HTTP 400, code 1000 | TBD | — |
| TC-UC2-N2 | Registration | AF-2 (Negative) | Email "bob@x.com" exists | `{email:"bob@x.com", ...}` | POST /user/create | HTTP 400, code 1000 | TBD | — |
| TC-UC2-N3 | Registration | AF-3 (Negative) | n/a | `{userName:"ab", password:"123456", email:"x@y.com"}` | POST /user/create | HTTP 400, validation error USERNAME_INVALID | TBD | — |
| TC-UC2-N4 | Registration | AF-4 (Negative) | n/a | `{userName:"validU", password:"12345", email:"x@y.com"}` | POST /user/create | HTTP 400, validation error INVALID_PASSWORD | TBD | — |

---

### UC-3: Create Post (`PostService.CreateNewPost`)

**Main Flow:**
1. Client sends `POST /create` with `PostRequest` + `MultipartFile`.
2. Service validates user via identity service HTTP call.
3. Service fetches profile via profile service HTTP call.
4. Service uploads media to Cloudflare R2.
5. Service persists `Post` entity.
6. Service updates Redis cache with new post.
7. Service returns `PostResponse`.

**Alternative / Exception Flows:**
- AF-1: User does not exist in identity service → `UserNotFoundException`
- AF-2: Redis unavailable → warns, skips cache, still returns response

| TC-ID | Use Case | Flow Type | Preconditions | Input | Steps | Expected Result | Actual Result | Pass/Fail |
|-------|----------|-----------|---------------|-------|-------|-----------------|---------------|-----------|
| TC-UC3-P1 | Create Post | Main (Positive) | User exists, Redis up, R2 accessible (all mocked) | Valid `PostRequest` + mock `MultipartFile` | POST /create | Returns `PostResponse` with id, urlMedia | TBD | — |
| TC-UC3-N1 | Create Post | AF-1 (Negative) | Identity service returns code ≠ 1000 | `{userId:"unknown"}` | POST /create | Throws `UserNotFoundException` | TBD | — |
| TC-UC3-N2 | Create Post | AF-2 (Negative) | Redis unavailable, user exists | Valid request | POST /create | Returns `PostResponse`, logs warning | TBD | — |

---

### UC-4: Like Post (`PostService.LikePost`)

**Main Flow:**
1. Client sends `PUT /like/{post-id}` with Authorization header.
2. Service extracts userId from JWT.
3. Service validates user exists.
4. Service checks if already liked (idempotent).
5. Service saves `PostLike` entity.
6. Service increments `post.liked` counter and saves.
7. Service updates cache entry.

**Alternative / Exception Flows:**
- AF-1: Already liked → returns without action (idempotent)
- AF-2: Post does not exist → no action
- AF-3: User does not exist → `UserNotFoundException`
- AF-4: Malformed / missing Authorization header → `RuntimeException`

| TC-ID | Use Case | Flow Type | Preconditions | Input | Steps | Expected Result | Actual Result | Pass/Fail |
|-------|----------|-----------|---------------|-------|-------|-----------------|---------------|-----------|
| TC-UC4-P1 | Like Post | Main (Positive) | Post exists, user valid, not yet liked | `postId="p1"`, valid JWT header | PUT /like/p1 | `PostLike` saved, `liked` counter +1 | TBD | — |
| TC-UC4-N1 | Like Post | AF-1 (Negative) | Post already liked by user | `postId="p1"`, same user JWT | PUT /like/p1 | No duplicate `PostLike`, counter unchanged | TBD | — |
| TC-UC4-N2 | Like Post | AF-3 (Negative) | User not in identity service | Valid JWT with unknown userId | PUT /like/p1 | Throws `UserNotFoundException` | TBD | — |
| TC-UC4-N3 | Like Post | AF-4 (Negative) | n/a | Missing / blank Authorization | PUT /like/p1 | Throws `RuntimeException("Missing Authorization header")` | TBD | — |

---

## PART B — BLACK-BOX TECHNIQUES

---

### B1. DECISION TABLE — `AuthService.authenticateUser`

**Why:** The method has exactly 3 boolean conditions with discrete actions per combination — a textbook Decision Table target.

**Conditions:**
- C1: Username exists in `UserRepository`
- C2: Password matches BCrypt hash
- C3: *(Future)* EmailVerified = true *(currently commented out; tested as always-true)*

**Actions:**
- A1: Generate tokens + return `AuthResponse`
- A2: Throw `AppException(USER_NOT_EXIST)`
- A3: Throw `AppException(AUTHENTICATED_FAILED)`

```
                  Col-1  Col-2  Col-3  Col-4
C1 (user exists)   F      T      T      T
C2 (pwd matches)   —      F      T      T
C3 (email verify)  —      —      F      T

A1 (tokens)                             X
A2 (USER_NOT_EXIST)  X
A3 (AUTH_FAILED)            X
(future: EMAIL_NOT_VERIFIED)       X
```

| Col | Conditions (C1/C2/C3) | Expected Action | TC-ID |
|-----|-----------------------|-----------------|-------|
| 1 | F / — / — | A2: USER_NOT_EXIST (1001) | DT-AUTH-001 |
| 2 | T / F / — | A3: AUTHENTICATED_FAILED (1002) | DT-AUTH-002 |
| 3 | T / T / F | *(commented out; treated as pass-through)* | DT-AUTH-003 |
| 4 | T / T / T | A1: Return AuthResponse | DT-AUTH-004 |

> **Test class:** `AuthLoginDecisionTableTest` — each column maps to one `@Test` method.

---

### B2. EQUIVALENCE CLASS PARTITIONING — `UserCreationRequest`

**Why:** `userName` and `password` each have only two meaningful zones (valid / invalid) defined by annotations `@Size`. ECP reduces N × M exhaustive tests to a handful of representatives.

#### Field: `userName` (`@Size(min = 4)`)

| Class | Description | Representative | Valid? |
|-------|-------------|----------------|--------|
| VEC-UN-1 | length ≥ 4 | `"alice"` (5 chars) | Yes |
| IEC-UN-1 | length < 4 | `"ab"` (2 chars) | No |
| IEC-UN-2 | null | `null` | No |
| IEC-UN-3 | blank string | `""` | No |

#### Field: `password` (`@Size(min = 6)`)

| Class | Description | Representative | Valid? |
|-------|-------------|----------------|--------|
| VEC-PW-1 | length ≥ 6 | `"secure1"` (7 chars) | Yes |
| IEC-PW-1 | length < 6 | `"abc"` (3 chars) | No |
| IEC-PW-2 | null | `null` | No |

#### Field: `email`

| Class | Description | Representative | Valid? |
|-------|-------------|----------------|--------|
| VEC-EM-1 | valid RFC email | `"user@test.com"` | Yes |
| IEC-EM-1 | no @ symbol | `"usertest.com"` | No |
| IEC-EM-2 | null | `null` | No |

**ECP reduces test count:**
- Exhaustive: 4 × 3 × 3 = 36 combinations
- ECP representative: 3 (one per VEC) + 7 (one per IEC) = **10 test cases**

> **Test class:** `UserCreationECPTest`

---

### B3. BOUNDARY VALUE ANALYSIS — `userName` and `password` length

**Why:** `@Size(min=4)` and `@Size(min=6)` define sharp numeric boundaries; BVA detects off-by-one defects that ECP misses.

#### `userName` (min = 4, practical max = 255)

| Boundary Point | Value | Length | Valid? | TC-ID |
|---------------|-------|--------|--------|-------|
| min−1 | `"abc"` | 3 | No — below minimum | BVA-UN-001 |
| min | `"abcd"` | 4 | Yes — exact minimum | BVA-UN-002 |
| min+1 | `"abcde"` | 5 | Yes | BVA-UN-003 |
| nominal | `"alice123"` | 8 | Yes | BVA-UN-004 |
| max−1 | 254-char string | 254 | Yes | BVA-UN-005 |
| max | 255-char string | 255 | Yes | BVA-UN-006 |
| max+1 | 256-char string | 256 | No — DB column overflow | BVA-UN-007 |

#### `password` (min = 6)

| Boundary Point | Value | Length | Valid? | TC-ID |
|---------------|-------|--------|--------|-------|
| min−1 | `"abcde"` | 5 | No | BVA-PW-001 |
| min | `"abcdef"` | 6 | Yes | BVA-PW-002 |
| min+1 | `"abcdefg"` | 7 | Yes | BVA-PW-003 |
| nominal | `"myPass99"` | 8 | Yes | BVA-PW-004 |

> **Test class:** `UserCreationBVATest`

---

### B4. STATE TRANSITION TESTING — User Account Lifecycle

**Why:** `User.userStatus` is driven by a sequence of events. Sneak paths (e.g., activating an already-deleted account) must be blocked.

#### State Transition Diagram

```
         [register]           [verifyEmail]          [deleteUser]
INITIAL ─────────────► INACTIVE ─────────────► ACTIVE ─────────────► DELETE
                           │                      │
                           │ [deleteUser]          │ [verifyEmail on active]
                           ▼                      ▼
                         DELETE            (no-op / guard needed)

SNEAK PATHS (invalid transitions to test):
  DELETE  ─► ACTIVE   (re-activating a deleted user)
  INACTIVE ─► DELETE  (direct delete without verify, currently allowed — see BUG-001)
```

#### State Transition Table

| State From | Event | State To | Valid? | TC-ID |
|------------|-------|----------|--------|-------|
| — | `createUser()` | INACTIVE | Yes | ST-USER-001 |
| INACTIVE | `verifyEmail()` | ACTIVE | Yes | ST-USER-002 |
| ACTIVE | `deleteUser()` | DELETE | Yes | ST-USER-003 |
| INACTIVE | `deleteUser()` | DELETE | Yes (allowed by code) | ST-USER-004 |
| DELETE | `verifyEmail()` | DELETE (no change) | Sneak path — should reject | ST-USER-005 |
| ACTIVE | `createUser()` (same userName) | ACTIVE (unchanged) | No — USER_EXISTED | ST-USER-006 |

> **Test class:** `UserAccountStateTransitionTest`

---

### B5. CAUSE-EFFECT GRAPH — `PostService.LikePost`

**Why:** `LikePost` has complex interdependencies between multiple boolean inputs — ideal for a Cause-Effect graph that maps all logical paths.

#### Causes

| ID | Cause |
|----|-------|
| C1 | User exists in identity service |
| C2 | Post exists in `PostRepo` |
| C3 | Like record already exists (`PostLikeRepo.existsByPostIdAndUserId`) |
| C4 | Redis connection is available |

#### Effects

| ID | Effect |
|----|--------|
| E1 | `UserNotFoundException` thrown |
| E2 | Method returns without action (idempotent) |
| E3 | `PostLike` entity persisted |
| E4 | `post.liked` incremented and saved |
| E5 | Cache entry updated in Redis |

#### Cause-Effect Graph (logical connectors)

```
C1 ──NOT──► [UserNotExist] ──────────────────────────────────────────► E1
C1 ──AND── C2 ──AND── NOT(C3) ──────────────────────────────────────► E3
C1 ──AND── C2 ──AND── NOT(C3) ──────────────────────────────────────► E4
C1 ──AND── C2 ──AND── NOT(C3) ──AND── C4 ───────────────────────────► E5
C1 ──AND── C2 ──AND── C3 ───────────────────────────────────────────► E2
C1 ──AND── NOT(C2) ─────────────────────────────────────────────────► (silent return)
```

#### Derived Decision Table

| Col | C1 | C2 | C3 | C4 | E1 | E2 | E3 | E4 | E5 | TC-ID |
|-----|----|----|----|----|----|----|----|----|-----|-------|
| 1 | F | — | — | — | X | | | | | CE-LIKE-001 |
| 2 | T | F | — | — | | | | | | CE-LIKE-002 |
| 3 | T | T | T | — | | X | | | | CE-LIKE-003 |
| 4 | T | T | F | F | | | X | X | | CE-LIKE-004 |
| 5 | T | T | F | T | | | X | X | X | CE-LIKE-005 |

> **Test class:** `CreatePostCauseEffectTest` (covers `LikePost` cause-effect columns)

---

## PART C — WHITE-BOX TEST CASES

### C1. Code Coverage Analysis

#### Function 1: `AuthService.verifyToken(String token)`

```java
protected SignedJWT verifyToken(String token) throws JOSEException, ParseException {
    JWSVerifier jwsVerifier = new MACVerifier(secretKey.getBytes()); // S1
    SignedJWT signedJWT = SignedJWT.parse(token);                    // S2
    var verified = signedJWT.verify(jwsVerifier);                    // S3
    if (!verified) {                                                 // B1
        throw new AppException(ErrorCode.TOKEN_INVALID);             // S4 (branch B1=T)
    }
    Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime(); // S5
    if (expiryTime.before(new Date(...))) {                           // B2
        throw new AppException(ErrorCode.TOKEN_EXPIRED);              // S6 (branch B2=T)
    }
    return signedJWT;                                                // S7 (branch B2=F)
}
```

| Metric | Total | Covered by Tests | % |
|--------|-------|-----------------|---|
| Statements | 7 | 7 | 100 % |
| Branches (B1-T, B1-F, B2-T, B2-F) | 4 | 4 | 100 % |
| Conditions (verified, expiryTime.before) | 2 | 2 | 100 % |

**Test paths:**
- Path α: S1→S2→S3→B1(F)→S5→B2(F)→S7 — valid token, not expired *(CFG-AUTH-PATH-1)*
- Path β: S1→S2→S3→B1(T)→S4 — signature invalid *(CFG-AUTH-PATH-2)*
- Path γ: S1→S2→S3→B1(F)→S5→B2(T)→S6 — token expired *(CFG-AUTH-PATH-3)*

> **Test class:** `AuthVerifyTokenCFGTest`

---

#### Function 2: `PostService.extractUserIdFromAuthorizationHeader(String header)`

```java
// S1: null/blank check
if (authorizationHeader == null || authorizationHeader.isBlank()) {   // B1
    throw new RuntimeException("Missing Authorization header");         // S2
}
// S3-S4: strip "Bearer "
String token = header.startsWith("Bearer ") ? header.substring(7) : header.trim(); // B2
try {
    String[] parts = token.split("\\.");                               // S5
    if (parts.length < 2) {                                           // B3
        throw new RuntimeException("Invalid JWT format");              // S6
    }
    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1])); // S7
    Map<String,Object> payload = mapper.readValue(payloadJson, Map.class);    // S8
    Object sub = payload.get("sub");                                   // S9
    if (sub == null || sub.toString().isBlank()) {                    // B4
        throw new RuntimeException("Token does not contain user id");  // S10
    }
    return sub.toString();                                             // S11
} catch (IllegalArgumentException | IOException e) {                  // EX
    throw new RuntimeException("Invalid Authorization token", e);      // S12
}
```

| Metric | Total | Covered | % |
|--------|-------|---------|---|
| Statements | 12 | 12 | 100 % |
| Branches (B1,B2,B3,B4,EX) | 10 | 10 | 100 % |
| Conditions (null, isBlank, startsWith, length<2, sub==null, isBlank) | 6 | 6 | 100 % |

> **Test class:** `ExtractUserIdCFGTest`

---

### C2. McCabe Cyclomatic Complexity

See dedicated CFG documents:
- `docs/test/CFG_AuthService_verifyToken.md`
- `docs/test/CFG_PostService_extractUserId.md`
