package com.chat.chat_service.mapper;

import com.chat.chat_service.dto.request.ChatMessageRequest;
import com.chat.chat_service.dto.response.ChatMessageResponse;
import com.chat.chat_service.entity.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMapper {

    public ChatMessageResponse convertFromChatMessage (ChatMessage chatMessage){


        return  ChatMessageResponse.builder()
                .conversationId(chatMessage.getConversationId())
                .message(chatMessage.getMessage())
                .sender(chatMessage.getSender())
                .createdDate(chatMessage.getCreatedDate())
                .build();
    }

    public ChatMessage convertChatMessageFromRequest(ChatMessageRequest request){

        return ChatMessage.builder()
                .conversationId(request.getConversationId())
                .message(request.getMessage())
                .build();
    }
}
