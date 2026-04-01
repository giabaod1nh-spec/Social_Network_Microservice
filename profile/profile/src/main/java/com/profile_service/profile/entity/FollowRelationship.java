package com.profile_service.profile.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.neo4j.core.schema.*;

import java.time.LocalDateTime;
import java.util.Set;

@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE )
public class FollowRelationship {
    @Id
    @GeneratedValue
    Long id;

    LocalDateTime createdAt;

    Boolean notificationEnabled;

    @TargetNode
    UserProfile target;

    @Relationship(type = "FOLLOW" , direction = Relationship.Direction.OUTGOING)
    Set<FollowRelationship> following;
}
