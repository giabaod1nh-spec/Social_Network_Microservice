package com.chat.chat_service.controller;

import com.chat.chat_service.configuration.SocketAuthContext;
import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.request.IntrospectRequest;
import com.chat.chat_service.entity.WebSocketSession;
import com.chat.chat_service.service.impl.ChatService;
import com.chat.chat_service.service.impl.IdentityService;
import com.chat.chat_service.service.impl.WebSocketSessionService;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
@Component
public class SocketHandler {
    SocketIOServer server;
    IdentityService identityService;
    WebSocketSessionService webSocketSessionService;
    ChatService chatService;

    @OnConnect
    public void clientConnected(SocketIOClient client){
        //Get token from request params
        String token = client.getHandshakeData().getSingleUrlParam("token");

        //Verify token
        var introspect = identityService.introspect(IntrospectRequest.builder().token(token).build());

        //Check requirement
        if(introspect.isValid()){
            log.info("Client connnected: {}" , client.getSessionId());

            //Persist webSocketSession
            WebSocketSession webSocketSession = WebSocketSession.builder()
                    .socketSessionId(client.getSessionId().toString())
                    .userId(introspect.getUserId())
                    .createdAt(Instant.now())
                    .build();

            webSocketSession = webSocketSessionService.create(webSocketSession);

            log.info("Web socket session created with id: {}" , webSocketSession.getId());
        }else{
            log.info("Authentication failed: {}" , client.getSessionId());
            client.disconnect();
        }
    }

    @OnDisconnect
    public void clientDisconnected(SocketIOClient client) {
        log.info("Client disConnected: {}", client.getSessionId());
        webSocketSessionService.deleteSession(client.getSessionId().toString());
    }

    @PostConstruct
    public void startServer() {
        server.start();
        server.addListeners(this);
        log.info("Socket server started");
    }

    @PreDestroy
    public void stopServer() {
        server.stop();
        log.info("Socket server stopped");
    }


    @OnEvent("send_message")
    public void onMessage(
            SocketIOClient client,
            ChatMessageRequest request
    ) {
        try {

            String token = client.get("token");

            SocketAuthContext.setToken(token);

            chatService.createChatMessage(request);

        } finally {
            SocketAuthContext.clear();
        }
    }

}
