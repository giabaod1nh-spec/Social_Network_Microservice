package com.chat.chat_service.service.impl;

import com.chat.chat_service.dto.request.IntrospectRequest;
import com.chat.chat_service.dto.response.IntrospectResponse;
import com.chat.chat_service.repository.httpclient.IdentityClient;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityService {
    final IdentityClient identityClient;

    public IntrospectResponse introspect(IntrospectRequest request) {
        try {
            var result =  identityClient.introspect(request).getResult();
            if (Objects.isNull(result)) {
                return IntrospectResponse.builder()
                        .valid(false)
                        .build();
            }
            return result;
        } catch (FeignException e) {
            log.error("Introspect failed: {}", e.getMessage(), e);
            return IntrospectResponse.builder()
                    .valid(false)
                    .build();
        }
    }
}
