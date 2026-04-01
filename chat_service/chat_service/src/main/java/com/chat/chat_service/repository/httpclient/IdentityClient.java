package com.chat.chat_service.repository.httpclient;

import com.chat.chat_service.dto.request.IntrospectRequest;
import com.chat.chat_service.dto.response.ApiResponse;
import com.chat.chat_service.dto.response.IntrospectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "identity-client", url = "${app.services.identity.url}")
public interface IdentityClient {
    @PostMapping("/auth/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request);
}
