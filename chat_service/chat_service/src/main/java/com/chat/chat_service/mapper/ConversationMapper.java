package com.chat.chat_service.mapper;

import com.chat.chat_service.dto.response.ConversationResponse;
import com.chat.chat_service.entity.Conversation;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

    public ConversationResponse convertConversationToResponse(Conversation conversation){

        return  ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType())
                .participantsHash(conversation.getParticipantHash())
                .participants(conversation.getParticipants())
                .createdDate(conversation.getCreatedDate())
                .modifiedDate(conversation.getModifiedDate())
                .build();
    }
}
