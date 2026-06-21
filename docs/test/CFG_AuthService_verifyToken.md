# CFG — `AuthService.verifyToken(String token)`
## McCabe Cyclomatic Complexity Analysis

**Service:** identity_service  
**Class:** `com.identity_service.identity.service.impl.AuthService`  
**Method:** `protected SignedJWT verifyToken(String token) throws JOSEException, ParseException`

---

## Source Code (annotated with node labels)

```java
protected SignedJWT verifyToken(String token) throws JOSEException, ParseException {

    // ─── NODE 1 ───────────────────────────────────────────────────────────
    JWSVerifier jwsVerifier = new MACVerifier(secretKey.getBytes());
    SignedJWT signedJWT = SignedJWT.parse(token);
    var verified = signedJWT.verify(jwsVerifier);

    // ─── DECISION D1 ──────────────────────────────────────────────────────
    if (!verified) {                          // D1: verified == false?
        // ── NODE 2 (D1 = TRUE) ───────────────────────────────────────────
        throw new AppException(ErrorCode.TOKEN_INVALID);
    }

    // ─── NODE 3 (D1 = FALSE) ─────────────────────────────────────────────
    Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

    // ─── DECISION D2 ──────────────────────────────────────────────────────
    if (expiryTime.before(new Date(Instant.now().toEpochMilli()))) {  // D2: expired?
        // ── NODE 4 (D2 = TRUE) ───────────────────────────────────────────
        throw new AppException(ErrorCode.TOKEN_EXPIRED);
    }

    // ─── NODE 5 (D2 = FALSE) ─────────────────────────────────────────────
    return signedJWT;
}
```

---

## Control Flow Graph (ASCII)

```
       ┌─────────────┐
       │   START / N1│  (verify signature setup)
       │  MACVerifier│
       │  parse token│
       │  verify()   │
       └──────┬──────┘
              │
         [D1: !verified]
        ┌─────┴──────┐
     T  │            │ F
        ▼            ▼
  ┌──────────┐  ┌──────────┐
  │  NODE 2  │  │  NODE 3  │  (get expiry time)
  │  throw   │  │ getExpiry│
  │TOKEN_INV │  └────┬─────┘
  └────┬─────┘       │
       │         [D2: expired?]
       │        ┌────┴────┐
       │     T  │         │ F
       │        ▼         ▼
       │  ┌──────────┐  ┌──────────┐
       │  │  NODE 4  │  │  NODE 5  │
       │  │  throw   │  │  return  │
       │  │TOKEN_EXP │  │ signedJWT│
       │  └────┬─────┘  └────┬─────┘
       │       │             │
       └───────┴─────────────┘
                    │
                 ┌──┴──┐
                 │ END │
                 └─────┘
```

---

## McCabe Complexity Calculation

| Metric | Count |
|--------|-------|
| **N** — number of nodes | 5 (N1, N2, N3, N4, N5) |
| **E** — number of edges | 6 (N1→N2, N1→N3, N2→END, N3→N4, N3→N5, N4→END, N5→END) → 7 |
| **P** — connected components | 1 |
| **V(G) = E − N + 2P** | 7 − 5 + 2(1) = **4** |

> **V(G) = 4** → minimum **4 independent test paths** required.  
> V(G) < 10: complexity is **acceptable** — no refactoring needed.

---

## Independent Paths

| Path ID | Node Sequence | Description | Test Method |
|---------|---------------|-------------|-------------|
| PATH-1 | N1 → N3 → N5 | Valid token, not expired → returns `SignedJWT` | `should_returnSignedJWT_when_tokenIsValidAndNotExpired` |
| PATH-2 | N1 → N2 → END | Signature verification fails → `TOKEN_INVALID` | `should_throwTokenInvalid_when_signatureIsInvalid` |
| PATH-3 | N1 → N3 → N4 → END | Signature valid but token is expired → `TOKEN_EXPIRED` | `should_throwTokenExpired_when_tokenIsExpired` |
| PATH-4 | N1 → (exception on parse) → END | `SignedJWT.parse` throws `ParseException` (propagates up) | `should_propagateParseException_when_tokenIsMalformed` |

> Path-4 represents the exception propagation boundary — the method declares `throws ParseException`, so a malformed token propagates directly to the caller without an explicit node in the CFG. It is still a distinct independent path from the caller's perspective.

---

## Test Cases Mapped to Paths

### PATH-1: Valid, non-expired token

```
Input  : A well-formed HS512 JWT signed with the correct secretKey, 
         expiry = now + 1 hour
Expected: SignedJWT object returned (non-null)
```

### PATH-2: Invalid signature

```
Input  : A well-formed JWT signed with a DIFFERENT key
Expected: AppException with ErrorCode.TOKEN_INVALID
```

### PATH-3: Expired token

```
Input  : A well-formed HS512 JWT signed correctly,
         expiry = now - 1 minute (in the past)
Expected: AppException with ErrorCode.TOKEN_EXPIRED
```

### PATH-4: Malformed token string

```
Input  : "not.a.jwt.at.all.invalid"  OR  "garbage"
Expected: ParseException propagated (or RuntimeException wrapping it)
```

---

## Seed Bug Interaction

| Seed | Effect on CFG | Detected by Path |
|------|---------------|-----------------|
| SEED-001 (invert expiry check) | D2 condition inverts: expired tokens pass, valid tokens throw | PATH-1 and PATH-3 both fail |
