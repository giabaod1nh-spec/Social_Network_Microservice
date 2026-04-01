package com.chat.chat_service.service.impl;

import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.response.ChatMessageResponse;
import com.chat.chat_service.entity.ChatMessage;
import com.chat.chat_service.entity.ParticipantInfo;
import com.chat.chat_service.exception.AppException;
import com.chat.chat_service.exception.ErrorCode;
import com.chat.chat_service.mapper.ChatMapper;
import com.chat.chat_service.repository.ConservationRepository;
import com.chat.chat_service.repository.ChatMessageRepository;
import com.chat.chat_service.repository.httpclient.ProfileClient;
import com.chat.chat_service.service.IChatService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
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
    private  final ConservationRepository conversationRepository;
    ProfileClient profileClient;
    private final ChatMapper chatMessageMapper;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public ChatMessageResponse createChatMessage(ChatMessageRequest request) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        // Validate conversationId
        conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipants()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // Get UserInfo from ProfileService
        var userResponse = profileClient.getUserProfileById(userId);
        if (Objects.isNull(userResponse)) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        var userInfo = userResponse.getResult();

        // Build Chat message Info
        ChatMessage chatMessage = chatMessageMapper.convertChatMessageFromRequest(request);
        chatMessage.setSender(ParticipantInfo.builder()
                .userId(userInfo.getUserId())
                .userName(userInfo.getUserName())
                //.firstName(userInfo.getFirstName())
                //.lastName(userInfo.getLastName())
               // .avatar(userInfo.getAvatar())
                .build());
        chatMessage.setCreatedDate(Instant.now());

        // Create chat message
        chatMessage = chatMessageRepository.save(chatMessage);

        // convert to Response
        return toChatMessageResponse(chatMessage);
    }
    private ChatMessageResponse toChatMessageResponse(ChatMessage chatMessage) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        var chatMessageResponse = chatMessageMapper.convertFromChatMessage(chatMessage);

        chatMessageResponse.setMe(userId.equals(chatMessage.getSender().getUserId()));

        return chatMessageResponse;
    }

    @Override
    public List<ChatMessageResponse> getAllMessages(String conversationId) {
        // Validate conversationId
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipants()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        var messages = chatMessageRepository.findAllByConversationIdOrderByCreatedDateDesc(conversationId);

        return messages.stream().map(chatMessageMapper::convertFromChatMessage).toList();
    }
}
