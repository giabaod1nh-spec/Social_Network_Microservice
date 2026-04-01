package com.identity_service.identity.service.impl;

import com.identity_service.identity.dto.request.AuthRequest;
import com.identity_service.identity.dto.request.IntroSpectRequest;
import com.identity_service.identity.dto.request.LogOutRequest;
import com.identity_service.identity.dto.request.RefreshTokenRequest;
import com.identity_service.identity.dto.response.AuthResponse;
import com.identity_service.identity.dto.response.IntroSpectResponse;
import com.identity_service.identity.exception.AppException;
import com.identity_service.identity.model.entity.EmailVerifyToken;
import com.identity_service.identity.model.entity.RefreshToken;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.exception.ErrorCode;
import com.identity_service.identity.model.enums.TokenType;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.repository.EmailVerifyTokenRepository;
import com.identity_service.identity.repository.RefreshTokenRepository;
import com.identity_service.identity.repository.UserRepository;
import com.identity_service.identity.service.IAuthService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Slf4j(topic = "AUTH_SERVICE")
public class AuthService implements IAuthService {
    HttpServletRequest httpServletRequest;
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    RefreshTokenRepository refreshTokenRepository;
    RedisTokenService redisTokenService;
    EmailVerifyTokenRepository emailVerifyTokenRepository;
    protected  static String secretKey = "bc4ab14dbbb049a77290ca0196a37d597d399a4fd5d8ccf2b831191d1995e84e";

    @Override
    @Transactional
    public AuthResponse authenticateUser(AuthRequest request) {

        User user = userRepository.findByUserName(request.getUserName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));

        //CHECK BANG CHUAN CUA SECURITY DUNG DAOAUTHENTICATIONPROVIDER
        boolean authenticate = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if(!authenticate){
            throw  new AppException(ErrorCode.AUTHENTICATED_FAILED);
        }
        //Check User status
        //if(!user.getEmailVerified()){
        //    throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
       // }
        //Neu authenticate return accessToken va refreshToken

        String accessToken = generateToken(user , TokenType.ACCESS ,
                new Date(Instant.now().plus(1 , ChronoUnit.HOURS).toEpochMilli()));

        Date sessionExpireTime = new Date(Instant.now().plus(7 , ChronoUnit.DAYS).toEpochMilli());

        String refreshToken = generateToken(user , TokenType.REFRESH ,
                new Date(Instant.now().plus(1 , ChronoUnit.DAYS).toEpochMilli()));

        RefreshToken refreshToken0 = RefreshToken.builder()
                .refreshToken(refreshToken)
                .users(user)
                .build();

        refreshTokenRepository.save(refreshToken0);

        return AuthResponse.builder()
                .authenticated(authenticate)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }



    @Override
    public IntroSpectResponse introspectToken(IntroSpectRequest request) {
        var token = request.getToken();
        boolean isValid = true;

        try {
            verifyToken(token);
        } catch (JOSEException | ParseException e) {
            isValid = false;
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        return IntroSpectResponse.builder()
                .isValid(isValid)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshTokenAfterTimeOut(RefreshTokenRequest request) throws ParseException, JOSEException {

        //Verify refreshToken
        SignedJWT signedJWT = verifyToken(request.getRefreshToken());

        String store = signedJWT.getJWTClaimsSet().getStringClaim("tokenType");

        //Check xem dung TokenType ko
        if(!"REFRESH".equals(store)){
            throw  new AppException(ErrorCode.TOKEN_TYPE_INVALID);
        }
        //Check xem token co exist trong DB
        RefreshToken oldToken = refreshTokenRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_NOT_FOUND));
        //Check xem user co xuat hien trong DB
        User user = userRepository.findByUserName(oldToken.getUsers().getUserName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXIST));
        //Neu valid cap lai new access va refreshToken

        String newAccessToken = generateToken(user , TokenType.ACCESS ,
                new Date(Instant.now().plus(1 , ChronoUnit.HOURS).toEpochMilli()));

        String newRefreshToken = generateToken(user, TokenType.REFRESH ,
                new Date(Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli()));

        //Revoke old refreshToken from DB
        refreshTokenRepository.delete(oldToken);

        return AuthResponse.builder()
                .authenticated(true)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Override
    @Transactional
    public void logOut(LogOutRequest logOutRequest) throws ParseException, JOSEException {

        //Xoa refreshToken trong DB
        refreshTokenRepository.deleteRefreshTokenByRefreshToken(logOutRequest.getRefreshToken());

        //Blacklist Access Token
        String header = httpServletRequest.getHeader("Authorization");

        if(header != null && header.startsWith("Bearer ")){
            String accessToken = header.substring(7);
            //Verify token
            SignedJWT signedJWT = verifyToken(accessToken);
            Date expireTime = signedJWT.getJWTClaimsSet().getExpirationTime();

            long ttl = expireTime.getTime() - System.currentTimeMillis();
            if(ttl > 0 ){
                redisTokenService.blackListToken(accessToken , ttl);
                log.info("Success save token in Redis");
            }else{
                log.info("Token has time out , no need to save in blacklist");
            }
        }else{
            log.info("No token in authorization header");
        }
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        EmailVerifyToken emailVerifyToken = emailVerifyTokenRepository.findByEmailVerifyToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.VERIFY_EMAIL_TOKEN_INVALID));

        //Neu xac thuc thanh cong gan User Status Active
        User user = emailVerifyToken.getUsers();
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        userRepository.save(user);
        //Verify success thi delete emailverifytoken

        emailVerifyTokenRepository.delete(emailVerifyToken);
    }


    private String generateToken(User user , TokenType tokenType , Date expiryTime) {
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUserId())
                .expirationTime(expiryTime)
                .issuer("baoxdev.com")
                .issueTime(new Date(Instant.now().toEpochMilli()))
                .claim("tokenType" , tokenType)
                .claim("userName" , user.getUserName())
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(jwsHeader , payload);

        try {
            JWSSigner jwsSigner = new MACSigner(secretKey.getBytes());
            jwsObject.sign(jwsSigner);
            return jwsObject.serialize();
        } catch (JOSEException e ) {
            log.error("Cannot create token" , e);
            throw new AppException(ErrorCode.CREATE_TOKEN_FAILED);
        }
    }

    protected SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier jwsVerifier = new MACVerifier(secretKey.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);

        var verified = signedJWT.verify(jwsVerifier);

        //Check xem token co valid ko
        if(!verified){
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        //Check xem token expired ko
        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        if(expiryTime.before(new Date(Instant.now().toEpochMilli()))){
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        return signedJWT;
    }
}
