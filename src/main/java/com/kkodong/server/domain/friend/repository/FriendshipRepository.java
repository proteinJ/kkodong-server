package com.kkodong.server.domain.friend.repository;

import com.kkodong.server.domain.friend.domain.Friendship;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    @Query(value = """
        SELECT CASE WHEN f.userAId = :me
            THEN f.userBId ELSE f.userAId END 
            FROM Friendship f
            WHERE f.userAId = :me OR f.userBId = :me
    """)
    List<UUID> findFriendUserIds(@Param("me") UUID me);
}
