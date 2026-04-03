package com.story.ig.model;
import com.common_library.common.model.BaseAuditEntity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Generated;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Date;

@Document(collection = "stories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Story extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Field("user_id")
    private String userId;

    @Field("url_media")
    private String urlMedia;

    @Field("liked")
    private Long liked;

    @Field("description")
    private String description;
}
