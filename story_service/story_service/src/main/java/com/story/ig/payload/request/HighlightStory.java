package com.story.ig.payload.request;

import com.common_library.common.model.BaseAuditEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class HighlightStory extends BaseAuditEntity {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("url_media")
    private String urlMedia;

    @JsonProperty("description")
    private String description;

    @JsonProperty("liked")
    private Long like;

}
