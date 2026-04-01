package com.chat.chat_service.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomJwtDecoder jwtDecoder;
    private final String[] PUBLIC_ENDPOINT = {"auth/**" , "user/**"};

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeHttpRequests(request -> request.
                requestMatchers(HttpMethod.POST , PUBLIC_ENDPOINT)
                .permitAll()
                .requestMatchers(HttpMethod.GET , PUBLIC_ENDPOINT).permitAll()
                .anyRequest().authenticated()
        );

        //Tat config CSRF
        httpSecurity.csrf(httpSecurityCsrfConfigurer -> httpSecurityCsrfConfigurer.disable());

        //Server ko can luu session cho user
        httpSecurity.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        //Xác thực bearer token trong header khi user request
        httpSecurity.oauth2ResourceServer(oauth2
                -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
        );
        return httpSecurity.build();

    }

}
