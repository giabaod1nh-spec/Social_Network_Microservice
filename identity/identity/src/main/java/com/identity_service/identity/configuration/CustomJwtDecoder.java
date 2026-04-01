package com.identity_service.identity.configuration;

import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;


@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class    CustomJwtDecoder implements JwtDecoder {
    // RedisTokenService redisTokenService;
    protected static String secretKey = "bc4ab14dbbb049a77290ca0196a37d597d399a4fd5d8ccf2b831191d1995e84e";

    @Override
    public Jwt decode(String token) throws JwtException {
      //  if (redisTokenService.isTokenBlackListed(token)) throw new AppException(ErrorCode.TOKEN_REVOKED);
      // No need to double verify token
        try{
            SignedJWT signedJWT = SignedJWT.parse(token);

        return new Jwt(token ,
                signedJWT.getJWTClaimsSet().getIssueTime().toInstant() ,
                signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(),
                signedJWT.getHeader().toJSONObject(),
                signedJWT.getJWTClaimsSet().getClaims()
                );
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private JwtDecoder nimbusJwtDecoder(){
        SecretKey key = new SecretKeySpec(secretKey.getBytes() , "HS512");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS512).build();
    }

}
