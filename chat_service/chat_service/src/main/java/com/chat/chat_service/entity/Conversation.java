package com.chat.chat_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(collection = "conversation")
public class Conversation {
    @MongoId
    String id;

    String type; //GROUP , DIRECT

    @Indexed(unique = true)
    String participantHash;

    List<ParticipantInfo> participants;

    Instant createdDate;

    Instant modifiedDate;
}
