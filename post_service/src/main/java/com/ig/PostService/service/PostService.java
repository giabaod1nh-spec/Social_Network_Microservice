package com.ig.PostService.service;

import com.ig.PostService.config.R2Config;
import com.ig.PostService.exception.UserNotFoundException;
import com.ig.PostService.mapper.Mapper;
import com.ig.PostService.model.Comment;
import com.ig.PostService.model.Post;
import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.response.CommentResponse;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.repo.CommentRepo;
import com.ig.PostService.repo.PostRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class PostService {
    private final String identityUrl = "http://localhost:8080/identity/user";

    @Autowired
    private S3Client r2Client;
    @Autowired
    private R2Config    r2Config;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PostRepo postRepo;
    @Autowired
    private CommentRepo commentRepo;
    @Autowired
    private Mapper mapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void CreateNewPost(PostRequest request, MultipartFile media) {
        if(!CheckUserExisted(request.getUserId())){
            throw new UserNotFoundException(request.getUserId());
        }

        UUID uuid = UUID.randomUUID();
        String idPost = request.getUserId() + media.getName() + uuid.toString();
        Post newPost = new Post();
        LocalDateTime dateCreate = LocalDateTime.now();
        newPost.setId(idPost);
        newPost.setCreateAt(dateCreate);
        newPost.setLiked(0l);
        newPost.setCommentList(new ArrayList<>());
        newPost.setUserId(request.getUserId());
        newPost.setDescription(request.getDescription());
        newPost.setUrlMedia(uploadMediaToS3(request.getUserId(), media));
        postRepo.save(newPost);
        if(checkRedisConnection()){
            PostResponse postResponse = mapper.PostResponseMapper(newPost);
            Cache postCache = cacheManager.getCache("Post");
            Cache.ValueWrapper postValueWrapper = postCache.get(request.getUserId());
            UserPostProfileResponse userPostProfileResponse = new UserPostProfileResponse();
            if(postValueWrapper != null){
                userPostProfileResponse = (UserPostProfileResponse) postValueWrapper.get();
            }
            userPostProfileResponse.getListUserPost().add(postResponse);
            postCache.put(request.getUserId(), userPostProfileResponse);
        }
        else{
            log.warn("Redis connection was interrupted");
        }
    }


    public UserPostProfileResponse GetPostInUserProfile(String userId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        UserPostProfileResponse userPostProfileResponse = new UserPostProfileResponse();
        List<PostResponse> listPostResponse = postRepo.getPostsByUserId(userId).stream().map((post) -> {
            PostResponse postResponse = mapper.PostResponseMapper(post);
            return  postResponse;
        }).toList();
        userPostProfileResponse.getListUserPost().addAll(listPostResponse);
        userPostProfileResponse.setUserId(userId);
        return userPostProfileResponse;
    }

    public void DeletePost(String userId, String postId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        if(checkRedisConnection()){
            Cache cache = cacheManager.getCache("Post");
            Cache.ValueWrapper valueWrapper = cache.get(userId);
            if(valueWrapper != null){
                UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) valueWrapper.get();
                userPostProfileResponse.getListUserPost().stream().filter((item) -> !item.getId().equals(postId));
                cache.put(userId, userPostProfileResponse);
            }
        }
        else{
            log.warn("Redis Connection Was Interrupted");
        }
        Post post = postRepo.getPostByIdAndUserId(postId, userId);
        postRepo.delete(post);
    }

    public void LikePost(String postId){
        Optional<Post> postOptional = postRepo.findById(postId);
        if(postOptional.isPresent()){
            Post post = postOptional.get();
            post.setLiked(post.getLiked()+1);
            postRepo.save(post);
            if(checkRedisConnection()){
                Cache cache = cacheManager.getCache("Post");
                Cache.ValueWrapper valueWrapper = cache.get(post.getUserId());
                if(valueWrapper != null){
                    UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) valueWrapper.get();
                    userPostProfileResponse.getListUserPost().stream().filter((item) -> item.getId().equals(postId))
                            .findFirst()
                            .ifPresent((item) -> item.setLiked(item.getLiked()+1));
                }
            }
            else{
                log.warn("Redis Connection Was Interrupted");
            }
        }
    }

    public void unlikePost(String postId){
        Optional<Post> postOptional = postRepo.findById(postId);
        if(postOptional.isPresent()){
            Post post = postOptional.get();
            if(post.getLiked() > 0){
                post.setLiked(post.getLiked()-1);
                postRepo.save(post);

                if(checkRedisConnection()){
                    Cache cache = cacheManager.getCache("Post");
                    Cache.ValueWrapper valueWrapper = cache.get(post.getUserId());
                    if(valueWrapper != null){
                        UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) valueWrapper.get();
                        userPostProfileResponse.getListUserPost().stream().filter((item) -> item.getId().equals(postId))
                                .findFirst()
                                .ifPresent((item) -> {
                                    if(item.getLiked() > 0){
                                        item.setLiked(item.getLiked() -1);
                                    }
                                });
                    }
                }
            }

        }
    }

    public void commentPost(String postId, CommentRequest request){
        if(!CheckUserExisted(request.getUserId())){
            throw new UserNotFoundException(request.getUserId());
        }
        Optional<Post> postOptional = postRepo.findById(postId);
        String userNameComment = getUserNameById(request.getUserId());
        if(postOptional.isPresent() && !userNameComment.isEmpty()){
            Post post = postOptional.get();
            LocalDateTime dateComment = LocalDateTime.now();
            Comment newComment = new Comment();
            newComment.setComment(request.getComment());
            newComment.setCommentAt(dateComment);
            newComment.setPost(post);
            newComment.setUserName(userNameComment);
            commentRepo.save(newComment);
            CommentResponse commentResponse = mapper.CommentResponseMapper(newComment);
            Cache cache = cacheManager.getCache("Post");
            Cache.ValueWrapper postValueWrapper = cache.get(post.getUserId());
            if(postValueWrapper != null) {
                UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) postValueWrapper.get();
                userPostProfileResponse.getListUserPost().stream()
                        .filter(item -> item.getId().equals(postId))
                        .findFirst()
                        .ifPresent(item -> item.getCommentList().add(commentResponse));
                cache.put(post.getUserId(), userPostProfileResponse);
            }
        }
    }

    private String uploadMediaToS3(String userId, MultipartFile media)  {
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString() + media.getName();
        String dirName = String.format("post/%s/%s", userId, fileName);

        PutObjectRequest request = PutObjectRequest.builder().bucket(r2Config.getBucketName())
                .key(dirName)
                .contentType(media.getContentType())
                .contentLength(media.getSize())
                .build();
        try {
            r2Client.putObject(request, RequestBody.fromInputStream(media.getInputStream(), media.getSize()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return r2Config.getPublicUrl() + "/" + dirName;
    }

    private boolean CheckUserExisted(String userId){
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(identityUrl + "/getById/" + userId)).GET().build();
        String codeIdentityService = "1002";
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper mapper = new ObjectMapper();
            Map dataRes = mapper.readValue(response.body(), Map.class);
            codeIdentityService = dataRes.get("code").toString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return codeIdentityService.equals("1000");
    }

    private String getUserNameById(String userId){
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(identityUrl + "/getById/" + userId)).GET().build();
        String userName = "";
        try{
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            ObjectMapper mapper = new ObjectMapper();
            Map datares = mapper.readValue(response.body(), Map.class);
            Map<String, Object> userInfo = (Map<String, Object>) datares.get("result");
            userName = userInfo.get("userName").toString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return userName;
    }

    public boolean checkRedisConnection(){
        JedisPool pool = new JedisPool();
        try{
            Jedis jesis = pool.getResource();
            return true;
        } catch (JedisConnectionException e){
            return  false;
        }
    }

    public void clearCache(){
        Cache cache = cacheManager.getCache("Post");
        cache.clear();
    }
}
