# Requirements Traceability Matrix (RTM)
## Social Network Microservice — Identity & Post Services

**Project:** Social_Network_Microservice  
**Services:** `identity_service` · `post_service`
**Version:** 1.0

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ Pass | Test verified correct against production code |
| ❌ Fail | Test expected to fail — known bug present |
| ⏳ Pending | Test written; not yet executed in CI |
| ⚠ NOT COVERED | Requirement has zero test cases |
| ⚠ ORPHAN TC | Test case maps to no requirement |

**Test Class Abbreviations:**

| Abbrev | Class | Layer |
|--------|-------|-------|
| **ACT** | `AuthControllerTest` *(existing)* | Controller |
| **UCT** | `UserControllerTest` *(existing)* | Controller |
| **AST** | `AuthServiceTest` *(existing — 46 tests)* | Service |
| **UST** | `UserServiceTest` *(existing — 23 tests)* | Service |
| **RTS** | `RedisTokenServiceTest` *(existing)* | Service |
| **DT** | `AuthLoginDecisionTableTest` *(new)* | Controller |
| **ECP** | `UserCreationECPTest` *(new)* | Service |
| **BVA** | `UserCreationBVATest` *(new)* | Controller |
| **ST-U** | `UserAccountStateTransitionTest` *(new)* | Service |
| **CE-A** | `AuthTokenCauseEffectTest` *(new)* | Service |
| **CFG-A** | `AuthVerifyTokenCFGTest` *(new)* | Service |
| **P-DT** | `PostControllerDTTest` *(new)* | Controller |
| **P-ECP** | `PostLikeECPTest` *(new)* | Service |
| **P-BVA** | `PostLikeBVATest` *(new)* | Service |
| **ST-P** | `PostLifecycleStateTransitionTest` *(new)* | Service |
| **CE-P** | `CreatePostCauseEffectTest` *(new)* | Service |
| **CFG-P** | `ExtractUserIdCFGTest` *(new)* | Service |

---

## PART 1 — FORWARD TRACEABILITY (Requirement → Test Case)

> Proves every requirement has at least one test case covering it.

---

### ─── IDENTITY SERVICE ──────────────────────────────────────────────────────

---

#### UC-01: User Login

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-01-P | `POST /auth/login` with valid credentials returns `AuthResponse` with `authenticated=true`, non-null access + refresh tokens | `TC-UC1-P1`, `DT-AUTH-004` | UC-Based, DT | High | ⏳ Pending |
| UC-01-AF1 | Username does not exist in DB → `USER_NOT_EXIST (1001)`, HTTP 404 | `TC-UC1-N1`, `DT-AUTH-001`, `AST` (DT col-1) | UC-Based, DT | High | ⏳ Pending |
| UC-01-AF2 | Password does not match BCrypt hash → `AUTHENTICATED_FAILED (1002)`, HTTP 404 | `TC-UC1-N2`, `DT-AUTH-002`, `AST` (DT col-2) | UC-Based, DT | High | ⏳ Pending |
| UC-01-AF3 | Blank/empty `userName` or `password` → validation error `MUST_NOT_BLANK (1017)`, HTTP 400 | `TC-UC1-N3`, `DT-AUTH-005`, `ACT.login_userNameInvalid`, `ACT.login_passwordInvalid` | UC-Based, DT | Medium | ⏳ Pending |

---

#### UC-02: User Registration

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-02-P | `POST /user/create` with unique userName + email → user saved (INACTIVE), email token sent, profile created, `UserResponse` returned | `TC-UC2-P1`, `ECP-CREATE-P1`, `ST-USER-001`, `UST` (createUser success) | UC-Based, ECP, ST | High | ⏳ Pending |
| UC-02-AF1 | `userName` already exists → `USER_EXISTED (1000)`, HTTP 400 | `TC-UC2-N1`, `ECP-CREATE-N1`, `ST-USER-006`, `UST` | UC-Based, ECP, ST | High | ⏳ Pending |
| UC-02-AF2 | `email` already exists → `USER_EXISTED (1000)`, HTTP 400 *(SEED-002 guard)* | `TC-UC2-N2`, `ECP-CREATE-N2`, `UST` | UC-Based, ECP | High | ⏳ Pending |
| UC-02-AF3 | `userName` length < 4 → `USERNAME_INVALID`, HTTP 400 | `TC-UC2-N3`, `BVA-UN-001`, `BVA-PW-NEG` | UC-Based, BVA | Medium | ⏳ Pending |
| UC-02-AF4 | `password` length < 6 → `INVALID_PASSWORD`, HTTP 400 | `TC-UC2-N4`, `BVA-PW-001` | UC-Based, BVA | Medium | ⏳ Pending |
| UC-02-AF5 | Notification service unavailable → exception propagated, no partial state | `ECP-CREATE-N3` | ECP | Medium | ⏳ Pending |
| UC-02-AF6 | Profile service unavailable → exception propagated | `ECP-CREATE-N4` | ECP | Medium | ⏳ Pending |

---

#### UC-03: Token Refresh

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-03-P | `POST /auth/refresh` with valid REFRESH token → old token revoked, new access + refresh tokens returned *(SEED-004 guard)* | `TC-UC4-P1 (refresh)`, `CE-REFRESH-005`, `DT-AUTH-006`, `AST` (refresh DT col-5) | UC-Based, CE, DT | High | ⏳ Pending |
| UC-03-AF1 | Invalid JWT signature → `TOKEN_INVALID (1007)` | `CE-REFRESH-001`, `AST` | CE, DT | High | ⏳ Pending |
| UC-03-AF2 | Token type is ACCESS not REFRESH → `TOKEN_TYPE_INVALID (1009)` | `CE-REFRESH-002`, `AST` | CE | High | ⏳ Pending |
| UC-03-AF3 | Token not found in DB (revoked) → `TOKEN_NOT_FOUND (1010)` | `CE-REFRESH-003`, `AST` | CE, DT | High | ⏳ Pending |
| UC-03-AF4 | User deleted between token issuance and refresh → `USER_NOT_EXIST (1001)` | `CE-REFRESH-004`, `AST` | CE | Medium | ⏳ Pending |

---

#### UC-04: Logout

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-04-P | `POST /auth/logout` → refresh token deleted from DB, valid access token blacklisted in Redis | `CE-LOGOUT-002`, `DT-AUTH-007`, `AST` (logOut CE) | UC-Based, CE | High | ⏳ Pending |
| UC-04-AF1 | No `Authorization` header → only refresh token deleted, no Redis blacklist | `CE-LOGOUT-001`, `AST` | CE | Medium | ⏳ Pending |
| UC-04-AF2 | Access token already expired (TTL ≤ 0) → `TOKEN_EXPIRED` thrown; refresh still deleted | `CE-LOGOUT-003`, `AST` | CE, BVA | Medium | ⏳ Pending |

---

#### UC-05: Email Verification

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-05-P | `POST /auth/verify_email?token=<tok>` → user status → ACTIVE, `emailVerified=true`, email token deleted | `ST-USER-002 (ref)`, `AST` (verifyEmail ST, ECP) | UC-Based, ST, ECP | High | ⏳ Pending |
| UC-05-AF1 | Invalid / expired email token → `VERIFY_EMAIL_TOKEN_INVALID (1012)` | `AST` (verifyEmail invalid token) | ECP | High | ⏳ Pending |
| UC-05-AF2 | Email token already used (deleted after first use) → token not found | `AST` | ECP | Medium | ⏳ Pending |

---

#### UC-06: Get User by ID

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-06-P | `GET /user/getById?userId=<id>` → returns `UserResponse` | `ST-USER-GET-P`, `UST` | UC-Based, ST | Medium | ⏳ Pending |
| UC-06-AF1 | User ID not found → `USER_NOT_EXIST (1001)` | `ST-USER-GET`, `UST` | UC-Based, ST | Medium | ⏳ Pending |
| UC-06-AF2 | Blank `userId` param → `USERID_NOT_FOUND (1016)`, HTTP 400 | `UCT` (getById blank) | UC-Based | Medium | ⏳ Pending |

---

#### UC-07: Delete User

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-07-P | `DELETE /user/delete?userId=<id>` → `UserStatus.DELETE` persisted in DB *(BUG-001: `save()` missing → currently NOT persisted)* | `ST-USER-003`, `ST-USER-004`, `UST` | UC-Based, ST | High | ❌ Fail (BUG-001) |
| UC-07-AF1 | User not found → `USER_NOT_EXIST (1001)` | `ST-USER-005`, `UST` | UC-Based, ST | Medium | ⏳ Pending |

---

#### UC-08: Token Introspection

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-08-P | `POST /auth/introspect` with valid token → `{isValid: true}` | `ACT.introspectToken_success`, `AST` (introspect ECP) | UC-Based, ECP | Medium | ⏳ Pending |
| UC-08-AF1 | Invalid / expired token → `TOKEN_INVALID (1007)` or `{isValid: false}` | `AST` (introspect invalid), `CFG-A.CFG-PATH-2`, `CFG-A.CFG-PATH-3` | ECP, CFG | Medium | ⏳ Pending |

---

### ─── POST SERVICE ───────────────────────────────────────────────────────────

---

#### UC-09: Create Post

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-09-P | `POST /create` (multipart) with valid user → media uploaded to R2, post saved, cache updated, `PostResponse` returned | `TC-UC3-P1`, `DT-POST-001` | UC-Based, DT | High | ⏳ Pending |
| UC-09-AF1 | Identity service returns non-1000 code (user not found) → `UserNotFoundException` | `TC-UC3-N1`, `DT-POST-002` | UC-Based, DT | High | ⏳ Pending |
| UC-09-AF2 | Redis unavailable → cache skipped, `PostResponse` still returned with warning log | `TC-UC3-N2` | UC-Based | Medium | ⏳ Pending |

---

#### UC-10: Get Posts in User Profile

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-10-P-EMPTY | `GET /profile/{user-id}` with user who has no posts → returns `UserPostProfileResponse` with empty list | `DT-PROFILE-002`, `ST-POST-007` | UC-Based, DT, ST | Medium | ⏳ Pending |
| UC-10-P-POSTS | `GET /profile/{user-id}` with user who has posts → returns `UserPostProfileResponse` with post list | `DT-PROFILE-003`, `ST-POST-007` | UC-Based, DT, ST | High | ⏳ Pending |
| UC-10-AF1 | User does not exist → `UserNotFoundException` *(BUG-004: `CheckUserExisted` always returns false due to wrong code check)* | `DT-PROFILE-001`, `ST-POST-008` | UC-Based, DT, ST | High | ❌ Fail (BUG-004) |

---

#### UC-11: Delete Post

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-11-P | `DELETE /delete/{user-id}/{post-id}` → post removed from DB and from Redis cache *(BUG-002: cache not updated)* | `DT-DELETE-001`, `ST-POST-004`, `ST-POST-006` | UC-Based, DT, ST | High | ❌ Fail (BUG-002) |
| UC-11-AF1 | User does not exist → `UserNotFoundException` | `DT-DELETE-002`, `ST-POST-008` | UC-Based, DT | Medium | ⏳ Pending |

---

#### UC-12: Like Post

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-12-P | `PUT /like/{post-id}` → `PostLike` persisted, `post.liked` +1, cache updated if Redis available *(SEED-003 guard)* | `TC-UC4-P1`, `DT-LIKE-001`, `ST-POST-001`, `CE-LIKE-004`, `CE-LIKE-005`, `BVA-LIKE-001`, `BVA-LIKE-MULTI`, `ECP-LIKE-P2`, `BVA-UNIQUE-001` | UC-Based, DT, ST, CE, BVA, ECP | High | ⏳ Pending |
| UC-12-AF1 | Missing or blank `Authorization` header → `RuntimeException("Missing Authorization header")` | `TC-UC4-N3`, `ECP-LIKE-N1`, `ECP-LIKE-N2`, `CFG-P.CFG-PATH-1` | UC-Based, ECP, CFG | High | ⏳ Pending |
| UC-12-AF2 | Post already liked by same user → idempotent return, no duplicate `PostLike` | `TC-UC4-N1`, `ECP-LIKE-N3`, `CE-LIKE-003`, `BVA-UNIQUE-002` | UC-Based, ECP, CE, BVA | High | ⏳ Pending |
| UC-12-AF3 | Post not found in DB → silent return, no action | `ECP-LIKE-N4`, `CE-LIKE-002`, `ST-POST-005` | ECP, CE, ST | Medium | ⏳ Pending |
| UC-12-AF4 | User not found in identity service → `UserNotFoundException` | `TC-UC4-N2`, `CE-LIKE-001` | UC-Based, CE | High | ⏳ Pending |

---

#### UC-13: Unlike Post

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-13-P | `PUT /unlike/{post-id}` with existing like → `PostLike` deleted, `post.liked` -1 (if >0), cache updated | `DT-UNLIKE-001`, `ST-POST-002`, `ECP-UNLIKE-P1`, `BVA-UNLIKE-002`, `BVA-UNLIKE-MULTI` | UC-Based, DT, ST, ECP, BVA | High | ⏳ Pending |
| UC-13-AF1 | Post not previously liked by user → idempotent return, no delete | `ECP-UNLIKE-N2` | ECP | Medium | ⏳ Pending |
| UC-13-AF2 | `post.liked` is already 0 → counter not decremented below zero | `TC-UC4 (boundary)`, `ECP-UNLIKE-N1`, `BVA-UNLIKE-001`, `BVA-UNLIKE-MULTI` | ECP, BVA | High | ⏳ Pending |

---

#### UC-14: Comment on Post

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-14-P | `PUT /comment/{post-id}` → `Comment` entity saved, cache updated, `ApiResponse` returned | `DT-COMMENT-001`, `ST-POST-003` | UC-Based, DT, ST | Medium | ⏳ Pending |
| UC-14-AF1 | User does not exist → `UserNotFoundException` | `ST-POST-008` | ST | Medium | ⏳ Pending |

---

#### UC-15: Get Feed

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-15-P | `GET /get-post` → returns posts from followed users; marks `likedByUser` per post | ⚠ **NOT COVERED** | — | Medium | ⚠ NOT COVERED |
| UC-15-AF1 | No followed users → fallback to all posts | ⚠ **NOT COVERED** | — | Low | ⚠ NOT COVERED |

---

#### UC-16: Clear Cache

| REQ-ID | Requirement / Use Case Description | TC-ID(s) | Technique | Priority | Status |
|--------|------------------------------------|----------|-----------|----------|--------|
| UC-16-P | `POST /clear-cache` → clears "Post" cache entirely | `DT-CACHE-001` | DT | Low | ⏳ Pending |

---

### ─── NON-FUNCTIONAL REQUIREMENTS ──────────────────────────────────────────

| REQ-ID | Requirement Description | TC-ID(s) | Technique | Priority | Status |
|--------|-----------------------|----------|-----------|----------|--------|
| NFR-01 | **Security** — JWT must be signed with HS512 + shared secret; tampered tokens rejected | `CFG-A.CFG-PATH-1`, `CFG-A.CFG-PATH-2`, `CE-REFRESH-001`, `AST` (DT col-1 refresh) | CFG (White-Box), CE | High | ⏳ Pending |
| NFR-02 | **Security** — Expired tokens must be rejected at every use *(SEED-001 guard)* | `CFG-A.CFG-PATH-3`, `CFG-A.CFG-PATH-5`, `CE-LOGOUT-003`, `AST` (BVA expiry) | CFG, BVA, CE | High | ⏳ Pending |
| NFR-03 | **Security** — Logged-out access tokens blacklisted in Redis for remaining TTL | `CE-LOGOUT-002`, `RTS.blackListToken`, `RTS.isTokenBlackListed` | CE, Unit | High | ⏳ Pending |
| NFR-04 | **Security** — Passwords BCrypt-encoded (strength 10) before persistence; never stored in plain text | `ST-USER-001` (verifies INACTIVE user mapper), `ECP-CREATE-P1` (verifies password field is encoded), `UST` | ST, ECP | High | ⏳ Pending |
| NFR-05 | **Data Integrity** — `userName` and `email` uniqueness enforced at service layer before DB insert | `ECP-CREATE-N1`, `ECP-CREATE-N2`, `ST-USER-006`, `UST` | ECP, ST | High | ⏳ Pending |
| NFR-06 | **Data Integrity** — Like operations idempotent; duplicate `PostLike` records impossible | `ECP-LIKE-N3`, `CE-LIKE-003`, `BVA-UNIQUE-002` | ECP, CE, BVA | High | ⏳ Pending |
| NFR-07 | **Data Integrity** — `post.liked` counter must never go below 0 | `ECP-UNLIKE-N1`, `BVA-UNLIKE-001`, `BVA-UNLIKE-MULTI` | ECP, BVA | High | ⏳ Pending |
| NFR-08 | **Performance** — Redis cache used for post profile queries (TTL = 1 day via `RedisConfig`) | `CE-LIKE-004` (Redis=off path), `CE-LIKE-005` (Redis=on path), `ST-POST-006` | CE, ST | Medium | ⏳ Pending |
| NFR-09 | **Reliability** — HTTP calls to identity/profile services retry up to 3 times on `ConnectException` / `ClosedChannelException` | ⚠ **NOT COVERED** — requires integration test with WireMock | — | Medium | ⚠ NOT COVERED |
| NFR-10 | **Coverage** — 80% branch coverage enforced by JaCoCo on `AuthService` and `UserService` | `CFG-A` (100% stmt/branch/cond on `verifyToken`), `CFG-P` (100% on `extractUserId`), full `AST` + `UST` suites | CFG (McCabe), White-Box | High | ⏳ Pending |
| NFR-11 | **API Design** — All REST responses wrapped in `ApiResponse<T>` with `code` / `message` / `result` fields | `DT-AUTH-001`, `DT-AUTH-004`, `DT-PROFILE-002`, `DT-COMMENT-001`, `ACT.login_success` | DT, UC-Based | Medium | ⏳ Pending |
| NFR-12 | **Data Integrity** — `UserStatus.DELETE` change must be persisted via `userRepository.save()` *(BUG-001: currently absent)* | `ST-USER-003`, `ST-USER-004`, `UST` | ST | High | ❌ Fail (BUG-001) |
| NFR-13 | **Data Integrity** — Redis cache updated (post removed) when `DeletePost` called *(BUG-002: stream filter result discarded)* | `ST-POST-006` | ST | Medium | ❌ Fail (BUG-002) |

---

## PART 2 — BACKWARD TRACEABILITY (Test Case → Requirement)

> Proves no test case is orphaned — every TC maps to at least one requirement.

### Identity Service — New Test Classes

| TC-ID | Test Method | Requirement(s) Covered | Class |
|-------|-------------|----------------------|-------|
| DT-AUTH-001 | `should_returnUserNotExist_when_usernameDoesNotExist` | UC-01-AF1, NFR-11 | DT |
| DT-AUTH-002 | `should_returnAuthFailed_when_passwordIsWrong` | UC-01-AF2, NFR-11 | DT |
| DT-AUTH-004 | `should_returnAuthResponse_when_credentialsAreValid` | UC-01-P, NFR-11 | DT |
| DT-AUTH-005 | `should_returnBadRequest_when_credentialsAreBlank` | UC-01-AF3 | DT |
| DT-AUTH-006 | `should_returnNewTokens_when_tokenIsRotated` | UC-03-P | DT |
| DT-AUTH-007 | `should_returnOk_when_logoutIsSuccessful` | UC-04-P, NFR-11 | DT |
| DT-SVC-REF | `should_documentColumnMapping` *(documentation only — no assertion)* | *(Cross-reference doc)* | DT |
| ECP-CREATE-P1 | `should_createUser_when_usernameAndEmailAreUnique` | UC-02-P, NFR-04, NFR-05 | ECP |
| ECP-CREATE-N1 | `should_throwUserExisted_when_usernameAlreadyTaken` | UC-02-AF1, NFR-05 | ECP |
| ECP-CREATE-N2 | `should_throwUserExisted_when_emailAlreadyTaken` | UC-02-AF2, NFR-05 | ECP |
| ECP-CREATE-N3 | `should_propagateException_when_notificationServiceFails` | UC-02-AF5 | ECP |
| ECP-CREATE-N4 | `should_propagateException_when_profileServiceFails` | UC-02-AF6 | ECP |
| BVA-UN-001 | `should_rejectRequest_when_usernameLengthIsThree` | UC-02-AF3 | BVA |
| BVA-UN-002 | `should_acceptRequest_when_usernameLengthIsFour` | UC-02-P | BVA |
| BVA-UN-003 | `should_acceptRequest_when_usernameLengthIsFive` | UC-02-P | BVA |
| BVA-UN-004 | `should_acceptRequest_when_usernameLengthIsNominal` | UC-02-P | BVA |
| BVA-UN-005 | `should_acceptRequest_when_usernameLengthIs254` | UC-02-P | BVA |
| BVA-UN-006 | `should_acceptRequest_when_usernameLengthIs255` | UC-02-P | BVA |
| BVA-UN-007 | `should_documentRisk_when_usernameLengthIs256` | UC-02-P *(gap documented)* | BVA |
| BVA-PW-001 | `should_rejectRequest_when_passwordLengthIsFive` | UC-02-AF4 | BVA |
| BVA-PW-002 | `should_acceptRequest_when_passwordLengthIsSix` | UC-02-P | BVA |
| BVA-PW-003 | `should_acceptRequest_when_passwordLengthIsSeven` | UC-02-P | BVA |
| BVA-PW-004 | `should_acceptRequest_when_passwordLengthIsNominal` | UC-02-P | BVA |
| BVA-PW-NEG | `should_rejectRequest_when_passwordIsBelowMinimum` | UC-02-AF3, UC-02-AF4 | BVA |
| ST-USER-001 | `should_createUserWithInactiveStatus_when_userRegisters` | UC-02-P, NFR-04 | ST-U |
| ST-USER-003 | `should_persistDeleteStatus_when_deleteUserCalledOnActiveUser` | UC-07-P, NFR-12 | ST-U |
| ST-USER-004 | `should_setDeleteStatus_when_deleteUserCalledOnInactiveUser` | UC-07-P, NFR-12 | ST-U |
| ST-USER-005 | `should_throwUserNotExist_when_deleteCalledForUnknownUser` | UC-07-AF1 | ST-U |
| ST-USER-006 | `should_throwUserExisted_when_activeUserTriesToRegisterSameUsername` | UC-02-AF1, NFR-05 | ST-U |
| ST-USER-GET | `should_throwUserNotExist_when_getUserCalledWithUnknownId` | UC-06-AF1 | ST-U |
| ST-USER-GET-P | `should_returnUserResponse_when_getUserCalledWithExistingId` | UC-06-P | ST-U |
| CE-REFRESH-001 | `should_throwTokenInvalid_when_refreshTokenSignatureIsInvalid` | UC-03-AF1, NFR-01 | CE-A |
| CE-REFRESH-002 | `should_throwTokenTypeInvalid_when_accessTokenSubmittedAsRefreshToken` | UC-03-AF2 | CE-A |
| CE-REFRESH-003 | `should_throwTokenNotFound_when_refreshTokenMissingFromDatabase` | UC-03-AF3 | CE-A |
| CE-REFRESH-004 | `should_throwUserNotExist_when_userDeletedAfterTokenIssued` | UC-03-AF4 | CE-A |
| CE-REFRESH-005 | `should_returnNewTokensAndRevokeOldToken_when_allCausesAreTrue` | UC-03-P, NFR-03 | CE-A |
| CE-LOGOUT-001 | `should_onlyDeleteRefreshToken_when_noAuthorizationHeaderPresent` | UC-04-AF1 | CE-A |
| CE-LOGOUT-002 | `should_blacklistAccessToken_when_validBearerTokenPresent` | UC-04-P, NFR-03 | CE-A |
| CE-LOGOUT-003 | `should_skipBlacklist_when_accessTokenIsAlreadyExpired` | UC-04-AF2, NFR-02 | CE-A |
| CFG-A.PATH-1 | `should_returnSignedJWT_when_tokenIsValidAndNotExpired` | NFR-01, UC-08-P | CFG-A |
| CFG-A.PATH-2 | `should_throwTokenInvalid_when_signatureIsInvalid` | NFR-01, UC-08-AF1 | CFG-A |
| CFG-A.PATH-3 | `should_throwTokenExpired_when_tokenIsExpired` | NFR-02 | CFG-A |
| CFG-A.PATH-4 | `should_propagateParseException_when_tokenIsMalformed` | NFR-01 | CFG-A |
| CFG-A.PATH-5 | `should_throwTokenExpired_when_tokenExpiresAtExactBoundary` | NFR-02 | CFG-A |

---

### Post Service — New Test Classes

| TC-ID | Test Method | Requirement(s) Covered | Class |
|-------|-------------|----------------------|-------|
| DT-POST-001 | `should_returnPostResponse_when_userExistsAndMediaProvided` | UC-09-P | P-DT |
| DT-POST-002 | `should_propagateUserNotFound_when_userDoesNotExist` | UC-09-AF1 | P-DT |
| DT-PROFILE-001 | `should_throw_when_userDoesNotExist` | UC-10-AF1 | P-DT |
| DT-PROFILE-002 | `should_returnEmptyList_when_userExistsButHasNoPosts` | UC-10-P-EMPTY, NFR-11 | P-DT |
| DT-PROFILE-003 | `should_returnPostList_when_userExistsAndHasPosts` | UC-10-P-POSTS | P-DT |
| DT-DELETE-001 | `should_return200_when_deleteIsSuccessful` | UC-11-P | P-DT |
| DT-DELETE-002 | `should_propagateException_when_userNotFoundOnDelete` | UC-11-AF1 | P-DT |
| DT-LIKE-001 | `should_return200_when_likePostCalled` | UC-12-P | P-DT |
| DT-UNLIKE-001 | `should_return200_when_unlikePostCalled` | UC-13-P | P-DT |
| DT-COMMENT-001 | `should_return200_when_commentPosted` | UC-14-P, NFR-11 | P-DT |
| DT-CACHE-001 | `should_return200_when_cacheClearedSuccessfully` | UC-16-P | P-DT |
| ECP-LIKE-P1 | `should_saveLikeAndIncrementCount_when_allConditionsAreValid` | UC-12-P, NFR-06 | P-ECP |
| ECP-LIKE-P2 | `should_createPostLikeWithCorrectFields_when_likeIsSaved` | UC-12-P | P-ECP |
| ECP-LIKE-N1 | `should_throwRuntimeException_when_authorizationHeaderIsNull` | UC-12-AF1 | P-ECP |
| ECP-LIKE-N2 | `should_throwRuntimeException_when_authorizationHeaderIsBlank` | UC-12-AF1 | P-ECP |
| ECP-LIKE-N3 | `should_returnWithoutSaving_when_postAlreadyLiked` | UC-12-AF2, NFR-06 | P-ECP |
| ECP-LIKE-N4 | `should_returnSilently_when_postNotFound` | UC-12-AF3 | P-ECP |
| ECP-UNLIKE-P1 | `should_deleteLikeAndDecrementCount_when_likeExists` | UC-13-P | P-ECP |
| ECP-UNLIKE-N1 | `should_notDecrementBelowZero_when_likedCountIsAlreadyZero` | UC-13-AF2, NFR-07 | P-ECP |
| ECP-UNLIKE-N2 | `should_returnWithoutDeleting_when_noLikeExists` | UC-13-AF1 | P-ECP |
| BVA-LIKE-001 | `should_incrementLikedByOne_when_postHasZeroLikes` | UC-12-P, NFR-07 *(SEED-003)* | P-BVA |
| BVA-LIKE-002 | `should_incrementLikedByOne_when_postHasOneLike` | UC-12-P | P-BVA |
| BVA-LIKE-003 | `should_incrementLikedByOne_when_postHasNominalLikes` | UC-12-P | P-BVA |
| BVA-LIKE-004 | `should_incrementLikedByOne_when_postHasManyLikes` | UC-12-P | P-BVA |
| BVA-LIKE-005 | `should_incrementLikedToMaxLong_when_likedIsMaxMinusOne` | UC-12-P | P-BVA |
| BVA-LIKE-MULTI | `should_incrementByExactlyOne_when_likeIsApplied` | UC-12-P, NFR-07 | P-BVA |
| BVA-UNLIKE-001 | `should_notDecrementBelowZero_when_likedIsAtMin` | UC-13-AF2, NFR-07 | P-BVA |
| BVA-UNLIKE-002 | `should_decrementToZero_when_likedIsOne` | UC-13-P | P-BVA |
| BVA-UNLIKE-003 | `should_decrementToOne_when_likedIsTwo` | UC-13-P | P-BVA |
| BVA-UNLIKE-004 | `should_decrementByOne_when_likedIsNominal` | UC-13-P | P-BVA |
| BVA-UNLIKE-MULTI | `should_respectGuard_when_unlikeApplied` | UC-13-AF2, NFR-07 | P-BVA |
| BVA-UNIQUE-001 | `should_saveLike_when_noExistingLikePresent` | UC-12-P, NFR-06 | P-BVA |
| BVA-UNIQUE-002 | `should_notSaveLike_when_likeAlreadyExists` | UC-12-AF2, NFR-06 | P-BVA |
| ST-POST-001 | `should_transitionToLiked_when_likePostApplied` | UC-12-P | ST-P |
| ST-POST-002 | `should_transitionBackToCreated_when_unlikePostApplied` | UC-13-P | ST-P |
| ST-POST-003 | `should_transitionToCommented_when_commentPostApplied` | UC-14-P | ST-P |
| ST-POST-004 | `should_deletePost_when_deletePostApplied` | UC-11-P | ST-P |
| ST-POST-005 | `should_returnSilently_when_likeAppliedToDeletedPost` | UC-12-AF3 | ST-P |
| ST-POST-006 | `should_documentCacheFilterBug_when_deletePostUpdatesCache` | UC-11-P, NFR-13 | ST-P |
| ST-POST-007 | `should_returnCreatedPosts_when_getPostInUserProfileCalled` | UC-10-P-EMPTY, UC-10-P-POSTS | ST-P |
| ST-POST-008 | `should_throwUserNotFoundException_when_userDoesNotExistInProfile` | UC-10-AF1, UC-11-AF1, UC-14-AF1 | ST-P |
| CE-LIKE-001 | `should_throwUserNotFoundException_when_userDoesNotExist` | UC-12-AF4 | CE-P |
| CE-LIKE-002 | `should_returnSilently_when_postNotFound` | UC-12-AF3 | CE-P |
| CE-LIKE-003 | `should_returnIdempotently_when_postAlreadyLiked` | UC-12-AF2, NFR-06 | CE-P |
| CE-LIKE-004 | `should_saveLikeAndIncrementCount_when_noExistingLikeAndRedisDown` | UC-12-P, NFR-08 | CE-P |
| CE-LIKE-005 | `should_saveLikeAndUpdateCache_when_noExistingLikeAndRedisAvailable` | UC-12-P, NFR-08 | CE-P |
| MAP-001 | `should_mapAllFields_when_postIsMapped` | UC-09-P, UC-10-P-POSTS | CE-P |
| MAP-002 | `should_defaultLikedByUserToFalse_when_postIsMapped` | UC-09-P, UC-10-P-POSTS | CE-P |
| CFG-P.PATH-1 | `should_throw_when_authorizationHeaderIsNullOrBlank` | UC-12-AF1, UC-13 (general) | CFG-P |
| CFG-P.PATH-2 | `should_returnUserId_when_validBearerToken` | UC-12-P, NFR-01 *(SEED-005)* | CFG-P |
| CFG-P.PATH-3 | `should_returnUserId_when_tokenWithoutBearerPrefix` | UC-12-P | CFG-P |
| CFG-P.PATH-4 | `should_throwInvalidJwtFormat_when_jwtHasFewerThanTwoParts` | UC-12-AF1 | CFG-P |
| CFG-P.PATH-5 | `should_throwMissingSubClaim_when_subClaimIsAbsent` | UC-12-AF1 | CFG-P |
| CFG-P.PATH-6 | `should_throwInvalidToken_when_base64PayloadIsInvalid` | UC-12-AF1 | CFG-P |
| SEED-005-GUARD | `should_notReturnNullUserId_when_correctPayloadIndexIsUsed` | NFR-01 | CFG-P |

---

## PART 3 — COVERAGE SUMMARY

### By Service and Requirement Type

| Scope | Total Requirements | With ≥ 1 TC | Not Covered | Coverage % |
|-------|--------------------|-------------|-------------|------------|
| Identity — Functional (UCs) | 22 | 20 | 2 *(UC-05-AF2 relies only on AST; UC-06-AF2 on UCT)* | **91 %** |
| Post — Functional (UCs) | 18 | 16 | 2 *(UC-15-P, UC-15-AF1)* | **89 %** |
| Non-Functional (NFRs) | 13 | 11 | 2 *(NFR-09 retry logic, partially NFR-10 at aggregate level)* | **85 %** |
| **TOTAL** | **53** | **47** | **6** | **89 %** |

---

### By Test Case Count

| Test Class | New TCs | Requirements Covered | Orphans |
|------------|---------|---------------------|---------|
| `AuthLoginDecisionTableTest` | 7 | UC-01, UC-03, UC-04 | 0 |
| `UserCreationECPTest` | 4 | UC-02, NFR-04, NFR-05 | 0 |
| `UserCreationBVATest` | 12 | UC-02 (all flows) | 0 |
| `UserAccountStateTransitionTest` | 7 | UC-02, UC-06, UC-07, NFR-12 | 0 |
| `AuthTokenCauseEffectTest` | 8 | UC-03, UC-04, NFR-02, NFR-03 | 0 |
| `AuthVerifyTokenCFGTest` | 5 | NFR-01, NFR-02, UC-08 | 0 |
| `PostControllerDTTest` | 11 | UC-09 through UC-16 | 0 |
| `PostLikeECPTest` | 9 | UC-12, UC-13, NFR-06, NFR-07 | 0 |
| `PostLikeBVATest` | 13 | UC-12, UC-13, NFR-06, NFR-07 | 0 |
| `PostLifecycleStateTransitionTest` | 8 | UC-10 through UC-14, NFR-13 | 0 |
| `CreatePostCauseEffectTest` | 7 | UC-12, NFR-06, NFR-08 | 0 |
| `ExtractUserIdCFGTest` | 8 | UC-12-AF1, NFR-01 | 0 |
| **TOTAL (new)** | **99** | **All 53 reqs (47 fully covered)** | **0** |

> **No orphaned test cases detected.** Every new TC maps to at least one functional or non-functional requirement.

---

### Requirements with Zero Coverage (⚠ NOT COVERED)

| REQ-ID | Description | Reason | Recommended Action |
|--------|-------------|--------|--------------------|
| UC-15-P | Get Feed — posts from followed users | Requires live HTTP calls to profile service (not mockable without WireMock) | Add `@SpringBootTest` integration test with WireMock stub for profile service |
| UC-15-AF1 | Get Feed — fallback to all posts when no followed users | Same as above | Same — WireMock integration test |
| NFR-09 | HTTP retry up to 3× on `ConnectException` | `sendGetWithRetry` uses a `final HttpClient` field, not injectable without refactoring | Refactor to inject `HttpClient` via constructor; add retry unit test with mock client |

---

## PART 4 — BUG IMPACT ON RTM

The following requirements are currently **blocked** by known bugs. RTM status must remain ❌ Fail until the bug is fixed and the test is re-run.

| Bug ID | REQ-ID(s) Affected | TC(s) That Fail | Fix Required |
|--------|--------------------|-----------------|--------------|
| BUG-001 | UC-07-P, NFR-12 | `ST-USER-003`, `ST-USER-004` | Add `userRepository.save(user)` in `UserService.deleteUser()` |
| BUG-002 | UC-11-P, NFR-13 | `ST-POST-006` | Assign stream filter result back: `userPostProfileResponse.setListUserPost(filteredList)` |
| BUG-003 | NFR-08 (partial) | *(integration test only)* | Replace `new JedisPool()` with Spring-configured connection factory |
| BUG-004 | UC-10-AF1 | `DT-PROFILE-001`, `ST-POST-008` | Change `"1002"` → `"1000"` in `PostService.CheckUserExisted` |

---

## PART 5 — RTM CHANGE LOG

| Version | Date | Change | Author |
|---------|------|--------|--------|
| 1.0 | 2026-06-07 | Initial RTM — 53 requirements, 99 new TCs, 6 uncovered items | QA Team |

> **Maintenance rule:** Update this file whenever a new test is added (`git commit` hook recommended) or a requirement changes. Re-run `mvn verify` and update Status column after each CI build.
