package com.ig.PostService.service;

import com.ig.PostService.config.R2Config;
import com.ig.PostService.exception.UserNotFoundException;
import com.ig.PostService.mapper.Mapper;
import com.ig.PostService.model.Comment;
import com.ig.PostService.model.Post;
import com.ig.PostService.model.PostLike;
import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.request.UserReuest;
import com.ig.PostService.payload.response.CommentResponse;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.repo.CommentRepo;
import com.ig.PostService.repo.PostLikeRepo;
import com.ig.PostService.repo.PostRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
public class PostService {
    private final String identityUrl = "http://localhost:8080/identity/user";
    private final String profileUrl = "http://localhost:8081/profile/";
    private static final int HTTP_RETRY_MAX = 3;
    @Autowired
    private S3Client r2Client;
    @Autowired
    private R2Config r2Config;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PostRepo postRepo;
    @Autowired
    private CommentRepo commentRepo;
    @Autowired
    private PostLikeRepo postLikeRepo;
    @Autowired
    private Mapper mapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private final String urlR2 = "https://pub-bd5ab7734dda491c8c8e7f89705ed9c2.r2.dev";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public PostResponse CreateNewPost(PostRequest request, MultipartFile media) {
        Map<String, Object> userInfo = getUserById(request.getUserId());
        String userName = valueAsString(userInfo.get("userName"));
        Map<String, Object> profileInfo = getProfileById(request.getUserId());
        UUID uuid = UUID.randomUUID();
        String idPost = request.getUserId() + media.getName() + uuid.toString();
        Post newPost = new Post();
        LocalDateTime dateCreate = LocalDateTime.now();
        newPost.setUserName(userName);
        newPost.setId(idPost);
        newPost.setCreateAt(dateCreate);
        newPost.setLiked(0l);
        newPost.setCommentList(new ArrayList<>());
        newPost.setUserId(request.getUserId());
        newPost.setDescription(request.getDescription());
        newPost.setFirstName(valueAsString(profileInfo.get("firstName")));
        newPost.setLastName(valueAsString(profileInfo.get("lastName")));
        newPost.setAvatarUrl(valueAsString(profileInfo.get("avatar")));
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
        return mapper.PostResponseMapper(newPost);
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

    public void LikePost(String postId, String authorizationHeader){
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Optional<Post> postOptional = postRepo.findById(postId);
        if(postOptional.isPresent()){
            if (postLikeRepo.existsByPostIdAndUserId(postId, userId)) {
                return;
            }
            Post post = postOptional.get();
            PostLike postLike = new PostLike();
            postLike.setPostId(postId);
            postLike.setUserId(userId);
            postLike.setCreatedAt(LocalDateTime.now());
            postLikeRepo.save(postLike);
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

    @Transactional
    public void unlikePost(String postId, String authorizationHeader){
        String userId = extractUserIdFromAuthorizationHeader(authorizationHeader);
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Optional<Post> postOptional = postRepo.findById(postId);
        if(postOptional.isPresent()){
            if (!postLikeRepo.existsByPostIdAndUserId(postId, userId)) {
                return;
            }
            Post post = postOptional.get();
            postLikeRepo.deleteByPostIdAndUserId(postId, userId);
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
        Map<String, Object> userInfor = getProfileById(request.getUserId());
        if(postOptional.isPresent() && !userNameComment.isEmpty()){
            Post post = postOptional.get();
            LocalDateTime dateComment = LocalDateTime.now();
            Comment newComment = new Comment();
            newComment.setComment(request.getComment());
            newComment.setLastName(valueAsString(userInfor.get("lastName")));
            newComment.setFirstName(valueAsString(userInfor.get("firstName")));
            newComment.setAvatarUrl(valueAsString(userInfor.get("avatar")));
            newComment.setCommentAt(dateComment);
            newComment.setLiked(0l);
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

    public Set<PostResponse> getPost(HttpServletRequest request){
        String authHeader = request.getHeader("Authorization");
        String userId = extractUserIdFromAuthorizationHeader(authHeader);
        List<String> userIdFollwed = getUserIdFollowed(authHeader);
        Set<PostResponse> response = new HashSet<>();
        if(checkRedisConnection()){
            Cache cache = cacheManager.getCache("Post");
            for(String followedUserId : userIdFollwed){
                Cache.ValueWrapper cacheUserProfileResponse = cache.get(followedUserId);
                if(cacheUserProfileResponse != null){
                    UserPostProfileResponse userPostProfileResponse = (UserPostProfileResponse) cacheUserProfileResponse.get();
                    assert userPostProfileResponse != null;
                    response.addAll(userPostProfileResponse.getListUserPost());
                }
                List<PostResponse> post = postRepo.getPostsByUserId(followedUserId).stream().map((item) -> {
                    return mapper.PostResponseMapper(item);
                }).toList();
                response.addAll(post);
            }
        }
        if(response.isEmpty()){
            List<PostResponse> post = postRepo.findAll().stream().map((item) -> {
                return mapper.PostResponseMapper(item);
            }).toList();
            response.addAll(post);
        }
        if (userId != null && !userId.isBlank() && !response.isEmpty()) {
            Set<String> postIds = response.stream()
                    .map(PostResponse::getId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            if (!postIds.isEmpty()) {
                Set<String> likedPostIds = postLikeRepo.findLikedPostIdsByUserIdAndPostIds(userId, postIds);
                response.forEach(item -> item.setLikedByUser(likedPostIds.contains(item.getId())));
            }
        }
        return  response;
    }

    private String extractUserIdFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new RuntimeException("Missing Authorization header");
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7).trim()
                : authorizationHeader.trim();
        try {
            String[] tokenParts = token.split("\\.");
            if (tokenParts.length < 2) {
                throw new RuntimeException("Invalid JWT format");
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]));
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(payloadJson, Map.class);
            Object sub = payload.get("sub");
            if (sub == null || sub.toString().isBlank()) {
                throw new RuntimeException("Token does not contain user id");
            }
            return sub.toString();
        } catch (IllegalArgumentException | IOException e) {
            throw new RuntimeException("Invalid Authorization token", e);
        }
    }

    public List<String> getUserIdFollowed(String authorizationHeader){
        List<String> listUserId = new ArrayList<>();
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            log.warn("Missing Authorization header when calling profile service");
            return listUserId;
        }

        String authHeader = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader
                : "Bearer " + authorizationHeader.trim();
        try{
            HttpResponse<String> response = sendGetWithRetry(profileUrl + "getFollowed", authHeader);
            ObjectMapper mapper = new ObjectMapper();
            if (response.statusCode() != 200) {
                log.warn("Profile service returned non-200 status: {}", response.statusCode());
                return listUserId;
            }

            Map<String, Object> datares = mapper.readValue(response.body(), Map.class);

            Object resultObj = datares.get("result");
            if (!(resultObj instanceof List<?> result)) {
                log.warn("Profile service returned empty or invalid result: {}", response.body());
                return listUserId;
            }

            for (Object item : result) {
                if (item instanceof Map<?, ?> user) {
                    Object userId = user.get("userId");
                    if (userId instanceof String userIdStr) {
                        listUserId.add(userIdStr);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return listUserId;
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
        return  urlR2 + "/" + dirName;
    }

    private boolean CheckUserExisted(String userId){
        String codeIdentityService = "1002";
        try {
            HttpResponse<String> response = sendGetWithRetry(identityUrl + "/getById?userId=" + userId, null);
            ObjectMapper mapper = new ObjectMapper();
            Map dataRes = mapper.readValue(response.body(), Map.class);
            codeIdentityService = dataRes.get("code").toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return codeIdentityService.equals("1000");
    }

    private Map<String, Object> getProfileById(String userId){
        try {
            HttpResponse<String> response = sendGetWithRetry(profileUrl + "info/internal/profile/" + userId, null);
            if (response.statusCode() != 200) {
                throw new RuntimeException("Cannot get profile for userId: " + userId);
            }
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dataRes = mapper.readValue(response.body(), Map.class);
            Object result = dataRes.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Profile response format is invalid");
            }
            return (Map<String, Object>) resultMap;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }



    private String getUserNameById(String userId){
        String userName = "";
        try{
            HttpResponse<String> response = sendGetWithRetry(identityUrl + "/getById?userId=" + userId, null);
            ObjectMapper mapper = new ObjectMapper();
            Map datares = mapper.readValue(response.body(), Map.class);
            Map<String, Object> userInfo = (Map<String, Object>) datares.get("result");
            userName = userInfo.get("userName").toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return userName;
    }

    private Map<String, Object> getUserById(String userId){
        try {
            HttpResponse<String> response = sendGetWithRetry(identityUrl + "/getById?userId=" + userId, null);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> dataRes = mapper.readValue(response.body(), Map.class);
            if (!"1000".equals(String.valueOf(dataRes.get("code")))) {
                throw new UserNotFoundException(userId);
            }
            Object result = dataRes.get("result");
            if (!(result instanceof Map<?, ?> resultMap)) {
                throw new RuntimeException("Identity response format is invalid");
            }
            return (Map<String, Object>) resultMap;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse<String> sendGetWithRetry(String url, String authorizationHeader) {
        for (int attempt = 1; attempt <= HTTP_RETRY_MAX; attempt++) {
            try {
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .GET();
                if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                    requestBuilder.header("Authorization", authorizationHeader);
                }
                return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while calling downstream: " + url, e);
            } catch (IOException e) {
                boolean retryable = isRetryableConnectionError(e);
                if (retryable && attempt < HTTP_RETRY_MAX) {
                    log.warn("Retry {}/{} for url {} due to {}", attempt, HTTP_RETRY_MAX, url, e.getClass().getSimpleName());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw new RuntimeException("Cannot connect to downstream url: " + url, e);
            }
        }
        throw new RuntimeException("Cannot connect to downstream url: " + url);
    }

    private boolean isRetryableConnectionError(IOException e) {
        if (e instanceof ConnectException) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause instanceof ClosedChannelException;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(150L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
