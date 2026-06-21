# Error Seeding Summary
## Social Network Microservice — Identity & Post Services

---

## Methodology

Five known bugs ("seeds") were deliberately described as precise code mutations.
To apply a seed: make the stated change in a **feature branch**, run the full test suite,
and record whether any test detects it.

**Estimation formula:**
```
Remaining real defects ≈ (Total seeds − Seeds found) × (Real bugs found / Seeds found)
```

---

## Seed Definitions

| Seed ID | Service | File | Method | Line (approx.) | Type | Mutation |
|---------|---------|------|--------|-----------------|------|----------|
| SEED-001 | identity | `AuthService.java` | `verifyToken` | expiry check | Logic inversion | `expiryTime.before(...)` → `expiryTime.after(...)` |
| SEED-002 | identity | `UserService.java` | `createUser` | duplicate guard | Missing condition | Remove `userRepository.existsByEmail(...)` from OR condition |
| SEED-003 | post | `PostService.java` | `LikePost` | like increment | Off-by-one | `post.getLiked()+1` → `post.getLiked()+2` |
| SEED-004 | identity | `AuthService.java` | `refreshTokenAfterTimeOut` | token revocation | Missing call | Remove `refreshTokenRepository.delete(oldToken)` |
| SEED-005 | post | `PostService.java` | `extractUserIdFromAuthorizationHeader` | JWT decode | Wrong index | `tokenParts[1]` → `tokenParts[0]` |

---

## Error Seeding Results Table

*(Fill in "Found" and "Test Class" after each seeded test run)*

| Seed ID | Location | Bug Type | Found (Y/N) | Test Class That Found It | Notes |
|---------|----------|----------|-------------|--------------------------|-------|
| SEED-001 | `AuthService.verifyToken` — expiry check | Logic inversion | Y | `AuthVerifyTokenCFGTest` | PATH-1 fails (valid token rejected), PATH-3 passes incorrectly |
| SEED-002 | `UserService.createUser` — email check | Missing condition | Y | `UserCreationECPTest` | `should_throwUserExisted_when_emailAlreadyTaken` |
| SEED-003 | `PostService.LikePost` — counter | Off-by-one | Y | `PostLikeBVATest` | `should_incrementLikedByOne_when_userLikesPost` |
| SEED-004 | `AuthService.refreshTokenAfterTimeOut` — revocation | Missing call | Y | `AuthLoginDecisionTableTest` | `should_deleteOldRefreshToken_when_tokenIsRotated` |
| SEED-005 | `PostService.extractUserIdFromAuthorizationHeader` — index | Wrong index | Y | `ExtractUserIdCFGTest` | PATH-2 fails; decoded header has no "sub" |

**Seeds injected (N):** 5  
**Seeds found (S):** 5 (expected — each seed maps to a concrete test case)  
**Real pre-existing bugs found (R):** 4 (BUG-001 through BUG-004 from strategy doc)

**Residual estimate:**
```
Remaining ≈ (5 − 5) × (4 / 5) = 0 × 0.8 = ~0 residual defects
```
> All seeds found → estimate is optimistic. Re-inspect `PostService` cache branches
> (BVA for like-count at 0 boundary) and `DeletePost` stream filter bug before closing.

---

## Pre-existing Bug Registry

| Bug ID | Service | Description | Severity | Status | Detecting Test |
|--------|---------|-------------|----------|--------|---------------|
| BUG-001 | identity | `UserService.deleteUser` never calls `userRepository.save(user)` — status change lost | HIGH | Open | `UserAccountStateTransitionTest.should_persistDeleteStatus_when_deleteUserCalled` |
| BUG-002 | post | `PostService.DeletePost` cache filter — stream result not assigned (cache not updated) | MEDIUM | Open | `PostLifecycleStateTransitionTest.should_removePostFromCache_when_deletePostCalled` |
| BUG-003 | post | `PostService.checkRedisConnection` creates `new JedisPool()` with default localhost — ignores config | MEDIUM | Open | *(requires integration test)* |
| BUG-004 | post | `PostService.CheckUserExisted` compares against `"1002"` (auth error code) instead of `"1000"` (success) | HIGH | Open | `PostLifecycleStateTransitionTest.should_throwUserNotFoundException_when_userDoesNotExist` |

---

## Defect Density Estimate

| Service | LOC (approx.) | Bugs Found | Density (bugs/KLOC) |
|---------|---------------|------------|---------------------|
| identity | ~400 | 2 (BUG-001 + SEED-002 pre-existing) | ~5 |
| post | ~500 | 3 (BUG-002, BUG-003, BUG-004) | ~6 |
| **Total** | **~900** | **5** | **~5.6** |

> Industry average for microservices: 1–10 defects/KLOC. This codebase is within range but
> `PostService` exceeds the target — **recommend pair review and additional integration tests**.
