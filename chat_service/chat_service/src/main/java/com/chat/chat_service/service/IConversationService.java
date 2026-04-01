package com.chat.chat_service.service;

import com.chat.chat_service.dto.request.ConversationRequest;
import com.chat.chat_service.dto.response.ConversationResponse;

import java.util.List;

public interface IConversationService {
    ConversationResponse createConversation (ConversationRequest request);
    List<ConversationResponse> myConversation ();
}
