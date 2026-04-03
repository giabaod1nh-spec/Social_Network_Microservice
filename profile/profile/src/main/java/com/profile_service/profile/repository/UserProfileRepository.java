package com.profile_service.profile.repository;

import com.profile_service.profile.entity.UserProfile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends Neo4jRepository<UserProfile , String> {

    Optional<UserProfile> findByUserId(String userId);

    @Query("""
MATCH (u:user_profile {userId: $userId})<-[:FOLLOW]-(f:user_profile)
RETURN f
""")
    List<UserProfile> findFollowersByUserId(String userId);

    @Query("""
MATCH (u:user_profile {userId: $userId})-[:FOLLOW]->(t:user_profile)
RETURN t
""")
    List<UserProfile> findFollowingByUserId(String userId);

}
