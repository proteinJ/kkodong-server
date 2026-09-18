package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.dto.DogResponse;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.friend.domain.Candidate;
import com.kkodong.server.domain.friend.domain.CursorState;
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

import java.time.LocalDate;
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
    private final RecommendationReasonBuilder recommendationReasonBuilder;

    /*
    decode → 반경 확인 → 제외 목록 → recall(첫 페이지만 반경 확장) → 후보 없으면 반환 → hydrate → rank → 페이지 자르기와 encode → 응답
     */
    public RecommendationResponse.page recommend(UUID userId, UUID dogId, int limit, String cursor) {
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
        CursorState state = RecommendationCursor.decode(cursor);
        if (state != null && !recommendationProperties.radiusStepsKm().contains(state.radiusKm())) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }

        double lat = user.getHomeLocation().getY();
        double lng = user.getHomeLocation().getX();

        // [ 제외 대상 User Id 수집 ]
        // 1. 차단한 사용자
        Collection<UUID> excludedUserIdCollection = new ArrayList<>(blockRepository.findRelatedUserIds(userId));
        // 2. 추천에서 본인을 빼는 용도 + 제외 목록이 비어서 NOT IN()이 SQL 오류 내는것 막음
        excludedUserIdCollection.add(userId);
        // 3. 이미 친구인 견주
        excludedUserIdCollection.addAll(friendshipRepository.findFriendUserIds(userId));

        //  ① RECALL — 반경 확장
        List<Integer> radiusSteps = (state == null)
                ? recommendationProperties.radiusStepsKm() : List.of(state.radiusKm());
        Recall recall = recall(userId, lat, lng, excludedUserIdCollection, radiusSteps, limit);
        if (recall.rows().isEmpty()) { // 후보가 없으면 바로 반환
            return RecommendationResponse.page.of(recall.radiusKm());
        }

        // ② HYDRATE
        Pool pool = hydrate(recall.rows());
        Subject me = Subject.of(dog, user);

        // ③ RANK
        long seed = Objects.hash(userId, LocalDate.now());
        List<Scored> ranked = recommendationScorer.rankAll(me, pool.candidates(), seed);

        // ④ PAGE: offset 부터 limit 까지
        int offset = (state == null) ? 0 : state.offset();
        List<Scored> pageItems = ranked.stream().skip(offset).limit(limit).toList();

        String nextCursor = (offset + limit < ranked.size())
                ? RecommendationCursor.encode(new CursorState(recall.radiusKm(), offset + limit)) : null;

        // ⑤ PRESENT
        List<RecommendationResponse.item> items = pageItems.stream()
                .map(s -> new RecommendationResponse.item(
                        DogResponse.publicInfo.from(pool.dogs().get(s.subject().dogId())), // dog
                        UserResponse.summary.from(pool.owners().get(s.subject().ownerId())), // owner
                        (int) Math.round(s.facts().distanceMeters() / 1000.0),
                        recommendationReasonBuilder.build(s.facts(), s.subject()), // reason
                        "none" // requestStatus
                ))
                .toList();

        return RecommendationResponse.page.of(items, nextCursor, recall.radiusKm());
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
    private record Recall(List<RecommendationCandidate> rows, int radiusKm) {}

    private Recall recall(UUID userId, double lat, double lng, Collection<UUID> excludedUserIds, List<Integer> radiusSteps, int limit) {
        List<RecommendationCandidate> rows = List.of();
        int appliedRadiusKm = radiusSteps.get(radiusSteps.size() - 1);

        for (int radiusKm : radiusSteps) {
            rows = dogRepository.findCandidates(
                    userId,
                    lat, lng,
                    radiusKm * 1000,
                    excludedUserIds,
                    recommendationProperties.candidateCap()
            );

            appliedRadiusKm = radiusKm;

            if (rows.size() >= limit) break;
        }
        return new Recall(rows, appliedRadiusKm);
    }
}
