package com.identity_service.identity.service;

import com.identity_service.identity.service.impl.RedisTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource("/test.properties")
@ExtendWith(MockitoExtension.class)
class RedisTokenServiceTest {

    @InjectMocks
    private RedisTokenService redisTokenService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void blackListToken_storesValueWithTtlMilliseconds() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String token = "jwt-access-token";
        long ttlMs = 3600_000L;

        redisTokenService.blackListToken(token, ttlMs);

        verify(valueOperations).set(eq(token), eq("logout_token"), eq(ttlMs), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void isTokenBlackListed_whenKeyExists_returnsTrue() {
        String token = "some-token";
        when(redisTemplate.hasKey(token)).thenReturn(true);

        assertThat(redisTokenService.isTokenBlackListed(token)).isTrue();
        verify(redisTemplate).hasKey(token);
    }

    @Test
    void isTokenBlackListed_whenKeyMissing_returnsFalse() {
        String token = "missing";
        when(redisTemplate.hasKey(token)).thenReturn(false);

        assertThat(redisTokenService.isTokenBlackListed(token)).isFalse();
    }
}
