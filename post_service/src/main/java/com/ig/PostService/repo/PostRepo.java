package com.ig.PostService.repo;

import com.ig.PostService.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepo extends JpaRepository<Post, String> {
    List<Post> getPostsByUserId(String userId);

    Post getPostByIdAndUserId(String id, String userId);

}
