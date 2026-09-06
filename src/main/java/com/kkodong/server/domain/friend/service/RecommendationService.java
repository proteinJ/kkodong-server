package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.friend.dto.RecommendationResponse;
import com.kkodong.server.domain.friend.repository.FriendshipRepository;
import com.kkodong.server.domain.friend.repository.RecommendationCandidate;
import com.kkodong.server.domain.safety.repository.BlockRepository;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.config.RecommendationProperties;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 후보 조회 + 반경 확장 + 정렬
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final RecommendationProperties recommendationProperties;
    private final UserRepository userRepository;
    private final DogRepository dogRepository;
    private final BlockRepository blockRepository;
    private final FriendshipRepository friendshipRepository;

    public RecommendationResponse.page recommend(UUID userId, UUID dogId, int limit, String cursor) {
        int maxRadiusKm = recommendationProperties.maxRadiusKm();

        User user = userRepository.findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getHomeLocation() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_HOME_LOCATION);
        }

        Dog dog = dogRepository.findById(dogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOG_NOT_FOUND));

        if (!dog.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.DOG_NOT_OWNED);
        }

        // 커서 해석
//        RecommendationCursor.decode(radius, offset);

        double lat = user.getHomeLocation().getY();
        double lng = user.getHomeLocation().getX();

        // [ 제외 대상 User Id 수집 ]
        // 1. 차단한 사용자
        Collection<UUID> excludedUserIdCollection = new ArrayList<>(blockRepository.findRelatedUserIds(userId));
        // 2. 본인 id 넣어서 문법에러 막음
        excludedUserIdCollection.add(userId);
        // 3. 이미 친구인 견주
        excludedUserIdCollection.addAll(friendshipRepository.findFriendUserIds(userId));

        List<RecommendationCandidate> candidates =  dogRepository.findCandidates(
                userId,
                lat, lng,
                maxRadiusKm * 1000,
                excludedUserIdCollection,
                recommendationProperties.candidateCap()
        );

        List<RecommendationResponse.item> items = candidates.stream()
                .map(c -> new RecommendationResponse.item(
                        null, // dog
                        null, // owner
                        (int) Math.round(c.getDistanceMeters() / 1000.0),
                        null, // reason
                        "none" // requestStatus
                ))
                .toList();

        return RecommendationResponse.page.of(items, null, maxRadiusKm);
    }

}
