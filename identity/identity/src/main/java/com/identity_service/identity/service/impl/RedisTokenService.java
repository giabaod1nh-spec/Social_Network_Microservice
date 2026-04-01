package com.identity_service.identity.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class RedisTokenService {
    StringRedisTemplate redisTemplate;
    public void blackListToken(String token , long timeToLive){

        redisTemplate.opsForValue().set(token , "logout_token" , timeToLive , TimeUnit.MILLISECONDS);
    }

    public boolean isTokenBlackListed(String token){
        return redisTemplate.hasKey(token);
    }

}
