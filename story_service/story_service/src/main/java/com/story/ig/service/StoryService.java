package com.story.ig.service;

import com.story.ig.config.R2Config;
import com.story.ig.dto.StoryDTO;
import com.story.ig.dto.UserStoriesDTO;
import com.story.ig.exception.UserNotFoundException;
import com.story.ig.model.Story;
import com.story.ig.model.UserStories;
import com.story.ig.payload.request.HighlightStory;
import com.story.ig.repo.UserStoriesRepo;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE , makeFinal = true)
public class StoryService{
        S3Client r2Client;
        R2Config r2Config;
        CacheManager cacheManager;
        UserStoriesRepo userStoriesRepo;

        @NonFinal
        @Value("${app.services.identity}")
        String identityUrl;


    public UserStoriesDTO PostStory(String userId, MultipartFile media, String description){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        try {
            LocalDateTime date = LocalDateTime.now();
            StoryDTO storyDTO = new StoryDTO();
            storyDTO.setUrlMedia(uploadMediaToS3(userId, media));
            storyDTO.setDescription(description);
            storyDTO.setCreateAt(date);
            storyDTO.setUserId(userId);
            storyDTO.setLiked(0l);

            Cache userStoriesCache = cacheManager.getCache("Stories");
            Cache.ValueWrapper userStoriesWrapper = userStoriesCache.get(userId);

            if(userStoriesWrapper == null) {
                UserStoriesDTO userStoriesDTO = new UserStoriesDTO();
                List<StoryDTO> listStories = new ArrayList<>();
                listStories.add(storyDTO);
                userStoriesDTO.setUserId(userId);
                userStoriesDTO.setStories(listStories);
                userStoriesCache.put(userId, userStoriesDTO);
                return userStoriesDTO;
            }
            else{
                UserStoriesDTO userStoriesDTO = (UserStoriesDTO) userStoriesWrapper.get();
                if(userStoriesDTO.getStories() == null || userStoriesDTO.getStories().isEmpty()){
                    List<StoryDTO> listStories = new ArrayList<>();
                    listStories.add(storyDTO);
                    userStoriesDTO.setUserId(userId);
                    userStoriesDTO.setStories(listStories);
                    userStoriesCache.put(userId, userStoriesDTO);
                    return userStoriesDTO;
                }
                else{
                    userStoriesDTO.getStories().add(storyDTO);
                    userStoriesCache.put(userId, userStoriesDTO);
                    return userStoriesDTO;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private String uploadMediaToS3(String userId,MultipartFile media) throws IOException {
        UUID uuid = UUID.randomUUID();
        String fileName = uuid.toString() + media.getName();
        String dirName = String.format("stories/%s/%s", userId, fileName);

        PutObjectRequest request = PutObjectRequest.builder().bucket(r2Config.getBucketName())
                .key(dirName)
                .contentType(media.getContentType())
                .contentLength(media.getSize())
                .build();
        r2Client.putObject(request, RequestBody.fromInputStream(media.getInputStream(), media.getSize()));
        return r2Config.getPublicUrl() + "/" + dirName;
    }

    public UserStoriesDTO getStoriesByUserId(String userId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Cache storyCache = cacheManager.getCache("Stories");
        Cache.ValueWrapper storyWrapper = storyCache.get(userId);
        if(storyWrapper != null) {
            UserStoriesDTO userStoriesDTO = (UserStoriesDTO) storyWrapper.get();
            List<StoryDTO> newStories = userStoriesDTO.getStories().stream().filter((item) -> item.getCreateAt().isBefore(item.getCreateAt().plusHours(24))).toList();
            userStoriesDTO.setStories(newStories);
            storyCache.put(userId, userStoriesDTO);
            return userStoriesDTO;
        }
        else{
            UserStoriesDTO response = new UserStoriesDTO();
            response.setUserId(userId);
            response.setStories(new ArrayList<>());
            return response;
        }
    }

    public UserStoriesDTO getStoriesHighlight(String userId){
        if(!CheckUserExisted(userId)){
            throw new UserNotFoundException(userId);
        }
        Optional<UserStories> userStoriesOptional = userStoriesRepo.findById(userId);
        if(userStoriesOptional.isPresent()){
            UserStories userStories = userStoriesOptional.get();
            UserStoriesDTO userStoriesDTO = new UserStoriesDTO();
            userStoriesDTO.setUserId(userId);
            userStoriesDTO.setStories(userStories.getListStories().stream().map((story -> {
                StoryDTO storyDTO = new StoryDTO();
                storyDTO.setUrlMedia(story.getUrlMedia());
                storyDTO.setLiked(story.getLiked());
                //storyDTO.setCreateAt(story.getCreateAt());
                storyDTO.setDescription(story.getDescription());
                storyDTO.setUserId(story.getUserId());
                return storyDTO;
            })).toList());
            return userStoriesDTO;
        }
        else{
            return null;
        }
    }

    public void ClearRedis(){
        Cache cacheUserStories = cacheManager.getCache("Stories");
        if(cacheUserStories != null){
            cacheUserStories.clear();
        }
    }

    public void makeHighLightStory(HighlightStory highlightStory) {
        if(!CheckUserExisted(highlightStory.getUserId())){
            throw new UserNotFoundException(highlightStory.getUserId());
        }
        else{
            Optional<UserStories> userStoriesOptional = userStoriesRepo.findById(highlightStory.getUserId());
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            UserStories userStories = new UserStories();

            if(userStoriesOptional.isPresent()){
                userStories = userStoriesOptional.get();
            }
            else{
                userStories.setListStories(new ArrayList<>());
            }

            UUID uuid = UUID.randomUUID();

            Story story = new Story();
            story.setId(highlightStory.getUserId() + uuid.toString());
            story.setUserId(highlightStory.getUserId());
            story.setUrlMedia(highlightStory.getUrlMedia());
            story.setLiked(highlightStory.getLike());
            story.setDescription(highlightStory.getDescription());
            //story.setCreateAt(highlightStory.getCreateAt());
            story.setLiked(0l);
            userStories.getListStories().add(story);
            userStories.setUserId(highlightStory.getUserId());
            userStoriesRepo.save(userStories);
        }
    }

    public boolean CheckUserExisted(String userId){
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(identityUrl + "/getById?userId=" + userId)).GET().build();
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


}
