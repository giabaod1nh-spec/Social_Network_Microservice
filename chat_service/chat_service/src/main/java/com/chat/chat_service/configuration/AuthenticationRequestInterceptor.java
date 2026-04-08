package com.chat.chat_service.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class AuthenticationRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {

        String token = SocketAuthContext.getToken();

        if (token != null) {

            template.header(
                    "Authorization",
                    token
            );

            return;
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attrs != null) {

            String header =
                    attrs.getRequest()
                            .getHeader("Authorization");

            if (header != null) {
                template.header(
                        "Authorization",
                        header
                );
            }
        }
    }
}
