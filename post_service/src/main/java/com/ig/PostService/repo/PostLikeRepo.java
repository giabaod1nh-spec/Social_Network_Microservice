package com.ig.PostService.repo;

import com.ig.PostService.model.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;

public interface PostLikeRepo extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUserId(String postId, String userId);

    @Modifying
    @Transactional
    void deleteByPostIdAndUserId(String postId, String userId);

    @Query("SELECT pl.postId FROM PostLike pl WHERE pl.userId = :userId AND pl.postId IN :postIds")
    Set<String> findLikedPostIdsByUserIdAndPostIds(
            @Param("userId") String userId,
            @Param("postIds") Collection<String> postIds
    );
}
