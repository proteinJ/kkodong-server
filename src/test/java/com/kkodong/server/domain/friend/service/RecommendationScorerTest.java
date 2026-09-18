package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.DogSize;
import com.kkodong.server.domain.friend.domain.Candidate;
import com.kkodong.server.domain.friend.domain.Scored;
import com.kkodong.server.domain.friend.domain.Subject;
import com.kkodong.server.support.PropertiesFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 점수 계산과 동점 셔플. 스프링도 DB도 필요 없다 — 값 객체({@code Subject})로 분리해 둔 보상이다.
 *
 * <p>특히 <b>동점 셔플은 실제 데이터로 검증할 수 없다.</b> 로컬 시드 5마리의 점수가
 * 87/67/65/46/36 으로 전부 달라 동점 그룹이 생기지 않는다. 같은 점수를 직접 만들어
 * 넣어야만 확인되는 동작이다.
 */
class RecommendationScorerTest {

    private final RecommendationScorer scorer =
            new RecommendationScorer(PropertiesFixture.dog(), PropertiesFixture.recommendation());

    private static final LocalDate BIRTH = LocalDate.of(2021, 6, 1);

    private static Subject me() {
        return new Subject(UUID.randomUUID(), UUID.randomUUID(), "푸들", BIRTH, DogSize.SMALL,
                List.of("활발함", "사교적"), List.of("evening", "morning"));
    }

    /** me 와 모든 신호가 같은 상대. */
    private static Subject twin() {
        return new Subject(UUID.randomUUID(), UUID.randomUUID(), "푸들", BIRTH, DogSize.SMALL,
                List.of("활발함", "사교적"), List.of("evening", "morning"));
    }

    /** 프로필을 아무것도 채우지 않은 상대. */
    private static Subject blank() {
        return new Subject(UUID.randomUUID(), UUID.randomUUID(), "믹스", null, null,
                List.of(), List.of());
    }

    @Test
    @DisplayName("모든 신호가 일치하고 거리 0이면 만점(100)")
    void perfectScore() {
        List<Scored> ranked = scorer.rankAll(me(), List.of(new Candidate(twin(), 0)), 1L);

        // 거리 30 + 시간대 15 + 성격 20 + 나이 15 + 체급 12 + 견종 8
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).total()).isCloseTo(100.0, within(0.0001));
    }

    @Test
    @DisplayName("프로필이 비어 있어도 0점이 아니라 중립(0.5)으로 채점한다")
    void missingDataIsNeutralNotZero() {
        // 0 으로 처리하면 온보딩에서 태그를 건너뛴 신규 유저가 추천 하위에 영구히 깔린다.
        List<Scored> ranked = scorer.rankAll(me(), List.of(new Candidate(blank(), 5000)), 1L);

        // 거리 0 + 시간대 7.5 + 성격 10 + 나이 7.5 + 체급 6 + 견종 2.4(UNPAIRABLE)
        assertThat(ranked.get(0).total()).isCloseTo(33.4, within(0.0001));
    }

    @Test
    @DisplayName("반경 상한을 넘어도 거리 점수가 음수로 내려가지 않는다")
    void distanceScoreIsClampedAtZero() {
        // 8km 면 1 - 8/5 = -0.6 이다. 음수를 막지 않으면 감점이 되어 다른 신호를 깎는다.
        List<Scored> ranked = scorer.rankAll(me(), List.of(new Candidate(twin(), 8000)), 1L);

        assertThat(ranked.get(0).total()).isCloseTo(70.0, within(0.0001));  // 100 - 거리 30점
    }

    @Test
    @DisplayName("점수가 높은 순으로 정렬된다")
    void sortedByScoreDescending() {
        List<Scored> ranked = scorer.rankAll(me(), List.of(
                new Candidate(blank(), 5000),   // 33.4
                new Candidate(twin(), 0),       // 100.0
                new Candidate(twin(), 8000)     // 70.0
        ), 1L);

        assertThat(ranked).extracting(Scored::total)
                .containsExactly(100.0, 70.0, 33.4);
    }

    @Test
    @DisplayName("같은 시드면 동점 그룹의 순서가 재현된다")
    void sameSeedGivesSameOrder() {
        // 페이징이 이 성질에 기댄다. 요청마다 순서가 바뀌면 2페이지에 중복·누락이 생긴다.
        List<Candidate> tied = tiedCandidates();

        assertThat(dogIds(scorer.rankAll(me(), tied, 42L)))
                .isEqualTo(dogIds(scorer.rankAll(me(), tied, 42L)));
    }

    @Test
    @DisplayName("시드가 다르면 동점 그룹의 순서가 달라진다")
    void differentSeedsGiveDifferentOrders() {
        // 완전히 결정적이면 상위에 뜬 강아지가 응답하지 않아도 계속 상위에 남아 화면이 정체된다.
        List<Candidate> tied = tiedCandidates();

        Set<List<UUID>> orders = new HashSet<>();
        for (long seed = 0; seed < 30; seed++) {
            orders.add(dogIds(scorer.rankAll(me(), tied, seed)));
        }

        assertThat(orders).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("셔플해도 후보가 사라지거나 중복되지 않는다")
    void shufflePreservesEveryCandidate() {
        List<Candidate> tied = tiedCandidates();

        List<UUID> ranked = dogIds(scorer.rankAll(me(), tied, 7L));

        assertThat(ranked).hasSize(tied.size())
                .containsExactlyInAnyOrderElementsOf(
                        tied.stream().map(c -> c.subject().dogId()).toList());
    }

    /** 점수가 전부 같아 하나의 동점 그룹이 되는 후보들. */
    private static List<Candidate> tiedCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            candidates.add(new Candidate(twin(), 0));
        }
        return candidates;
    }

    private static List<UUID> dogIds(List<Scored> ranked) {
        return ranked.stream().map(s -> s.subject().dogId()).toList();
    }
}
