package com.chat.chat_service.service.impl;

import com.chat.chat_service.configuration.SocketAuthContext;
import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.request.NotificationMobileRequest;
import com.chat.chat_service.dto.response.ChatMessageResponse;
import com.chat.chat_service.entity.ChatMessage;
import com.chat.chat_service.entity.ParticipantInfo;
import com.chat.chat_service.entity.WebSocketSession;
import com.chat.chat_service.exception.AppException;
import com.chat.chat_service.exception.ErrorCode;
import com.chat.chat_service.mapper.ChatMapper;
import com.chat.chat_service.repository.ConservationRepository;
import com.chat.chat_service.repository.ChatMessageRepository;
import com.chat.chat_service.repository.WebSocketSessionRepository;
import com.chat.chat_service.repository.httpclient.NotificationClient;
import com.chat.chat_service.repository.httpclient.ProfileClient;
import com.chat.chat_service.service.IChatService;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService  implements IChatService {
    private final ConservationRepository conversationRepository;
    private final ProfileClient profileClient;
    private final NotificationClient notificationClient;
    private final ChatMapper chatMessageMapper;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketSessionRepository webSocketSessionRepository;
    private final SocketIOServer socketIOServer;

    /**
     * Resolves the current user's ID from either:
     *  - SocketAuthContext (when called from a Socket.IO event thread), or
     *  - Spring SecurityContextHolder (when called from an HTTP REST request).
     */
    private String resolveUserId() {
        String socketToken = SocketAuthContext.getToken();
        if (socketToken != null) {
            // Socket.IO path: parse sub claim directly from the JWT
            try {
                String payload = socketToken.startsWith("Bearer ")
                        ? socketToken.substring(7) : socketToken;
                String[] parts = payload.split("\\.");
                if (parts.length >= 2) {
                    String json = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    com.fasterxml.jackson.databind.ObjectMapper om =
                            new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> claims =
                            om.readValue(json, java.util.Map.class);
                    Object sub = claims.get("sub");
                    if (sub != null) return sub.toString();
                }
            } catch (Exception e) {
                log.error("Failed to parse userId from socket token", e);
            }
        }
        // HTTP REST path
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Override
    public ChatMessageResponse createChatMessage(ChatMessageRequest request) {
        String userId = resolveUserId();

        // Validate sender is a participant of the conversation
        var conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        conversation.getParticipants()
                .stream()
                .filter(p -> userId.equals(p.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // Find receiver (the other participant)
        ParticipantInfo receiver = conversation.getParticipants()
                .stream()
                .filter(p -> !userId.equals(p.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // Get sender profile from ProfileService
        var userResponse = profileClient.getUserProfileById(userId);
        if (Objects.isNull(userResponse)) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        var userInfo = userResponse.getResult();

        // Build and persist ChatMessage
        ChatMessage chatMessage = chatMessageMapper.convertChatMessageFromRequest(request);
        chatMessage.setSender(ParticipantInfo.builder()
                .userId(userInfo.getUserId())
                .userName(userInfo.getUserName())
                .avatar(userInfo.getAvatar())
                .build());
        chatMessage.setCreatedDate(Instant.now());
        chatMessage = chatMessageRepository.save(chatMessage);

        // Build response once so we reuse it
        ChatMessageResponse response = buildChatMessageResponse(chatMessage, userId);

        // ── Real-time delivery ──────────────────────────────────────────────
        // Push message to every active Socket.IO session of the receiver
        List<WebSocketSession> receiverSessions =
                webSocketSessionRepository.findAllByUserIdIn(List.of(receiver.getUserId()));
        for (WebSocketSession session : receiverSessions) {
            try {
                java.util.UUID sid = java.util.UUID.fromString(session.getSocketSessionId());
                com.corundumstudio.socketio.SocketIOClient receiverClient =
                        socketIOServer.getClient(sid);
                if (receiverClient != null && receiverClient.isChannelOpen()) {
                    receiverClient.sendEvent("receive_message", response);
                }
            } catch (Exception e) {
                log.warn("Could not deliver message to session {}: {}",
                        session.getSocketSessionId(), e.getMessage());
            }
        }
        // ───────────────────────────────────────────────────────────────────

        // Push mobile notification
        notificationClient.sendMobileNotification(NotificationMobileRequest.builder()
                .userId(receiver.getUserId())
                .tittle("New message from: " + userInfo.getUserName())
                .body(chatMessage.getMessage())
                .build());

        return response;
    }

    private ChatMessageResponse buildChatMessageResponse(ChatMessage chatMessage, String currentUserId) {
        var resp = chatMessageMapper.convertFromChatMessage(chatMessage);
        resp.setMe(currentUserId.equals(chatMessage.getSender().getUserId()));
        return resp;
    }

    @Override
    public List<ChatMessageResponse> getAllMessages(String conversationId) {
        String userId = resolveUserId();

        // Validate the requester is a participant
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipants()
                .stream()
                .filter(p -> userId.equals(p.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        return chatMessageRepository
                .findAllByConversationIdOrderByCreatedDateDesc(conversationId)
                .stream()
                .map(m -> buildChatMessageResponse(m, userId))
                .toList();
    }
}
