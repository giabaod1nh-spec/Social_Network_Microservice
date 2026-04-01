package com.chat.chat_service.controller;

import com.chat.chat_service.service.impl.IdentityService;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Component
public class SocketHandler {
    SocketIOServer server;
    IdentityService identityService;
}
