package com.ig.PostService.payload.request;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PostRequest {
    private String userId;
    private String description;
    private LocalDateTime createAt;
    private Long liked;
}
