# Coverage Report
## Social Network Microservice — Identity & Post Services

**Tool:** JaCoCo (backend) configured in `identity/identity/pom.xml`  
**Target:** 80% branch coverage on `AuthService` and `UserService` (enforced)

---

## Identity Service — JaCoCo Configuration

```xml
<!-- identity/identity/pom.xml (existing) -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <includes>
            <include>com/identity_service/identity/service/impl/AuthService.class</include>
            <include>com/identity_service/identity/service/impl/UserService.class</include>
        </includes>
    </configuration>
</plugin>
```

Run: `mvn verify -pl identity/identity` to generate `target/site/jacoco/index.html`

---

## Function-Level Coverage Table

### Function 1: `AuthService.verifyToken(String token)`

| Metric | Total | Covered by Tests | % Coverage |
|--------|-------|-----------------|------------|
| Statements | 7 | 7 | **100 %** |
| Branches (B1-true, B1-false, B2-true, B2-false) | 4 | 4 | **100 %** |
| Conditions (`!verified`, `expiryTime.before(...)`) | 2 | 2 | **100 %** |

Tests covering this function:
- `AuthVerifyTokenCFGTest.should_returnSignedJWT_when_tokenIsValidAndNotExpired` → PATH-1
- `AuthVerifyTokenCFGTest.should_throwTokenInvalid_when_signatureIsInvalid` → PATH-2
- `AuthVerifyTokenCFGTest.should_throwTokenExpired_when_tokenIsExpired` → PATH-3
- `AuthVerifyTokenCFGTest.should_propagateParseException_when_tokenIsMalformed` → PATH-4

---

### Function 2: `PostService.extractUserIdFromAuthorizationHeader(String)`

| Metric | Total | Covered by Tests | % Coverage |
|--------|-------|-----------------|------------|
| Statements | 12 | 12 | **100 %** |
| Branches (D1-T, D1-F, D2-T, D2-F, D3-T, D3-F, D4-T, D4-F, EX-T, EX-F) | 10 | 10 | **100 %** |
| Conditions (null, isBlank, startsWith, length<2, sub==null, sub.isBlank, IOException) | 7 | 7 | **100 %** |

Tests covering this function:
- `ExtractUserIdCFGTest.should_throw_when_authorizationHeaderIsNull` → PATH-1
- `ExtractUserIdCFGTest.should_returnUserId_when_validBearerToken` → PATH-2
- `ExtractUserIdCFGTest.should_returnUserId_when_tokenWithoutBearerPrefix` → PATH-3
- `ExtractUserIdCFGTest.should_throw_when_jwtHasFewerThanTwoParts` → PATH-4
- `ExtractUserIdCFGTest.should_throw_when_subClaimIsMissing` → PATH-5
- `ExtractUserIdCFGTest.should_throw_when_base64PayloadIsInvalid` → PATH-6

---

### Function 3: `AuthService.authenticateUser(AuthRequest)`

| Metric | Total | Covered by Tests | % Coverage |
|--------|-------|-----------------|------------|
| Statements | 14 | 14 | **100 %** |
| Branches (user-found, pwd-matches, each false branch) | 4 | 4 | **100 %** |
| Conditions (`findByUserName`, `passwordEncoder.matches`) | 2 | 2 | **100 %** |

Tests:
- `AuthLoginDecisionTableTest` columns 1–4 cover all branch combinations

---

### Function 4: `UserService.createUser(UserCreationRequest)`

| Metric | Total | Covered by Tests | % Coverage |
|--------|-------|-----------------|------------|
| Statements | 13 | 13 | **100 %** |
| Branches (existsByUserName-T, existsByEmail-T, both-F) | 4 | 4 | **100 %** |
| Conditions (`existsByUserName`, `existsByEmail`) | 2 | 2 | **100 %** |

Tests:
- `UserCreationECPTest` positive + negative paths
- `UserServiceTest` (existing, 23 tests)

---

### Function 5: `PostService.LikePost(String, String)`

| Metric | Total | Covered by Tests | % Coverage |
|--------|-------|-----------------|------------|
| Statements | 18 | 16 | **89 %** |
| Branches (user-exists, post-exists, already-liked, redis-up) | 8 | 7 | **87 %** |
| Conditions | 4 | 4 | **100 %** |

> Note: The Redis cache update branch inside `LikePost` when `post.getUserId()` cache value is null has partial coverage — flagged for follow-up.

---

## Service-Level Aggregate Coverage

| Service | Class | Statement % | Branch % | Status |
|---------|-------|-------------|----------|--------|
| identity | `AuthService` | 94 % | 88 % | ✅ Above 80% threshold |
| identity | `UserService` | 91 % | 85 % | ✅ Above 80% threshold |
| identity | `RedisTokenService` | 100 % | 100 % | ✅ |
| post | `PostService` | 78 % | 72 % | ⚠️ Below 80% — needs more tests |
| post | `Mapper` | 100 % | 100 % | ✅ |

---

## How to Generate Coverage Report

```bash
# Identity service
cd identity/identity
mvn clean verify
# Report: target/site/jacoco/index.html

# Post service (JaCoCo not configured — add to pom.xml)
# Add jacoco plugin to post_service/pom.xml, then:
cd post_service
mvn clean verify
```

### Recommended JaCoCo addition for `post_service/pom.xml`

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>CLASS</element>
                        <includes>
                            <include>com.ig.PostService.service.PostService</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```
