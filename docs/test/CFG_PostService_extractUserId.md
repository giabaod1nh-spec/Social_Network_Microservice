# CFG — `PostService.extractUserIdFromAuthorizationHeader(String header)`
## McCabe Cyclomatic Complexity Analysis

**Service:** post_service  
**Class:** `com.ig.PostService.service.PostService`  
**Method:** `private String extractUserIdFromAuthorizationHeader(String authorizationHeader)`

---

## Source Code (annotated with node labels)

```java
private String extractUserIdFromAuthorizationHeader(String authorizationHeader) {

    // ─── DECISION D1 ──────────────────────────────────────────────────────
    if (authorizationHeader == null || authorizationHeader.isBlank()) {   // D1
        // NODE 2 (D1 = TRUE)
        throw new RuntimeException("Missing Authorization header");
    }

    // NODE 3 (D1 = FALSE) ─────────────────────────────────────────────────
    // DECISION D2: strip "Bearer " prefix
    String token = authorizationHeader.startsWith("Bearer ")              // D2
            ? authorizationHeader.substring(7).trim()    // NODE 4 (D2=T)
            : authorizationHeader.trim();                // NODE 5 (D2=F)

    // NODE 6 (after D2) ───────────────────────────────────────────────────
    try {
        String[] tokenParts = token.split("\\.");

        // DECISION D3 ──────────────────────────────────────────────────────
        if (tokenParts.length < 2) {                                       // D3
            // NODE 7 (D3 = TRUE)
            throw new RuntimeException("Invalid JWT format");
        }

        // NODE 8 (D3 = FALSE) ─────────────────────────────────────────────
        String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]));
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
        Object sub = payload.get("sub");

        // DECISION D4 ──────────────────────────────────────────────────────
        if (sub == null || sub.toString().isBlank()) {                     // D4
            // NODE 9 (D4 = TRUE)
            throw new RuntimeException("Token does not contain user id");
        }

        // NODE 10 (D4 = FALSE) ────────────────────────────────────────────
        return sub.toString();

    } catch (IllegalArgumentException | IOException e) {    // EX-HANDLER
        // NODE 11
        throw new RuntimeException("Invalid Authorization token", e);
    }
}
```

---

## Control Flow Graph (ASCII)

```
         ┌─────────────┐
         │   START/N1  │  (entry point)
         └──────┬──────┘
                │
           [D1: null or blank?]
          ┌─────┴──────┐
       T  │            │ F
          ▼            ▼
    ┌──────────┐  ┌──────────┐
    │  NODE 2  │  │  NODE 3  │  (proceed)
    │  throw   │  └────┬─────┘
    │ MISSING  │       │
    └────┬─────┘  [D2: startsWith "Bearer "?]
         │       ┌──────┴──────┐
         │    T  │             │ F
         │       ▼             ▼
         │  ┌────────┐   ┌────────┐
         │  │ NODE 4 │   │ NODE 5 │
         │  │substring│  │ trim() │
         │  └────┬───┘   └────┬───┘
         │       └─────┬──────┘
         │          ┌──┴───┐
         │          │NODE 6│  (split token)
         │          └──┬───┘
         │        [D3: parts.length < 2?]
         │       ┌──────┴──────┐
         │    T  │             │ F
         │       ▼             ▼
         │  ┌────────┐   ┌────────┐
         │  │ NODE 7 │   │ NODE 8 │  (decode payload, read sub)
         │  │ throw  │   └────┬───┘
         │  │INVALID │   [D4: sub null or blank?]
         │  │FORMAT  │  ┌──────┴──────┐
         │  └────┬───┘  T│            │F
         │       │       ▼            ▼
         │       │  ┌────────┐  ┌────────┐
         │       │  │ NODE 9 │  │NODE 10 │
         │       │  │ throw  │  │return  │
         │       │  │NO SUBID│  │sub.str │
         │       │  └────┬───┘  └────┬───┘
         │       │       │           │
         │       │   ┌───┴──┐        │
         │       │   │NODE11│ (catch)│
         │       │   │throw │        │
         │       │   └──┬───┘        │
         └───────┴───────┴───────────┘
                         │
                      ┌──┴──┐
                      │ END │
                      └─────┘
```

---

## McCabe Complexity Calculation

| Metric | Count |
|--------|-------|
| **N** — nodes | 11 (N1–N11) |
| **E** — edges | N1→N2, N1→N3, N2→END, N3→N4, N3→N5, N4→N6, N5→N6, N6→N7, N6→N8, N7→END, N8→N9, N8→N10, N9→N11, N10→END, N11→END = **15** |
| **P** — connected components | 1 |
| **V(G) = E − N + 2P** | 15 − 11 + 2 = **6** |

> **V(G) = 6** → minimum **6 independent test paths**.  
> V(G) < 10: complexity is **acceptable**.

---

## Independent Paths

| Path ID | Node Sequence | Description | Test Method |
|---------|---------------|-------------|-------------|
| PATH-1 | N1→N2→END | null / blank header → `RuntimeException("Missing Authorization header")` | `should_throw_when_authorizationHeaderIsNull` |
| PATH-2 | N1→N3→N4→N6→N10→END | Valid Bearer JWT with `sub` present | `should_returnUserId_when_validBearerToken` |
| PATH-3 | N1→N3→N5→N6→N10→END | Raw token (no "Bearer " prefix) with valid `sub` | `should_returnUserId_when_tokenWithoutBearerPrefix` |
| PATH-4 | N1→N3→N4→N6→N7→END | Token has < 2 parts (not a valid JWT structure) | `should_throw_when_jwtHasFewerThanTwoParts` |
| PATH-5 | N1→N3→N4→N6→N8→N9→END | JWT decoded but `sub` claim is null or blank | `should_throw_when_subClaimIsMissing` |
| PATH-6 | N1→N3→N4→N6→N8→N11→END | Base64 decode fails (bad padding / illegal chars) → catch block | `should_throw_when_base64PayloadIsInvalid` |

---

## Seed Bug Interaction

| Seed | Effect on CFG | Detected by Path |
|------|---------------|-----------------|
| SEED-005 (`tokenParts[0]` instead of `[1]`) | Decodes JWT header (base64) instead of payload; `sub` will be null | PATH-2 fails — returns userId=null or throws |
