# Test Strategy Document
## Social Network Microservice — Identity & Post Services

**Project:** Social_Network_Microservice  
**Services Under Test:** `identity_service` · `post_service`  
**QA Framework:** JUnit 5 + Mockito (backend) · JaCoCo (coverage)

---

## 1. PARETO PRINCIPLE — Top 20 % Modules (80 % of Value)

The following modules are ranked by **usage frequency × criticality**.
A defect in any of these causes cascading failures across the entire platform.

| Rank | Module | Service | Justification | Priority |
|------|--------|---------|---------------|----------|
| 1 | `AuthService.authenticateUser` | identity | Every API call is gated by a successful login. A broken login renders the whole platform unusable. | **P0** |
| 2 | `AuthService.verifyToken` | identity | Called by every protected endpoint. Silent failures allow unauthorised access (security breach). | **P0** |
| 3 | `AuthService.refreshTokenAfterTimeOut` | identity | Token rotation is the only mechanism keeping long-lived sessions alive. A bug here forces constant re-login or allows session hijacking via stale tokens. | **P1** |
| 4 | `UserService.createUser` | identity | All platform users must register. Duplicate-user or orphan-profile bugs permanently corrupt data. | **P1** |
| 5 | `PostService.CreateNewPost` | post | Core social action — creates the post, uploads media to R2, and updates the Redis cache atomically. Failure breaks content creation for every user. | **P1** |
| 6 | `PostService.LikePost / unlikePost` | post | Highest-frequency mutation endpoints (social engagement). Concurrency bugs lead to incorrect like counts that are visible to all followers. | **P2** |
| 7 | `AuthService.logOut` | identity | Tokens must be blacklisted on logout. A bug here is a direct security vulnerability. | **P2** |
| 8 | `UserService.deleteUser` | identity | **Known bug** — `userRepository.save(user)` is never called after `user.setUserStatus(DELETE)`, so the status change is lost. High risk. | **P2** |

> **Key finding:** Modules ranked 1–4 (identity authentication + registration) form a single authentication boundary. Fix and test these before any post-service work.

---

## 2. TOP-DOWN TESTING PLAN WITH STUB REGISTRY

Testing proceeds from the outermost architectural layer inward.

```
Layer 0 ─ HTTP / Integration  (not in scope for unit tests)
Layer 1 ─ REST Controllers    ← Start here (MockMvc)
Layer 2 ─ Service Layer       ← Stub: repositories + HTTP clients
Layer 3 ─ Repository Layer    ← @DataJpaTest with H2
```

### 2.1 Controller Layer (Layer 1)

| Test Class | Stubs Used | Simulates |
|------------|------------|-----------|
| `AuthLoginDecisionTableTest` | `IAuthService` (Mockito mock) | Full service behaviour per decision column |
| `PostControllerDTTest` | `PostService` (Mockito mock) | All post CRUD + cache operations |
| `UserControllerECPTest` | `IUserService` (Mockito mock) | User creation + retrieval validation paths |

All controller stubs are created with `@MockitoBean` / `@MockBean` on the service interface.
Controllers are exercised via `MockMvc` (`@WebMvcTest`).

### 2.2 Service Layer (Layer 2)

| Test Class | Stubs Used | Simulates |
|------------|------------|-----------|
| `AuthLoginDecisionTableTest` (service layer variant) | `UserRepository`, `PasswordEncoder`, `RefreshTokenRepository`, `RedisTokenService`, `EmailVerifyTokenRepository` | Database + Redis behaviour |
| `UserCreationECPTest` | `UserRepository`, `UserMapper`, `UserProfileMapper`, `ProfileClient`, `NotificationClient`, `EmailVerifyTokenRepository` | Profile + notification calls |
| `PostLikeECPTest` | `PostRepo`, `PostLikeRepo`, `CacheManager`, `PostService.checkRedisConnection` (spy) | Redis on/off, DB likes |
| `ExtractUserIdCFGTest` | None (pure logic test on `extractUserIdFromAuthorizationHeader`) | JWT base64 parsing |

### 2.3 Stub Registry

| Stub ID | Stub Target | Type | Used In | What It Returns |
|---------|-------------|------|---------|-----------------|
| STB-01 | `UserRepository.findByUserName` | Mockito mock | `AuthLoginDecisionTableTest` | `Optional.of(user)` or `Optional.empty()` |
| STB-02 | `PasswordEncoder.matches` | Mockito mock | `AuthLoginDecisionTableTest` | `true` / `false` |
| STB-03 | `RefreshTokenRepository.findByRefreshToken` | Mockito mock | `AuthServiceBVATest` | Valid `RefreshToken` or empty |
| STB-04 | `ProfileClient.createProfile` | Mockito mock | `UserCreationECPTest` | `UserProfileResponse` stub |
| STB-05 | `NotificationClient.verifyEmailUser` | Mockito mock | `UserCreationECPTest` | void (no-op) |
| STB-06 | `PostRepo.findById` | Mockito mock | `PostLikeECPTest` | `Optional<Post>` |
| STB-07 | `PostLikeRepo.existsByPostIdAndUserId` | Mockito mock | `PostLikeECPTest` | `true` / `false` |
| STB-08 | `CacheManager.getCache("Post")` | Mockito mock | `CreatePostCauseEffectTest` | `Cache` mock |
| STB-09 | `PostService.checkRedisConnection` | Mockito spy | `PostLikeECPTest`, `CreatePostCauseEffectTest` | `true` / `false` |
| STB-10 | `HttpClient.send(...)` | `ReflectionTestUtils` inject | `PostLifecycleStateTransitionTest` | Preset `HttpResponse<String>` |

---

## 3. ERROR SEEDING PLAN

### 3.1 Seeding Strategy

Five bugs are deliberately injected into **copies** of the production code (or described as precise mutations).
After the test suite runs, the ratio of found seeds to total seeds is used to estimate residual real defects.

**Formula:**
```
Remaining real defects ≈ (Total seeds − Seeds found) × (Real bugs found / Seeds found)
```

### 3.2 Seed Definitions

| Seed ID | Service | File | Location | Bug Type | Mutation Description |
|---------|---------|------|----------|----------|----------------------|
| SEED-001 | identity | `AuthService.java` | `verifyToken()` line — expiry check | Logic inversion | Change `expiryTime.before(new Date(...))` → `expiryTime.after(new Date(...))` so valid tokens appear expired |
| SEED-002 | identity | `UserService.java` | `createUser()` duplicate-email guard | Missing condition | Remove `userRepository.existsByEmail(request.getEmail())` so duplicate emails are allowed |
| SEED-003 | post | `PostService.java` | `LikePost()` increment | Off-by-one | Change `post.getLiked()+1` → `post.getLiked()+2` so every like adds 2 |
| SEED-004 | identity | `AuthService.java` | `refreshTokenAfterTimeOut()` revocation | Missing call | Remove `refreshTokenRepository.delete(oldToken)` so old refresh tokens remain valid after rotation |
| SEED-005 | post | `PostService.java` | `extractUserIdFromAuthorizationHeader()` | Wrong array index | Change `tokenParts[1]` → `tokenParts[0]` so the JWT header is decoded instead of the payload |

### 3.3 Error Seeding Results Table (to be filled after test run)

| Seed ID | Location | Type | Found by Test | Test Class | Pass/Fail |
|---------|----------|------|---------------|------------|-----------|
| SEED-001 | `AuthService.verifyToken` | Logic inversion | Y | `AuthVerifyTokenCFGTest` | — |
| SEED-002 | `UserService.createUser` | Missing condition | Y | `UserCreationECPTest` | — |
| SEED-003 | `PostService.LikePost` | Off-by-one | Y | `PostLikeBVATest` | — |
| SEED-004 | `AuthService.refreshTokenAfterTimeOut` | Missing call | Y | `AuthLoginDecisionTableTest` | — |
| SEED-005 | `PostService.extractUserIdFromAuthorizationHeader` | Wrong index | Y | `ExtractUserIdCFGTest` | — |

### 3.4 Pre-existing Bugs (discovered during analysis — NOT seeded)

| Bug ID | Service | File | Description | Severity |
|--------|---------|------|-------------|----------|
| BUG-001 | identity | `UserService.deleteUser` | `userRepository.save(user)` is never called — `UserStatus.DELETE` change is lost | HIGH |
| BUG-002 | post | `PostService.DeletePost` | `stream().filter(...)` result is not assigned back; cache is never updated | MEDIUM |
| BUG-003 | post | `PostService.checkRedisConnection` | Creates `new JedisPool()` with default localhost:6379 ignoring Spring config; always fails in non-default setups | MEDIUM |
| BUG-004 | post | `PostService.CheckUserExisted` | Uses `"1002"` (AUTHENTICATED_FAILED) as the comparison code; should be `"1000"` (success) | HIGH |

> After running the full seeded suite: if 4/5 seeds are found and 4 real bugs were also found:  
> Remaining ≈ (5 − 4) × (4 / 4) = **1 residual defect** — estimate should trigger re-inspection of post service cache logic.

---

## 4. TEST EXECUTION ORDER

```
Phase 1 ─ Unit (no Spring context)
  → AuthLoginDecisionTableTest
  → UserCreationECPTest / UserCreationBVATest
  → UserAccountStateTransitionTest
  → AuthTokenCauseEffectTest
  → AuthVerifyTokenCFGTest
  → PostLikeECPTest / PostLikeBVATest
  → PostLifecycleStateTransitionTest
  → CreatePostCauseEffectTest
  → ExtractUserIdCFGTest

Phase 2 ─ Slice (@WebMvcTest)
  → PostControllerDTTest
  → (existing) AuthControllerTest / UserControllerTest

Phase 3 ─ Coverage Report
  → mvn verify (JaCoCo) for identity
  → (manual) Istanbul for any React frontend
```

---

## 5. TOOLS & CONFIGURATION

| Tool | Purpose | Config |
|------|---------|--------|
| JUnit 5 | Test runner | Spring Boot Starter Test |
| Mockito 5 | Stubs & mocks | `@ExtendWith(MockitoExtension.class)` |
| MockMvc | Controller slice | `@WebMvcTest` |
| JaCoCo | Coverage (identity) | 80 % branch coverage enforced on `AuthService`, `UserService` |
| H2 | In-memory DB for `@DataJpaTest` | `src/test/resources/application-test.properties` |
| Nimbus JOSE JWT | JWT generation in tests | `com.nimbusds:nimbus-jose-jwt` |
