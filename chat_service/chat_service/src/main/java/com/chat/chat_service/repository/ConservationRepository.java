package com.chat.chat_service.repository;

import com.chat.chat_service.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConservationRepository extends MongoRepository<Conversation, String> {
    @Query("{'participants.userId' : ?0}")
    List<Conversation> findAllByParticipantIdsContains(String userId);
}
