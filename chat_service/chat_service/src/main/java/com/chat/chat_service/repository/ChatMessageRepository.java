package com.chat.chat_service.repository;

import com.chat.chat_service.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage , String> {

    List<ChatMessage> findAllByConversationIdOrderByCreatedDateDesc(String conversationId);
}
