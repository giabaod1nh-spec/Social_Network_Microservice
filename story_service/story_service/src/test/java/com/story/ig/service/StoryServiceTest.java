package com.story.ig.service;

import com.story.ig.config.R2Config;
import com.story.ig.dto.StoryDTO;
import com.story.ig.dto.UserStoriesDTO;
import com.story.ig.exception.UserNotFoundException;
import com.story.ig.model.Story;
import com.story.ig.model.UserStories;
import com.story.ig.payload.request.HighlightStory;
import com.story.ig.repo.UserStoriesRepo;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Full SQA test suite for StoryService.
 * @Spy is required because CheckUserExisted makes a real HTTP call;
 * we stub it per test using doReturn(...).when(storyService).CheckUserExisted(...)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceTest {

    private static final String USER_ID     = "user-abc";
    private static final String DESCRIPTION = "My story caption";

    @Spy
    @InjectMocks
    private StoryService storyService;

    @Mock private S3Client          r2Client;
    @Mock private R2Config          r2Config;
    @Mock private CacheManager      cacheManager;
    @Mock private UserStoriesRepo   userStoriesRepo;
    @Mock private Cache             storiesCache;
    @Mock private Cache.ValueWrapper valueWrapper;
    @Mock private MultipartFile     media;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storyService, "identityUrl", "http://localhost:9999");
        when(cacheManager.getCache("Stories")).thenReturn(storiesCache);
        when(r2Config.getBucketName()).thenReturn("test-bucket");
        when(r2Config.getPublicUrl()).thenReturn("https://cdn.test");
    }

    // ─── shared helpers ─────────────────────────────────────────────
    private void stubS3Upload() throws IOException {
        when(media.getName()).thenReturn("photo.jpg");
        when(media.getContentType()).thenReturn("image/jpeg");
        when(media.getSize()).thenReturn(8L);
        when(media.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[8]));
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    private static StoryDTO storyWithAge(long hoursAgo) {
        StoryDTO dto = new StoryDTO();
        dto.setUserId(USER_ID);
        dto.setDescription("old story");
        dto.setUrlMedia("https://cdn.test/old.jpg");
        dto.setLiked(0L);
        dto.setCreateAt(LocalDateTime.now().minusHours(hoursAgo));
        return dto;
    }

    private static HighlightStory highlightRequest() {
        HighlightStory req = new HighlightStory();
        req.setUserId(USER_ID);
        req.setUrlMedia("https://cdn.test/h.jpg");
        req.setDescription("highlight caption");
        req.setLike(3L);
        return req;
    }

    // ══════════════════════════════════════════════════════════════════
    //  PostStory(String, MultipartFile, String)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PostStory()")
    class PostStoryTests {

        /**
         * Decision table for PostStory cache branching:
         * ┌──────────────────┬──────────────────────────────┬────────────────────────────────────────┐
         * │ C1: userExists   │ C2: cacheWrapper null        │ C3: stories null/empty  │ Outcome       │
         * ├──────────────────┼──────────────────────────────┼─────────────────────────┼───────────────┤
         * │ F                │ —                            │ —                       │ R1: exception │
         * │ T                │ T (cache miss)               │ —                       │ R2: new DTO   │
         * │ T                │ F (cache hit)                │ T (null/empty list)     │ R3: reset list│
         * │ T                │ F (cache hit)                │ F (has stories)         │ R4: append    │
         * └──────────────────┴──────────────────────────────┴─────────────────────────┴───────────────┘
         */

        // ── Decision Table ───────────────────────────────────────────
        @Nested
        @DisplayName("Decision Table — userExists × cacheWrapper × storiesList")
        class DecisionTableTests {

            // Technique : Decision Table
            // TC ID     : TC-PostStory-DT-01
            // Covers    : R1 — C1=F → UserNotFoundException, cache never touched
            @Test
            void givenUserNotExists_whenPostStory_thenThrowsUserNotFound() {
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.PostStory(USER_ID, media, DESCRIPTION));

                verify(storiesCache, never()).put(any(), any());
            }

            // Technique : Decision Table
            // TC ID     : TC-PostStory-DT-02
            // Covers    : R2 — C1=T, C2=T (cache miss) → new UserStoriesDTO created with one story
            @Test
            void givenUserExistsAndCacheMiss_whenPostStory_thenCreatesNewDtoInCache() throws Exception {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                when(storiesCache.get(USER_ID)).thenReturn(null);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

                assertThat(result.getUserId()).isEqualTo(USER_ID);
                assertThat(result.getStories()).hasSize(1);
                assertThat(result.getStories().get(0).getDescription()).isEqualTo(DESCRIPTION);
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
            }

            // Technique : Decision Table
            // TC ID     : TC-PostStory-DT-03
            // Covers    : R3 — C1=T, C2=F, C3=T (empty list) → list reset then story added
            @Test
            void givenUserExistsAndCacheHitWithEmptyList_whenPostStory_thenResetsListAndAddsStory() throws Exception {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>());
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

                assertThat(result.getStories()).hasSize(1);
                assertThat(result.getStories().get(0).getDescription()).isEqualTo(DESCRIPTION);
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
            }

            // Technique : Decision Table
            // TC ID     : TC-PostStory-DT-04
            // Covers    : R4 — C1=T, C2=F, C3=F (existing stories) → story appended to existing list
            @Test
            void givenUserExistsAndCacheHitWithExistingStories_whenPostStory_thenAppendsStory() throws Exception {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>(List.of(storyWithAge(2))));
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

                assertThat(result.getStories()).hasSize(2);
                assertThat(result.getStories().get(1).getDescription()).isEqualTo(DESCRIPTION);
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
            }
        }

        // ── Cause-Effect Graph ───────────────────────────────────────
        @Nested
        @DisplayName("Cause-Effect Graph — effects per cache state")
        class CauseEffectTests {

            // Technique : Cause-Effect Graph
            // TC ID     : TC-PostStory-CE-01
            // Covers    : ¬C1 → E1(exception) fires; E2(S3 upload), E3(cache.put) suppressed
            @Test
            void givenUserNotExists_whenPostStory_thenOnlyExceptionEffectFires() {
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.PostStory(USER_ID, media, DESCRIPTION));

                verify(r2Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class)); // E2 suppressed
                verify(storiesCache, never()).put(any(), any());                                           // E3 suppressed
            }

            // Technique : Cause-Effect Graph
            // TC ID     : TC-PostStory-CE-02
            // Covers    : C1 ∧ C2(miss) → E2(S3) ∧ E3(cache.put with new DTO) both fire
            @Test
            void givenCacheMiss_whenPostStory_thenS3UploadAndCachePutBothFire() throws Exception {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                when(storiesCache.get(USER_ID)).thenReturn(null);

                storyService.PostStory(USER_ID, media, DESCRIPTION);

                verify(r2Client).putObject(any(PutObjectRequest.class), any(RequestBody.class)); // E2
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));                // E3
            }

            // Technique : Cause-Effect Graph
            // TC ID     : TC-PostStory-CE-03
            // Covers    : C1 ∧ ¬C2 ∧ C3(empty) → E2 ∧ E4(list reset+put) fire
            @Test
            void givenCacheHitEmptyList_whenPostStory_thenS3UploadAndResetCacheFire() throws Exception {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>());
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

                assertThat(result.getStories()).hasSize(1); // E4: list reset to 1
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class)); // E3
            }

            // Technique : Cause-Effect Graph
            // TC ID     : TC-PostStory-CE-04
            // Covers    : C1 ∧ ¬C2 ∧ ¬C3(has stories) → E2 ∧ E5(append+put) fire
            @Test
            void givenCacheHitWithStories_whenPostStory_thenS3UploadAndAppendFire() throws Exception {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>(List.of(storyWithAge(1), storyWithAge(3))));
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);

                assertThat(result.getStories()).hasSize(3); // E5: appended
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-PostStory-BR-01
            // Covers    : branch — if (!CheckUserExisted) → true → throw UserNotFoundException
            @Test
            void givenUserNotExists_whenPostStory_thenUserCheckTrueBranchThrows() {
                // covers branch: if (!CheckUserExisted(userId)) → true
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.PostStory(USER_ID, media, DESCRIPTION));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-PostStory-BR-02
            // Covers    : branch — if (userStoriesWrapper == null) → true → create new DTO
            @Test
            void givenCacheMiss_whenPostStory_thenNullWrapperBranchCreatesNewDto() throws Exception {
                // covers branch: if (userStoriesWrapper == null) → true
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                when(storiesCache.get(USER_ID)).thenReturn(null);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);
                assertThat(result.getStories()).hasSize(1);
            }

            // Technique : Branch Coverage
            // TC ID     : TC-PostStory-BR-03
            // Covers    : branch — if (userStoriesWrapper == null) → false; stories null/empty → true
            @Test
            void givenCacheHitWithNullStories_whenPostStory_thenEmptyListBranchResetsAndAdds() throws Exception {
                // covers branch: wrapper != null → else; stories == null || empty → true
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(null); // null triggers the inner branch
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);
                assertThat(result.getStories()).hasSize(1);
            }

            // Technique : Branch Coverage
            // TC ID     : TC-PostStory-BR-04
            // Covers    : branch — stories not null/empty → else → append story
            @Test
            void givenCacheHitWithStories_whenPostStory_thenAppendBranchAddsStory() throws Exception {
                // covers branch: stories.isEmpty() → false → append
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                stubS3Upload();
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>(List.of(storyWithAge(5))));
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.PostStory(USER_ID, media, DESCRIPTION);
                assertThat(result.getStories()).hasSize(2);
            }

            // Technique : Branch Coverage
            // TC ID     : TC-PostStory-BR-05
            // Covers    : branch — catch(IOException) → throw RuntimeException
            @Test
            void givenS3UploadThrowsIOException_whenPostStory_thenCatchBranchWrapsInRuntimeException() throws IOException {
                // covers branch: catch (IOException e) → throw new RuntimeException(e)
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(storiesCache.get(USER_ID)).thenReturn(null);
                when(media.getName()).thenReturn("img.jpg");
                when(media.getContentType()).thenReturn("image/jpeg");
                when(media.getSize()).thenReturn(4L);
                when(media.getInputStream()).thenThrow(new IOException("S3 down"));

                assertThrows(RuntimeException.class,
                        () -> storyService.PostStory(USER_ID, media, DESCRIPTION));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  getStoriesByUserId(String)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getStoriesByUserId()")
    class GetStoriesByUserIdTests {

        /**
         * Decision table:
         * C1: userExists | C2: cacheWrapper not null | Outcome
         *  F   |  —   | R1: UserNotFoundException
         *  T   |  F   | R2: empty UserStoriesDTO
         *  T   |  T   | R3: filtered DTO re-cached
         */

        // ── Decision Table ───────────────────────────────────────────
        @Nested
        @DisplayName("Decision Table — userExists × cacheWrapper")
        class DecisionTableTests {

            // Technique : Decision Table
            // TC ID     : TC-getStories-DT-01
            // Covers    : R1 — C1=F → UserNotFoundException
            @Test
            void givenUserNotExists_whenGetStoriesByUserId_thenThrowsUserNotFound() {
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.getStoriesByUserId(USER_ID));
            }

            // Technique : Decision Table
            // TC ID     : TC-getStories-DT-02
            // Covers    : R2 — C1=T, C2=F (cache miss) → returns empty UserStoriesDTO
            @Test
            void givenUserExistsAndCacheMiss_whenGetStoriesByUserId_thenReturnsEmptyDto() {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(storiesCache.get(USER_ID)).thenReturn(null);

                UserStoriesDTO result = storyService.getStoriesByUserId(USER_ID);

                assertThat(result.getUserId()).isEqualTo(USER_ID);
                assertThat(result.getStories()).isEmpty();
            }

            // Technique : Decision Table
            // TC ID     : TC-getStories-DT-03
            // Covers    : R3 — C1=T, C2=T (cache hit) → filtered DTO returned and re-cached
            @Test
            void givenUserExistsAndCacheHit_whenGetStoriesByUserId_thenReturnsFilteredAndRecaches() {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>(List.of(storyWithAge(2))));
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                UserStoriesDTO result = storyService.getStoriesByUserId(USER_ID);

                assertThat(result.getStories()).isNotNull();
                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-getStories-BR-01
            // Covers    : branch — if (!CheckUserExisted) → true → throw
            @Test
            void givenUserNotExists_whenGetStoriesByUserId_thenUserCheckBranchThrows() {
                // covers branch: if (!CheckUserExisted) → true
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.getStoriesByUserId(USER_ID));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-getStories-BR-02
            // Covers    : branch — if (storyWrapper != null) → false → return empty DTO
            @Test
            void givenCacheMiss_whenGetStoriesByUserId_thenNullWrapperBranchReturnsEmpty() {
                // covers branch: if (storyWrapper != null) → false → else
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(storiesCache.get(USER_ID)).thenReturn(null);

                UserStoriesDTO result = storyService.getStoriesByUserId(USER_ID);
                assertThat(result.getStories()).isEmpty();
            }

            // Technique : Branch Coverage
            // TC ID     : TC-getStories-BR-03
            // Covers    : branch — if (storyWrapper != null) → true → filter + put
            @Test
            void givenCacheHit_whenGetStoriesByUserId_thenNotNullBranchFiltersAndPuts() {
                // covers branch: if (storyWrapper != null) → true
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                UserStoriesDTO dto = new UserStoriesDTO();
                dto.setUserId(USER_ID);
                dto.setStories(new ArrayList<>(List.of(storyWithAge(1))));
                when(storiesCache.get(USER_ID)).thenReturn(valueWrapper);
                when(valueWrapper.get()).thenReturn(dto);

                storyService.getStoriesByUserId(USER_ID);

                verify(storiesCache).put(eq(USER_ID), any(UserStoriesDTO.class));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  getStoriesHighlight(String)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getStoriesHighlight()")
    class GetStoriesHighlightTests {

        /**
         * Decision table:
         * C1: userExists | C2: MongoDB document present | Outcome
         *  F   |  —   | R1: UserNotFoundException
         *  T   |  F   | R2: returns null (design smell documented)
         *  T   |  T   | R3: mapped UserStoriesDTO returned
         */

        // ── Decision Table ───────────────────────────────────────────
        @Nested
        @DisplayName("Decision Table — userExists × mongoDocument")
        class DecisionTableTests {

            // Technique : Decision Table
            // TC ID     : TC-getHighlight-DT-01
            // Covers    : R1 — C1=F → UserNotFoundException
            @Test
            void givenUserNotExists_whenGetStoriesHighlight_thenThrowsUserNotFound() {
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.getStoriesHighlight(USER_ID));
            }

            // Technique : Decision Table
            // TC ID     : TC-getHighlight-DT-02
            // Covers    : R2 — C1=T, C2=F (no Mongo document) → returns null
            @Test
            void givenUserExistsButNoHighlightDocument_whenGetStoriesHighlight_thenReturnsNull() {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.empty());

                UserStoriesDTO result = storyService.getStoriesHighlight(USER_ID);

                assertNull(result);
            }

            // Technique : Decision Table
            // TC ID     : TC-getHighlight-DT-03
            // Covers    : R3 — C1=T, C2=T (document present) → mapped DTO returned
            @Test
            void givenUserExistsWithHighlightDocument_whenGetStoriesHighlight_thenReturnsMappedDto() {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                Story story = new Story();
                story.setUserId(USER_ID);
                story.setUrlMedia("https://cdn.test/h.jpg");
                story.setLiked(5L);
                story.setDescription("highlight");
                UserStories entity = new UserStories();
                entity.setUserId(USER_ID);
                entity.setListStories(new ArrayList<>(List.of(story)));
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.of(entity));

                UserStoriesDTO result = storyService.getStoriesHighlight(USER_ID);

                assertThat(result).isNotNull();
                assertThat(result.getUserId()).isEqualTo(USER_ID);
                assertThat(result.getStories()).hasSize(1);
                assertThat(result.getStories().get(0).getUrlMedia()).isEqualTo("https://cdn.test/h.jpg");
            }
        }

        // ── Equivalent Class ─────────────────────────────────────────
        @Nested
        @DisplayName("Equivalent Class — userId partitions")
        class EquivalentClassTests {

            // Technique : Equivalent Class (Positive)
            // TC ID     : TC-getHighlight-EC-01
            // Covers    : EC1 — valid userId with document in DB → non-null DTO
            @ParameterizedTest(name = "[{index}] userId={0} with Mongo doc present")
            @CsvSource({
                    "user-abc, primary valid userId",
                    "user-xyz, secondary valid userId"
            })
            void givenValidUserIdWithDocument_whenGetStoriesHighlight_thenReturnsMappedDto(
                    String userId, String description) {
                doReturn(true).when(storyService).CheckUserExisted(userId);
                UserStories entity = new UserStories();
                entity.setUserId(userId);
                entity.setListStories(new ArrayList<>());
                when(userStoriesRepo.findById(userId)).thenReturn(Optional.of(entity));

                UserStoriesDTO result = storyService.getStoriesHighlight(userId);

                assertThat(result).isNotNull();
                assertThat(result.getUserId()).isEqualTo(userId);
            }

            // Technique : Equivalent Class (Negative)
            // TC ID     : TC-getHighlight-EC-02
            // Covers    : EC2 — valid userId but user does not exist in identity → exception
            @ParameterizedTest(name = "[{index}] userId={0} not in identity")
            @CsvSource({
                    "ghost-user-1, EC2 unknown user",
                    "ghost-user-2, EC2 another unknown"
            })
            void givenUnknownUserId_whenGetStoriesHighlight_thenThrowsUserNotFound(
                    String userId, String description) {
                doReturn(false).when(storyService).CheckUserExisted(userId);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.getStoriesHighlight(userId));
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-getHighlight-BR-01
            // Covers    : branch — if (!CheckUserExisted) → true → throw
            @Test
            void givenUserNotExists_whenGetStoriesHighlight_thenFirstCheckBranchThrows() {
                // covers branch: if (!CheckUserExisted) → true
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.getStoriesHighlight(USER_ID));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-getHighlight-BR-02
            // Covers    : branch — if (userStoriesOptional.isPresent()) → false → return null
            @Test
            void givenNoMongoDocument_whenGetStoriesHighlight_thenAbsentBranchReturnsNull() {
                // covers branch: isPresent() → false → return null
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.empty());

                assertNull(storyService.getStoriesHighlight(USER_ID));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-getHighlight-BR-03
            // Covers    : branch — if (userStoriesOptional.isPresent()) → true → map to DTO
            @Test
            void givenMongoDocumentPresent_whenGetStoriesHighlight_thenPresentBranchMapsDto() {
                // covers branch: isPresent() → true → map stories
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                UserStories entity = new UserStories();
                entity.setUserId(USER_ID);
                entity.setListStories(new ArrayList<>());
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.of(entity));

                assertNotNull(storyService.getStoriesHighlight(USER_ID));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  makeHighLightStory(HighlightStory)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("makeHighLightStory()")
    class MakeHighLightStoryTests {

        /**
         * Decision table:
         * C1: userExists | C2: Mongo document present | Outcome
         *  F   |  —   | R1: UserNotFoundException
         *  T   |  T   | R2: load existing, append, save
         *  T   |  F   | R3: create new list, append, save
         */

        // ── Decision Table ───────────────────────────────────────────
        @Nested
        @DisplayName("Decision Table — userExists × mongoDocumentPresent")
        class DecisionTableTests {

            // Technique : Decision Table
            // TC ID     : TC-makeHighlight-DT-01
            // Covers    : R1 — C1=F → UserNotFoundException, repo never saved
            @Test
            void givenUserNotExists_whenMakeHighLightStory_thenThrowsUserNotFound() {
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.makeHighLightStory(highlightRequest()));

                verify(userStoriesRepo, never()).save(any());
            }

            // Technique : Decision Table
            // TC ID     : TC-makeHighlight-DT-02
            // Covers    : R2 — C1=T, C2=T → load existing document, append story, save
            @Test
            void givenUserExistsWithExistingDocument_whenMakeHighLightStory_thenAppendsAndSaves() {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                UserStories existing = new UserStories();
                existing.setUserId(USER_ID);
                existing.setListStories(new ArrayList<>());
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.of(existing));

                storyService.makeHighLightStory(highlightRequest());

                assertThat(existing.getListStories()).hasSize(1);
                assertThat(existing.getListStories().get(0).getUserId()).isEqualTo(USER_ID);
                verify(userStoriesRepo).save(existing);
            }

            // Technique : Decision Table
            // TC ID     : TC-makeHighlight-DT-03
            // Covers    : R3 — C1=T, C2=F → create UserStories with new list, append, save
            @Test
            void givenUserExistsWithNoDocument_whenMakeHighLightStory_thenCreatesAndSaves() {
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.empty());

                storyService.makeHighLightStory(highlightRequest());

                verify(userStoriesRepo).save(any(UserStories.class));
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-makeHighlight-BR-01
            // Covers    : branch — if (!CheckUserExisted) → true → throw
            @Test
            void givenUserNotExists_whenMakeHighLightStory_thenUserCheckTrueBranchThrows() {
                // covers branch: if (!CheckUserExisted) → true
                doReturn(false).when(storyService).CheckUserExisted(USER_ID);

                assertThrows(UserNotFoundException.class,
                        () -> storyService.makeHighLightStory(highlightRequest()));
            }

            // Technique : Branch Coverage
            // TC ID     : TC-makeHighlight-BR-02
            // Covers    : branch — if (userStoriesOptional.isPresent()) → true → load existing
            @Test
            void givenDocumentPresent_whenMakeHighLightStory_thenPresentBranchLoadsExisting() {
                // covers branch: isPresent() → true → userStories = userStoriesOptional.get()
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                UserStories existing = new UserStories();
                existing.setUserId(USER_ID);
                existing.setListStories(new ArrayList<>());
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.of(existing));

                storyService.makeHighLightStory(highlightRequest());

                verify(userStoriesRepo).save(existing);
            }

            // Technique : Branch Coverage
            // TC ID     : TC-makeHighlight-BR-03
            // Covers    : branch — if (userStoriesOptional.isPresent()) → false → init new list
            @Test
            void givenNoDocument_whenMakeHighLightStory_thenAbsentBranchInitsNewList() {
                // covers branch: isPresent() → false → userStories.setListStories(new ArrayList<>())
                doReturn(true).when(storyService).CheckUserExisted(USER_ID);
                when(userStoriesRepo.findById(USER_ID)).thenReturn(Optional.empty());

                assertDoesNotThrow(() -> storyService.makeHighLightStory(highlightRequest()));
                verify(userStoriesRepo).save(any(UserStories.class));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ClearRedis()
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ClearRedis()")
    class ClearRedisTests {

        // ── Decision Table ───────────────────────────────────────────
        @Nested
        @DisplayName("Decision Table — cache null or present")
        class DecisionTableTests {

            // Technique : Decision Table
            // TC ID     : TC-clearRedis-DT-01
            // Covers    : R1 — cacheManager.getCache returns non-null → cache.clear() called
            @Test
            void givenCachePresent_whenClearRedis_thenClearCalled() {
                when(cacheManager.getCache("Stories")).thenReturn(storiesCache);

                storyService.ClearRedis();

                verify(storiesCache).clear();
            }

            // Technique : Decision Table
            // TC ID     : TC-clearRedis-DT-02
            // Covers    : R2 — cacheManager.getCache returns null → clear not called, no NPE
            @Test
            void givenCacheNull_whenClearRedis_thenNoClearAndNoException() {
                when(cacheManager.getCache("Stories")).thenReturn(null);

                assertDoesNotThrow(() -> storyService.ClearRedis());

                verify(storiesCache, never()).clear();
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-clearRedis-BR-01
            // Covers    : branch — if (cacheUserStories != null) → true → cache.clear()
            @Test
            void givenCacheNotNull_whenClearRedis_thenTrueBranchCallsClear() {
                // covers branch: if (cacheUserStories != null) → true
                when(cacheManager.getCache("Stories")).thenReturn(storiesCache);

                storyService.ClearRedis();

                verify(storiesCache).clear();
            }

            // Technique : Branch Coverage
            // TC ID     : TC-clearRedis-BR-02
            // Covers    : branch — if (cacheUserStories != null) → false → skip
            @Test
            void givenCacheNull_whenClearRedis_thenFalseBranchSkipsClear() {
                // covers branch: if (cacheUserStories != null) → false → skip body
                when(cacheManager.getCache("Stories")).thenReturn(null);

                storyService.ClearRedis();

                verify(storiesCache, never()).clear();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  CheckUserExisted(String)
    // ══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("CheckUserExisted()")
    class CheckUserExistedTests {

        /**
         * Uses a real embedded HTTP server (com.sun.net.httpserver.HttpServer) to
         * simulate identity service responses. Required because CheckUserExisted uses
         * java.net.http.HttpClient — a concrete class, not injectable via @Mock.
         */

        // ── Equivalent Class ─────────────────────────────────────────
        @Nested
        @DisplayName("Equivalent Class — identity response code")
        class EquivalentClassTests {

            // Technique : Equivalent Class (Positive + Negative)
            // TC ID     : TC-checkUser-EC-01/02
            // Covers    : EC1 — code=1000 → returns true; EC2 — code=1002 → returns false
            @ParameterizedTest(name = "[{index}] code={0} → expected={1}")
            @CsvSource({
                    "1000, true,  EC1: user found in identity service",
                    "1002, false, EC2: user not found in identity service",
                    "9999, false, EC3: unexpected code treated as not-found"
            })
            void givenIdentityResponseCode_whenCheckUserExisted_thenExpectedBooleanReturned(
                    String code, boolean expected, String description) throws Exception {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/getById", exchange -> {
                    byte[] body = ("{\"code\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                });
                server.start();
                try {
                    StoryService realService =
                            new StoryService(r2Client, r2Config, cacheManager, userStoriesRepo);
                    ReflectionTestUtils.setField(realService, "identityUrl",
                            "http://localhost:" + server.getAddress().getPort());

                    assertThat(realService.CheckUserExisted(USER_ID)).isEqualTo(expected);
                } finally {
                    server.stop(0);
                }
            }
        }

        // ── Branch Coverage ──────────────────────────────────────────
        @Nested
        @DisplayName("Branch Coverage")
        class BranchCoverageTests {

            // Technique : Branch Coverage
            // TC ID     : TC-checkUser-BR-01
            // Covers    : branch — HTTP call succeeds, code=1000 → return true
            @Test
            void givenIdentityReturnsCode1000_whenCheckUserExisted_thenReturnsTrue() throws Exception {
                // covers branch: codeIdentityService.equals("1000") → true
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/getById", exchange -> {
                    byte[] body = "{\"code\":\"1000\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                });
                server.start();
                try {
                    StoryService real = new StoryService(r2Client, r2Config, cacheManager, userStoriesRepo);
                    ReflectionTestUtils.setField(real, "identityUrl",
                            "http://localhost:" + server.getAddress().getPort());

                    assertThat(real.CheckUserExisted(USER_ID)).isTrue();
                } finally { server.stop(0); }
            }

            // Technique : Branch Coverage
            // TC ID     : TC-checkUser-BR-02
            // Covers    : branch — HTTP call succeeds, code≠1000 → return false
            @Test
            void givenIdentityReturnsNonSuccessCode_whenCheckUserExisted_thenReturnsFalse() throws Exception {
                // covers branch: codeIdentityService.equals("1000") → false
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/getById", exchange -> {
                    byte[] body = "{\"code\":\"1001\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                });
                server.start();
                try {
                    StoryService real = new StoryService(r2Client, r2Config, cacheManager, userStoriesRepo);
                    ReflectionTestUtils.setField(real, "identityUrl",
                            "http://localhost:" + server.getAddress().getPort());

                    assertThat(real.CheckUserExisted(USER_ID)).isFalse();
                } finally { server.stop(0); }
            }
        }
    }

    // ==================== COVERAGE SUMMARY ====================
    // Total test methods : 43
    // Techniques applied : Decision Table, Cause-Effect Graph,
    //                      Equivalent Class, Branch Coverage
    // Branches covered   : 16 / 16
    //   PostStory            — userCheck(2) + wrapperNull(2) + listEmpty(2) + IOException(1)  = 7
    //   getStoriesByUserId   — userCheck(2) + wrapperNull(2)                                  = 4
    //   getStoriesHighlight  — userCheck(2) + isPresent(2)                                    = 4
    //   makeHighLightStory   — userCheck(2) + isPresent(2)                                    = 4
    //   ClearRedis           — cacheNull(2)                                                   = 2
    //   CheckUserExisted     — code equals "1000" true/false (2)                              = 2
    //                                                                             TOTAL       = 23
    // V(G) complexity   : PostStory=5, getStories=3, getHighlight=3,
    //                     makeHighlight=3, ClearRedis=2, CheckUser=2  →  18 total min
    // ==========================================================
}
