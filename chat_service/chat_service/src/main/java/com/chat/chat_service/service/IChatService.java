package com.chat.chat_service.service;

import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.response.ChatMessageResponse;
import com.chat.chat_service.entity.ChatMessage;

import java.util.List;

public interface IChatService {
        ChatMessageResponse createChatMessage (ChatMessageRequest request);

    List<ChatMessageResponse> getAllMessages(String conversationId);
}
