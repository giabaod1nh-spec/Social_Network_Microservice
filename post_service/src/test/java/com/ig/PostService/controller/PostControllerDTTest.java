package com.ig.PostService.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ig.PostService.exception.UserNotFoundException;
import com.ig.PostService.payload.request.CommentRequest;
import com.ig.PostService.payload.request.PostRequest;
import com.ig.PostService.payload.response.ApiResponse;
import com.ig.PostService.payload.response.PostResponse;
import com.ig.PostService.payload.response.UserPostProfileResponse;
import com.ig.PostService.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B1 — DECISION TABLE for PostController endpoints
 *
 * Decision table targets: POST /create, GET /profile/{user-id}, DELETE /delete/{uid}/{pid}
 *
 * Table for GET /profile/{user-id}:
 *   C1: User exists in identity service
 *   C2: User has posts
 *
 *   Col-1: C1=F → UserNotFoundException (500 or custom)
 *   Col-2: C1=T, C2=F → empty list returned
 *   Col-3: C1=T, C2=T → list of posts returned
 *
 * Controller layer — service is fully stubbed (STUB: PostService).
 */
@WebMvcTest(PostController.class)
@DisplayName("[B1-DT] PostController — Decision Table")
class PostControllerDTTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // STUB: PostService — replaces all business logic for controller-layer isolation
    @MockBean
    private PostService postService;

    /* ═══════════════════════════════════════════════════════════════════
     * POST /create — multipart
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("POST /create — CreateNewPost Decision Table")
    class CreatePost {

        @Test
        @DisplayName("DT-POST-001 | C1=T (user exists) → 200 with PostResponse")
        void should_returnPostResponse_when_userExistsAndMediaProvided() throws Exception {
            PostResponse stubPost = buildStubPostResponse("post-001", "user-001");
            // STUB: service returns created post
            when(postService.CreateNewPost(any(), any())).thenReturn(stubPost);

            MockMultipartFile mediaPart = new MockMultipartFile(
                    "media", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image".getBytes());
            MockMultipartFile dataPart = new MockMultipartFile(
                    "data", "", MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(new PostRequest("user-001", "Hello world!", null, 0L)));

            mockMvc.perform(multipart("/create")
                            .file(mediaPart)
                            .file(dataPart))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("post-001"));
        }

        @Test
        @DisplayName("DT-POST-002 | C1=F (user not found) → UserNotFoundException propagated")
        void should_propagateUserNotFound_when_userDoesNotExist() throws Exception {
            // STUB: user not in identity service
            when(postService.CreateNewPost(any(), any()))
                    .thenThrow(new UserNotFoundException("user-999"));

            MockMultipartFile mediaPart = new MockMultipartFile(
                    "media", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "img".getBytes());
            MockMultipartFile dataPart = new MockMultipartFile(
                    "data", "", MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(new PostRequest("user-999", "test", null, 0L)));

            mockMvc.perform(multipart("/create")
                            .file(mediaPart)
                            .file(dataPart))
                    .andExpect(status().is5xxServerError()); // no @ControllerAdvice — propagates as 500
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * GET /profile/{user-id} — Decision Table (C1 × C2)
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("GET /profile/{user-id} — GetPostInUserProfile Decision Table")
    class GetPostInUserProfile {

        @Test
        @DisplayName("DT-PROFILE-001 | C1=F → UserNotFoundException (5xx)")
        void should_throw_when_userDoesNotExist() throws Exception {
            // STUB: user not found
            when(postService.GetPostInUserProfile("ghost-user"))
                    .thenThrow(new UserNotFoundException("ghost-user"));

            mockMvc.perform(get("/profile/ghost-user"))
                    .andExpect(status().is5xxServerError());
        }

        @Test
        @DisplayName("DT-PROFILE-002 | C1=T, C2=F → 200 with empty post list")
        void should_returnEmptyList_when_userExistsButHasNoPosts() throws Exception {
            UserPostProfileResponse emptyResponse = new UserPostProfileResponse();
            emptyResponse.setUserId("user-001");
            // STUB: user exists but has no posts
            when(postService.GetPostInUserProfile("user-001")).thenReturn(emptyResponse);

            mockMvc.perform(get("/profile/user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("user-001"))
                    .andExpect(jsonPath("$.listUserPost").isArray())
                    .andExpect(jsonPath("$.listUserPost").isEmpty());
        }

        @Test
        @DisplayName("DT-PROFILE-003 | C1=T, C2=T → 200 with non-empty post list")
        void should_returnPostList_when_userExistsAndHasPosts() throws Exception {
            UserPostProfileResponse response = new UserPostProfileResponse();
            response.setUserId("user-001");
            response.getListUserPost().add(buildStubPostResponse("p-1", "user-001"));
            response.getListUserPost().add(buildStubPostResponse("p-2", "user-001"));

            // STUB: two posts returned
            when(postService.GetPostInUserProfile("user-001")).thenReturn(response);

            mockMvc.perform(get("/profile/user-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.listUserPost.length()").value(2));
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * DELETE /delete/{user-id}/{post-id}
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("DELETE /delete/{user-id}/{post-id}")
    class DeletePost {

        @Test
        @DisplayName("DT-DELETE-001 | User + post exist → 200 void response")
        void should_return200_when_deleteIsSuccessful() throws Exception {
            doNothing().when(postService).DeletePost("user-001", "post-001");

            mockMvc.perform(delete("/delete/user-001/post-001"))
                    .andExpect(status().isOk());

            verify(postService).DeletePost("user-001", "post-001");
        }

        @Test
        @DisplayName("DT-DELETE-002 | User not found → UserNotFoundException (5xx)")
        void should_propagateException_when_userNotFoundOnDelete() throws Exception {
            doThrow(new UserNotFoundException("user-999"))
                    .when(postService).DeletePost("user-999", "post-001");

            mockMvc.perform(delete("/delete/user-999/post-001"))
                    .andExpect(status().is5xxServerError());
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * PUT /like/{post-id} and /unlike/{post-id}
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("PUT /like and /unlike")
    class LikeUnlike {

        @Test
        @DisplayName("DT-LIKE-001 | Like post — 200")
        void should_return200_when_likePostCalled() throws Exception {
            doNothing().when(postService).LikePost(eq("post-001"), any());

            mockMvc.perform(put("/like/post-001")
                            .header("Authorization", "Bearer stub.token"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DT-UNLIKE-001 | Unlike post — 200")
        void should_return200_when_unlikePostCalled() throws Exception {
            doNothing().when(postService).unlikePost(eq("post-001"), any());

            mockMvc.perform(put("/unlike/post-001")
                            .header("Authorization", "Bearer stub.token"))
                    .andExpect(status().isOk());
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * PUT /comment/{post-id}
     * ═══════════════════════════════════════════════════════════════════ */

    @Test
    @DisplayName("DT-COMMENT-001 | Comment on post → 200 ApiResponse")
    void should_return200_when_commentPosted() throws Exception {
        doNothing().when(postService).commentPost(eq("post-001"), any());
        CommentRequest req = new CommentRequest("user-001", "Nice photo!");

        mockMvc.perform(put("/comment/post-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    /* ═══════════════════════════════════════════════════════════════════
     * POST /clear-cache
     * ═══════════════════════════════════════════════════════════════════ */

    @Test
    @DisplayName("DT-CACHE-001 | Clear cache → 200")
    void should_return200_when_cacheClearedSuccessfully() throws Exception {
        doNothing().when(postService).clearCache();

        mockMvc.perform(post("/clear-cache"))
                .andExpect(status().isOk());
    }

    /* ─── Helper ──────────────────────────────────────────────────────── */
    private PostResponse buildStubPostResponse(String postId, String userId) {
        PostResponse r = new PostResponse();
        r.setId(postId);
        r.setUserName("testuser");
        r.setFirstName("Test");
        r.setLastName("User");
        r.setDescription("Test post");
        r.setLiked(0L);
        r.setLikedByUser(false);
        r.setCreateAt(LocalDateTime.now());
        r.setCommentList(new ArrayList<>());
        return r;
    }
}
