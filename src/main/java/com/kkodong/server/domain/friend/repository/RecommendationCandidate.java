package com.kkodong.server.domain.friend.repository;

import java.util.UUID;

public interface RecommendationCandidate {
    UUID getDogId();
    UUID getOwnerId();
    double getDistanceMeters();
}
