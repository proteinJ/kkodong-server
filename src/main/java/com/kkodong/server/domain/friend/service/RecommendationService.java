package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.dto.DogResponse;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.friend.domain.Candidate;
import com.kkodong.server.domain.friend.domain.Scored;
import com.kkodong.server.domain.friend.domain.Subject;
import com.kkodong.server.domain.friend.dto.RecommendationResponse;
import com.kkodong.server.domain.friend.repository.FriendshipRepository;
import com.kkodong.server.domain.friend.repository.RecommendationCandidate;
import com.kkodong.server.domain.safety.repository.BlockRepository;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.dto.UserResponse;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.config.RecommendationProperties;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

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
    private final RecommendationScorer recommendationScorer;

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

        //  ① RECALL — 반경 확장은 4단계에서 이 호출을 루프로 감싼다
        List<RecommendationCandidate> rows =  dogRepository.findCandidates(
                userId,
                lat, lng,
                maxRadiusKm * 1000,
                excludedUserIdCollection,
                recommendationProperties.candidateCap()
        );

        // ② HYDRATE — 1단계
        Pool pool = hydrate(rows);
        Subject me = Subject.of(dog, user);

        // ③ RANK — 2단계
//        long seed = Objects.hash(userId, LocalDate.now());
//        List<Scored> ranked = recommendationScorer.rankAll(me, pool.candidates, seed);

        // ④ PAGE — 4단계: offset 부터 limit 까지
//        List<Scored> pageItems = ranked.stream().skip(offset).limit(limit).toList();

        // ⑤ PRESENT — 3단계
        List<RecommendationResponse.item> items = pool.candidates.stream()
                .map(c -> new RecommendationResponse.item(
                        DogResponse.publicInfo.from(pool.dogs.get(c.subject().dogId())), // dog
                        UserResponse.summary.from(pool.owners.get(c.subject().ownerId())), // owner
                        (int) Math.round(c.distanceMeters() / 1000.0),
                        null, // reason
                        "none" // requestStatus
                ))
                .toList();

        return RecommendationResponse.page.of(items, null, maxRadiusKm);
    }

    private Pool hydrate(List<RecommendationCandidate> rows) {
        Map<UUID, Dog> dogs = dogRepository.findAllById(rows.stream().map(RecommendationCandidate::getDogId).toList())
                .stream().collect(toMap(Dog::getId, Function.identity()));
        Map<UUID, User> owners = userRepository.findAllById(rows.stream().map(RecommendationCandidate::getOwnerId).toList())
                .stream().collect(toMap(User::getId, Function.identity()));

        List<Candidate> candidates = rows.stream()
                .map(r -> new Candidate(Subject.of(dogs.get(r.getDogId()), owners.get(r.getOwnerId())),
                        r.getDistanceMeters())).toList();

        return new Pool(candidates, dogs, owners);
    }

    private record Pool(List<Candidate> candidates, Map<UUID, Dog> dogs, Map<UUID, User> owners) {}

}
