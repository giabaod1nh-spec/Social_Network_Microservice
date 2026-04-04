package com.ig.PostService.payload.response;

import com.ig.PostService.model.Comment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PostResponse {
    private String id;
    private String userId;
    private String description;
    private Long liked;
    private LocalDateTime createAt;
    private String urlMedia;
    private List<CommentResponse> commentList;
}
