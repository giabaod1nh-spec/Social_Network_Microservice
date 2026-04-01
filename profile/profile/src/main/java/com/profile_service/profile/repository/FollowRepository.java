package com.profile_service.profile.repository;

import com.profile_service.profile.entity.FollowRelationship;
import com.profile_service.profile.entity.UserProfile;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;

public interface FollowRepository extends Neo4jRepository<FollowRelationship , Long> {
    @Query("""
MATCH (f:user_profile {userId: $followerUserId})
MATCH (t:user_profile {userId: $targetUserId})
MERGE (f)-[r:FOLLOW]->(t)
ON CREATE SET r.createdAt = datetime(), r.notificationEnabled = true
"RETURN count(r) > 0")
""")
    boolean createFollow(String followerUserId, String targetUserId);

    @Query("""
MATCH (f:user_profile {userId: $followerUserId})-[r:FOLLOW]->(t:user_profile {userId: $targetUserId})
WITH r
DELETE r
RETURN COUNT(*)
""")
    long unfollow(String followerUserId, String targetUserId);

    @Query("""
MATCH (f:user_profile {userId: $followerUserId})-[r:FOLLOW]->(t:user_profile {userId: $targetUserId})
RETURN COUNT(r) > 0
""")
    boolean isFollowing(String followerUserId, String targetUserId);

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
