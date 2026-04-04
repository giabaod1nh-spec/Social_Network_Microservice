package com.ig.PostService.mapper;

import com.ig.PostService.model.Comment;
import com.ig.PostService.model.Post;
import com.ig.PostService.payload.response.CommentResponse;
import com.ig.PostService.payload.response.PostResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Mapper {
    public PostResponse PostResponseMapper(Post post){

        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setLiked(post.getLiked());
        response.setDescription(post.getDescription());
        response.setCreateAt(post.getCreateAt());
        response.setUrlMedia(post.getUrlMedia());

        List<CommentResponse> commentResponses = post.getCommentList().stream().map((item) -> {
            CommentResponse commentResponse = new CommentResponse();
            commentResponse.setComment(item.getComment());
            commentResponse.setCommentAt(item.getCommentAt());
            commentResponse.setUserName(item.getUserName());
            return commentResponse;
        }).toList();
        response.setCommentList(commentResponses);
        return response;
    }
    public  CommentResponse CommentResponseMapper(Comment comment){
        CommentResponse commentResponse = new CommentResponse();
        commentResponse.setUserName(comment.getUserName());
        commentResponse.setComment(comment.getComment());
        commentResponse.setCommentAt(comment.getCommentAt());
        return commentResponse;
    }
}
