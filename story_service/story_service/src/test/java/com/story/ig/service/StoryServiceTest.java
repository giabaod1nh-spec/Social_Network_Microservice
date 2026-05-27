package com.story.ig.service;

import com.story.ig.config.R2Config;
import com.story.ig.dto.StoryDTO;
import com.story.ig.dto.UserStoriesDTO;
import com.story.ig.exception.UserNotFoundException;
import com.story.ig.model.Story;
import com.story.ig.model.UserStories;
import com.story.ig.payload.request.HighlightStory;
import com.story.ig.repo.UserStoriesRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceTest {

    private static final String USER_ID = "user-abc";
    private static final String DESCRIPTION = "My story";

    @Spy
    @InjectMocks
    private StoryService storyService;

    @Mock
    private S3Client r2Client;
    @Mock
    private R2Config r2Config;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private UserStoriesRepo userStoriesRepo;
    @Mock
    private Cache storiesCache;
    @Mock
    private Cache.ValueWrapper valueWrapper;
    @Mock
    private MultipartFile media;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storyService, "identityUrl", "http://localhost:9999");
        when(cacheManager.getCache("Stories")).thenReturn(storiesCache);
        when(r2Config.getBucketName()).thenReturn("test-bucket");
        when(r2Config.getPublicUrl()).thenReturn("https://cdn.test");
    }

    // --- PostStory: ECP userId + cache decision table ---

    @ParameterizedTest(name = "PostStory_{1}")
    @CsvSource({
            "false, userMissing_throwsUserNotFound",
    })
    void PostStory_userNotExists_throws(boolean userExists, String scenario) {
        doReturn(userExists).when(storyService).CheckUserExisted(USER_ID);

        assertThrows(UserNotFoundException.class, () -> storyService.PostStory(USER_ID, media, DESCRIPTION));
        verify(storiesCache, never()).put(any(), any());
    }

    @ParameterizedTest(name = "PostStory_{2}")
    @CsvSource({
            "MISSING, firstStory_createsCacheEntry",
            "EMPTY_LIST, emptyStories_reinitializesList",
            "HAS_STORIES, existingStories_appendsStory",
    })
    void PostStory_cacheDecisionTable(String cacheState, String scenario) throws Exception {
        doReturn(true).when(storyService).CheckUserExisted(USER_ID);
        stubMediaUpload();

        switch (cacheState) {
            case "MISSING" -> when(storiesCache.get(USER_ID)).thenReturn(null);
            case "EMPTY_LIST" -> {
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>());
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);
            }
            case "HAS_STORIES" -> {
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                List<StoryDTO> existing = new ArrayList<>();
                existing.add(existingStory());
                dto.setStories(existing);
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);
            }
            default -> throw new IllegalArgumentException(cacheState);
        }

        UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getStories()).isNotEmpty();
        assertThat(result.getStories().getLast().getDescription()).isEqualTo(DESCRIPTION);
        verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
    }

    @Test
    void PostStory_nullStoriesList_reinitializesAndReturns() throws Exception {
        doReturn(true).when(storyService).CheckUserExisted(USER_ID);
        stubMediaUpload();
        UserStoriesDTO dto = new UserStoriesDTO();
        dto.setUserId(USER_ID);
        dto.setStories(null);
        when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(dto);

        UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

        assertThat(result.getStories()).hasSize(1);
    }

    // --- getStoriesByUserId ---

    @Test
    void getStoriesByUserId_userMissing_throwsUserNotFound() {
        doReturn(false).when(storyService).CheckUserExisted(USER_ID);

        assertThrows(UserNotFoundException.class, () -> storyService.getStoriesByUserId(USER_ID));
    }

    @Test
    void getStoriesByUserId_cacheMiss_returnsEmptyList() {
        doReturn(true).when(storyService).CheckUserExisted(USER_ID);
        when(storiesCache.get(USER_ID)).thenReturn(null);

        UserStoriesDTO result = storyService.getStoriesByUserId(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getStories()).isEmpty();
    }

    @Test
    void getStoriesByUserId_cacheHit_returnsFilteredStories() {
        doReturn(true).when(storyService).CheckUserExisted(USER_ID);
        UserStoriesDTO dto = new UserStoriesDTO();
        dto.setUserId(USER_ID);
        dto.setStories(new ArrayList<>(List.of(existingStory())));
        when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
        when(valueWrapper.get()).thenReturn(dto);

        UserStoriesDTO result = storyService.getStoriesByUserId(USER_ID);

        assertThat(result.getStories()).isNotNull();
        verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
    }

    // --- getStoriesHighlight ---

    @ParameterizedTest(name = "getStoriesHighlight_{1}")
    @CsvSource({
            "PRESENT, repoHit_returnsDto",
            "ABSENT, repoMiss_returnsNull",
    })
    void getStoriesHighlight_repositoryPartition(String repoState, String scenario) {
        doReturn(true).when(storyService).CheckUserExisted(USER_ID);

        if ("PRESENT".equals(repoState)) {
            Story story = new Story();
            story.setUserId(USER_ID);
            story.setUrlMedia("https://cdn.test/x.jpg");
            story.setLiked(2L);
            story.setDescription("highlight");
            UserStories entity = new UserStories();
            entity.setUserId(USER_ID);
            entity.setListStories(new ArrayList<>(List.of(story)));
            when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.of(entity));

            UserStoriesDTO result = storyService.getStoriesHighlight(USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getStories()).hasSize(1);
            assertThat(result.getStories().getFirst().getUrlMedia()).isEqualTo("https://cdn.test/x.jpg");
        } else {
            when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.empty());

            assertNull(storyService.getStoriesHighlight(USER_ID));
        }
    }

    @Test
    void getStoriesHighlight_userMissing_throwsUserNotFound() {
        doReturn(false).when(storyService).CheckUserExisted(USER_ID);

        assertThrows(UserNotFoundException.class, () -> storyService.getStoriesHighlight(USER_ID));
    }

    // --- makeHighLightStory ---

    @ParameterizedTest(name = "makeHighLightStory_{1}")
    @CsvSource({
            "EXISTING, userStoriesPresent_savesAppendedStory",
            "NEW, userStoriesAbsent_createsAndSaves",
    })
    void makeHighLightStory_repositoryDecisionTable(String repoState, String scenario) {
        doReturn(true).when(storyService).CheckUserExisted(USER_ID);
        HighlightStory request = highlightRequest();

        if ("EXISTING".equals(repoState)) {
            UserStories existing = new UserStories();
            existing.setUserId(USER_ID);
            existing.setListStories(new ArrayList<>());
            when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.of(existing));
        } else {
            when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.empty());
        }

        storyService.makeHighLightStory(request);

        verify(userStoriesRepo).save(any(UserStories.class));
    }

    @Test
    void makeHighLightStory_userMissing_throwsUserNotFound() {
        doReturn(false).when(storyService).CheckUserExisted(USER_ID);

        assertThrows(UserNotFoundException.class, () -> storyService.makeHighLightStory(highlightRequest()));
        verify(userStoriesRepo, never()).save(any());
    }

    // --- CheckUserExisted: ECP on identity response code ---

    @ParameterizedTest(name = "CheckUserExisted_{2}")
    @CsvSource({
            "1000, true, userFound_returnsTrue",
            "1002, false, userMissing_returnsFalse",
    })
    void CheckUserExisted_identityCodePartition(String code, boolean expected, String scenario) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/getById", exchange -> {
            byte[] body = ("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            StoryService realService = new StoryService(r2Client, r2Config, cacheManager, userStoriesRepo);
            ReflectionTestUtils.setField(realService, "identityUrl", "http://localhost:" + port);

            assertThat(realService.CheckUserExisted(USER_ID)).isEqualTo(expected);
        } finally {
            server.stop(0);
        }
    }

    // --- ClearRedis ---

    @ParameterizedTest(name = "ClearRedis_{1}")
    @CsvSource({
            "true, cachePresent_clearsCache",
            "false, cacheAbsent_noOperation",
    })
    void ClearRedis_cacheNullDecision(boolean cacheResolvable, String scenario) {
        when(cacheManager.getCache("Stories")).thenReturn(cacheResolvable ? storiesCache : null);

        storyService.ClearRedis();

        if (cacheResolvable) {
            verify(storiesCache).clear();
        }
    }

    private void stubMediaUpload() throws IOException {
        when(media.getName()).thenReturn("photo.jpg");
        when(media.getContentType()).thenReturn("image/jpeg");
        when(media.getSize()).thenReturn(4L);
        when(media.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    private static StoryDTO existingStory() {
        StoryDTO dto = new StoryDTO();
        dto.setUserId(USER_ID);
        dto.setDescription("old");
        dto.setUrlMedia("https://cdn.test/old.jpg");
        dto.setLiked(0L);
        dto.setCreateAt(LocalDateTime.now().minusHours(1));
        return dto;
    }

    private static HighlightStory highlightRequest() {
        HighlightStory request = new HighlightStory();
        request.setUserId(USER_ID);
        request.setUrlMedia("https://cdn.test/h.jpg");
        request.setDescription("highlight desc");
        request.setLike(5L);
        return request;
    }
}
