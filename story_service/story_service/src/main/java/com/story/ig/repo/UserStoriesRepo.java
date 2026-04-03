package com.story.ig.repo;

import com.mongodb.client.MongoCollection;
import com.story.ig.model.UserStories;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserStoriesRepo extends MongoRepository<UserStories, String> {
}
