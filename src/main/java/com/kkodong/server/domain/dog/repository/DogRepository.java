package com.kkodong.server.domain.dog.repository;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.friend.repository.RecommendationCandidate;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DogRepository extends JpaRepository<Dog, UUID> {
    List<Dog> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID userId);

    @Query(value = """
        SELECT d.id AS dogId,
               d.owner_id AS ownerId,
               ST_Distance(u.home_location,
                           ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distanceMeters
               FROM dogs d
               JOIN users u ON u.id = d.owner_id
               WHERE u.id <> :me
                   AND u.home_location IS NOT NULL
                   AND ST_DWithin(u.home_location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
                   AND u.id NOT IN (:excludedUserIds)
               ORDER BY distanceMeters
               LIMIT :candidateCap
    """, nativeQuery = true)
    List<RecommendationCandidate> findCandidates(
            @Param("me") UUID me,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusMeters") int radiusMeters,
            @Param("excludedUserIds") Collection<UUID> excludedUserIds,
            @Param("candidateCap") int candidateCap
            );
}
