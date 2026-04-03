package com.story.ig.controller;

import com.story.ig.dto.UserStoriesDTO;
import com.story.ig.payload.request.HighlightStory;
import com.story.ig.service.StoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class StoryController {
    @Autowired
    StoryService storyService;

    @PostMapping("/post-story/{user-id}")
    public ResponseEntity<?> postStory(@PathVariable("user-id") String userId, @RequestParam("media") MultipartFile media,
                                       @RequestParam(value = "description", required = false) String description){
        UserStoriesDTO response = storyService.PostStory(userId, media, description);
        if(response == null){
            return ResponseEntity.status(404).body("UserID not found");
        }
        else{
            return ResponseEntity.ok().body(response);
        }
    }

    @GetMapping("/get-story-byId/{user-id}")
    public ResponseEntity<?> getStoriesByUserId(@PathVariable("user-id") String userId){
        UserStoriesDTO response = storyService.getStoriesByUserId(userId);
        if(response != null){
            return ResponseEntity.ok().body(response);
        }
        else{
            return ResponseEntity.status(404).body("user id not found");
        }
    }

    @PostMapping("api/clear")
    public void ClearRedis(){
        storyService.ClearRedis();
    }

    @PostMapping("/highlight")
    public ResponseEntity<?> makeHighlightStory(@RequestBody HighlightStory highlightStory){
        storyService.makeHighLightStory(highlightStory);
        return ResponseEntity.ok().body("Make story highlighted");
    }

    @GetMapping("/get-highlight/{user-id}")
    public ResponseEntity<?> getHighlightStory(@PathVariable("user-id") String userId){
        UserStoriesDTO response = storyService.getStoriesHighlight(userId);
        if(response == null){
            return ResponseEntity.status(404).body("User id not found");
        }
        else{
            return ResponseEntity.ok().body(response);
        }
    }
}
