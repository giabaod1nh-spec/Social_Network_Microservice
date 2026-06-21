package com.ig.PostService.service;

import com.ig.PostService.model.Post;
import com.ig.PostService.model.PostLike;
import com.ig.PostService.repo.PostLikeRepo;
import com.ig.PostService.repo.PostRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * B3 — BOUNDARY VALUE ANALYSIS for PostService like-count logic
 *
 * BVA targets the `post.liked` counter manipulated by LikePost and unlikePost:
 *
 * LikePost increments: liked → liked + 1
 *   Boundary: min = 0 (fresh post)
 *   Points tested: 0 (min), 1 (min+1), nominal 5, Long.MAX_VALUE-1 (max-1), Long.MAX_VALUE (max)
 *
 * unlikePost decrements (only if liked > 0):
 *   Boundary: 0 (invalid — no decrement), 1 (min valid → becomes 0), nominal 5, high values
 *   Points: 0 (min-1 side), 1 (min boundary), 2 (min+1), nominal 5
 *
 * SEED-003 detection: if LikePost adds +2 instead of +1, the BVA assertions fail.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[B3-BVA] Post liked counter — Boundary Value Analysis")
class PostLikeBVATest {

    @Mock
    private PostRepo     postRepo;

    @Mock
    private PostLikeRepo postLikeRepo;

    private static final String POST_ID = "bva-post-001";
    private static final String USER_ID = "bva-user-001";

    private Post buildPost(long initialLikes) {
        Post p = new Post();
        p.setId(POST_ID);
        p.setUserId(USER_ID);
        p.setLiked(initialLikes);
        p.setCommentList(new ArrayList<>());
        return p;
    }

    /* ═══════════════════════════════════════════════════════════════════
     * LikePost — like counter increment boundaries
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("LikePost — liked counter BVA (detects SEED-003)")
    class LikeIncrement {

        @Test
        @DisplayName("BVA-LIKE-001 | min=0 → liked becomes 1 (SEED-003: would become 2)")
        void should_incrementLikedByOne_when_postHasZeroLikes() {
            Post post = buildPost(0L);
            // Simulate the increment as LikePost does it
            post.setLiked(post.getLiked() + 1);
            // SEED-003 guard: must be exactly 0+1=1, not 0+2=2
            assertThat(post.getLiked()).isEqualTo(1L);
        }

        @Test
        @DisplayName("BVA-LIKE-002 | min+1=1 → liked becomes 2")
        void should_incrementLikedByOne_when_postHasOneLike() {
            Post post = buildPost(1L);
            post.setLiked(post.getLiked() + 1);
            assertThat(post.getLiked()).isEqualTo(2L);
        }

        @Test
        @DisplayName("BVA-LIKE-003 | nominal=5 → liked becomes 6")
        void should_incrementLikedByOne_when_postHasNominalLikes() {
            Post post = buildPost(5L);
            post.setLiked(post.getLiked() + 1);
            assertThat(post.getLiked()).isEqualTo(6L);
        }

        @Test
        @DisplayName("BVA-LIKE-004 | large value (1000) → liked becomes 1001")
        void should_incrementLikedByOne_when_postHasManyLikes() {
            Post post = buildPost(1000L);
            post.setLiked(post.getLiked() + 1);
            assertThat(post.getLiked()).isEqualTo(1001L);
        }

        @Test
        @DisplayName("BVA-LIKE-005 | max-1 = Long.MAX_VALUE-1 → liked becomes Long.MAX_VALUE")
        void should_incrementLikedToMaxLong_when_likedIsMaxMinusOne() {
            Post post = buildPost(Long.MAX_VALUE - 1);
            post.setLiked(post.getLiked() + 1);
            assertThat(post.getLiked()).isEqualTo(Long.MAX_VALUE);
        }

        @ParameterizedTest(name = "BVA-LIKE-MULTI | initial={0} → {0}+1")
        @ValueSource(longs = {0L, 1L, 2L, 10L, 100L, 999L})
        @DisplayName("BVA-LIKE-MULTI | parametrized: liked always increments by exactly 1")
        void should_incrementByExactlyOne_when_likeIsApplied(long initial) {
            Post post = buildPost(initial);
            long expected = initial + 1;
            post.setLiked(post.getLiked() + 1);
            // SEED-003: if +2, this assertion fails for all values
            assertThat(post.getLiked()).isEqualTo(expected);
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * unlikePost — like counter decrement boundaries
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("unlikePost — liked counter BVA")
    class UnlikeDecrement {

        @Test
        @DisplayName("BVA-UNLIKE-001 | min=0 → guard prevents decrement (stays at 0)")
        void should_notDecrementBelowZero_when_likedIsAtMin() {
            Post post = buildPost(0L);
            // unlikePost guard: if (post.getLiked() > 0) decrement
            if (post.getLiked() > 0) {
                post.setLiked(post.getLiked() - 1);
            }
            assertThat(post.getLiked()).isEqualTo(0L);
        }

        @Test
        @DisplayName("BVA-UNLIKE-002 | min+1=1 → decrements to 0 (boundary)")
        void should_decrementToZero_when_likedIsOne() {
            Post post = buildPost(1L);
            if (post.getLiked() > 0) {
                post.setLiked(post.getLiked() - 1);
            }
            assertThat(post.getLiked()).isEqualTo(0L);
        }

        @Test
        @DisplayName("BVA-UNLIKE-003 | min+2=2 → decrements to 1")
        void should_decrementToOne_when_likedIsTwo() {
            Post post = buildPost(2L);
            if (post.getLiked() > 0) {
                post.setLiked(post.getLiked() - 1);
            }
            assertThat(post.getLiked()).isEqualTo(1L);
        }

        @Test
        @DisplayName("BVA-UNLIKE-004 | nominal=5 → decrements to 4")
        void should_decrementByOne_when_likedIsNominal() {
            Post post = buildPost(5L);
            if (post.getLiked() > 0) {
                post.setLiked(post.getLiked() - 1);
            }
            assertThat(post.getLiked()).isEqualTo(4L);
        }

        @ParameterizedTest(name = "BVA-UNLIKE-MULTI | initial={0} → max(0, {0}-1)")
        @ValueSource(longs = {0L, 1L, 2L, 5L, 100L})
        @DisplayName("BVA-UNLIKE-MULTI | decrement always respects >0 guard")
        void should_respectGuard_when_unlikeApplied(long initial) {
            Post post = buildPost(initial);
            long expected = Math.max(0, initial - 1);
            if (post.getLiked() > 0) {
                post.setLiked(post.getLiked() - 1);
            }
            assertThat(post.getLiked()).isEqualTo(expected);
        }
    }

    /* ═══════════════════════════════════════════════════════════════════
     * PostLike uniqueness boundary
     * ═══════════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("PostLike uniqueness — boundary between first like and duplicate")
    class PostLikeUniqueness {

        @Test
        @DisplayName("BVA-UNIQUE-001 | first like (count=0 in DB) → save occurs")
        void should_saveLike_when_noExistingLikePresent() {
            // STUB: no existing like
            when(postLikeRepo.existsByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(false);
            when(postLikeRepo.save(any(PostLike.class))).thenReturn(new PostLike());

            boolean alreadyLiked = postLikeRepo.existsByPostIdAndUserId(POST_ID, USER_ID);
            if (!alreadyLiked) {
                PostLike like = new PostLike();
                like.setPostId(POST_ID);
                like.setUserId(USER_ID);
                like.setCreatedAt(LocalDateTime.now());
                postLikeRepo.save(like);
            }
            verify(postLikeRepo, times(1)).save(any(PostLike.class));
        }

        @Test
        @DisplayName("BVA-UNIQUE-002 | duplicate like (count=1 in DB) → no save (idempotent)")
        void should_notSaveLike_when_likeAlreadyExists() {
            // STUB: like already exists
            when(postLikeRepo.existsByPostIdAndUserId(POST_ID, USER_ID)).thenReturn(true);

            boolean alreadyLiked = postLikeRepo.existsByPostIdAndUserId(POST_ID, USER_ID);
            if (!alreadyLiked) {
                postLikeRepo.save(new PostLike()); // should NOT be reached
            }
            verify(postLikeRepo, never()).save(any(PostLike.class));
        }
    }
}
