package com.story.ig.dto;

import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
@Data
@NoArgsConstructor
public class StoryDTO {
    private String description;
    private String userId;
    private String urlMedia;
    private Long liked;
    private LocalDateTime createAt;
}
