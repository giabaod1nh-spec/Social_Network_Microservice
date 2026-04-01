package com.profile_service.profile.configuration;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class SecurityConfig {
    CustomJwtDecoder customJwtDecoder;

    @NonFinal
    private String[] public_endpoints = {"/info/getProfile/**" , "/info/internal/**"};

    @Bean
    public SecurityFilterChain securityFilterChain (HttpSecurity httpSecurity, JwtDecoder jwtDecoder) throws Exception {
                httpSecurity.csrf(csrf -> csrf.disable())
                .sessionManagement(s
                        -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth
                        -> auth.requestMatchers(HttpMethod.GET , public_endpoints)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST , public_endpoints)
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                );

        httpSecurity.oauth2ResourceServer(oauth2
                -> oauth2.jwt(jwt
                        -> jwt.decoder(customJwtDecoder))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
        );

        return httpSecurity.build();
    }
}
