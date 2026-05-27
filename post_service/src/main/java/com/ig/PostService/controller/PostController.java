package com.ig.PostService.controller;

import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.request.UserReuest;
import com.ig.PostService.payload.response.ApiResponse;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
public class PostController {
    @Autowired
    private PostService postService;

    @PostMapping(value = "/create" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPost(@RequestPart("data") PostRequest request, @RequestPart("media") MultipartFile media){
        return ResponseEntity.ok().body(postService.CreateNewPost(request, media));
    }

    @GetMapping("/profile/{user-id}")
    public UserPostProfileResponse getPostInUserProfile(@PathVariable("user-id") String userId){
        return postService.GetPostInUserProfile(userId);
    }

    @DeleteMapping("/delete/{user-id}/{post-id}")
    public void deletePost(@PathVariable("user-id") String userId, @PathVariable("post-id") String postId){
        postService.DeletePost(userId, postId);
    }

    @PutMapping("/like/{post-id}")
    public void likePost(@PathVariable("post-id") String postId,
                         HttpServletRequest request){
        postService.LikePost(postId, request.getHeader("Authorization"));
    }

    @PutMapping("/unlike/{post-id}")
    public void unlikePost(@PathVariable("post-id") String postId,
                           HttpServletRequest request){
        postService.unlikePost(postId, request.getHeader("Authorization"));
    }

    @PutMapping("/comment/{post-id}")
    public ApiResponse commentPost(@PathVariable("post-id") String postId, @RequestBody CommentRequest request){
        postService.commentPost(postId, request);
        return new ApiResponse();
    }

    @PostMapping("/clear-cache")
    public void clearCache(){
        postService.clearCache();
    }


    @GetMapping("/get-post")
    public Set<PostResponse> getPost(HttpServletRequest request){
        return postService.getPost(request);
    }
}
