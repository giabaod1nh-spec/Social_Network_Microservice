package com.ig.PostService.controller;

import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
public class PostController {
    @Autowired
    private PostService postService;

    @GetMapping("/get-post/{user-id}")
    public List<PostResponse> getPost(@PathVariable("user-id") String userId){
        return new ArrayList<>();
    }

    @PostMapping(value = "/create" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPost(@RequestPart("data") PostRequest request, @RequestPart("media") MultipartFile media){
        postService.CreateNewPost(request, media);
        return ResponseEntity.ok().body("ok");
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
    public void likePost(@PathVariable("post-id") String postId){
        postService.LikePost(postId);
    }

    @PutMapping("/unlike/{post-id}")
    public void unlikePost(@PathVariable("post-id") String postId){
        postService.unlikePost(postId);
    }

    @PutMapping("/comment/{post-id}")
    public void commentPost(@PathVariable("post-id") String postId, @RequestBody CommentRequest request){
        postService.commentPost(postId, request);
    }

    @PostMapping("/clear-cache")
    public void clearCache(){
        postService.clearCache();
    }
}
