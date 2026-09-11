package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.DogSize;
import com.kkodong.server.domain.friend.domain.*;
import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.config.RecommendationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.round;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * 6개 신호 점수 → 합산 · 동점 셔플
 */
@RequiredArgsConstructor
@Component
public class RecommendationScorer {

    private final DogProperties dogProperties;
    private final RecommendationProperties recommendationProperties;

    private MatchFacts facts(Subject me, Candidate c) {
        Subject other = c.subject();
        return new MatchFacts(
                c.distanceMeters(),
                shared(me.personalityTraits(), other.personalityTraits()),
                shared(me.walkTimeSlots(), other.walkTimeSlots()),
                ageDiffMonths(me.birthDate(), other.birthDate()),
                sizeStepDiff(me.size(), other.size()),
                breedRelation(me.breed(), other.breed())
        );
    }

    /**
     * 견종 관계 판정. UNPAIRABLE(믹스·기타·미입력)을 <b>가장 먼저</b> 본다 —
     * 뒤로 밀면 둘 다 "믹스"일 때 SAME(1.0)이 되어버린다. 명세는 믹스를 항상 0.3으로 정했다.
     *
     * <p>목록에 없는 견종(other)끼리는 같은 그룹으로 치지 않는다 — "분류가 안 된 견종"이라는
     * 이유로 서로 비슷하다고 볼 근거가 없다. 견종 검증(D005) 이후로는 잘 생기지 않는
     * 경우지만 기존 데이터 대비 방어로 남긴다. mine 만 봐도 되는 이유는, mine 이 other 가
     * 아니면서 theirs 가 other 면 아래 그룹 비교에서 어차피 어긋나기 때문이다.
     */
    private BreedRelation breedRelation(String mine, String theirs) {
        DogProperties.Breed breeds = dogProperties.breed();
        if (breeds.isUnpairable(mine) || breeds.isUnpairable(theirs)) {
            return BreedRelation.UNPAIRABLE;
        } else if (mine.equals(theirs)) {
            return BreedRelation.SAME;
        } else if (breeds.groupOf(mine).equals(DogProperties.Breed.OTHER)) {
            return BreedRelation.DIFFERENT;
        } else if (breeds.groupOf(mine).equals(breeds.groupOf(theirs))) {
            return BreedRelation.SAME_GROUP;
        } else {
            return BreedRelation.DIFFERENT;
        }
    }

    /** 체급 단계 차이 0/1/2. enum 의 compareTo 는 ordinal 차이라 SMALL·MEDIUM·LARGE 선언 순서에 기댄다. */
    private Integer sizeStepDiff(DogSize mine, DogSize theirs) {
        if (mine == null || theirs == null) { return null; }   // 미입력 → 중립(0.5)
        return abs(mine.compareTo(theirs));
    }

    /** 개월 수 차이의 절댓값. abs 를 빼면 순서에 따라 음수가 나와 점수가 1을 넘는다. */
    private Integer ageDiffMonths(LocalDate mine, LocalDate theirs) {
        if (mine == null || theirs == null) { return null; }   // 미입력 → 중립(0.5)
        return Math.abs(Math.toIntExact(ChronoUnit.MONTHS.between(mine, theirs)));
    }

    /**
     * [ 겹치는 항목 ] <b>null</b> = 한쪽이라도 미입력(→ 중립 0.5),
     * <b>[]</b> = 양쪽 다 입력했으나 겹치는 것 없음(→ 0.0).
     * 이 둘을 구분하지 않으면 태그를 건너뛴 신규 유저가 추천 하위에 영구히 깔린다.
     */
    private List<String> shared(List<String> mine, List<String> theirs) {
        if (mine.isEmpty() || theirs.isEmpty()) { return null; }
        return mine.stream().filter(new HashSet<>(theirs)::contains).toList();
    }


    public List<Scored> rankAll(Subject me, List<Candidate> candidates, long shuffleSeed) {
        // 가중합
        List<Scored> scored = candidates.stream()
                .map(c -> score(me, c))
                .toList();

        // 반올림 - score 소수점 첫째 자리에서 반올림 -> 정수
        Map<Long, List<Scored>> groups = scored.stream()
                .collect(groupingBy(s -> round(s.total()), () -> new TreeMap<Long, List<Scored>>(Comparator.reverseOrder()), toList()));

        // 셔플 - 같은 정수 점수 그룹안에서 셔플(정렬)
        Random random = new Random(shuffleSeed);
        List<Scored> ranked = new ArrayList<>();

        for (List<Scored> group : groups.values()) {
            List<Scored> shuffled = new ArrayList<>(group);
            Collections.shuffle(shuffled, random);
            ranked.addAll(shuffled); // ranked에 추가
        }
        return ranked;
    }

    private Scored score(Subject me, Candidate c) {
        MatchFacts f = facts(me, c);
        Subject other = c.subject();

        // 1. 거리 (30점)
        double distance_s = distanceScore(f.distanceMeters());
        // 2. 시간대 (15점)
        double timeSlot_s = slotScore(f.sharedTimeSlots(), me.walkTimeSlots(), other.walkTimeSlots());
        // 3. 성격 (20점)
        double personality_s = slotScore(f.sharedTraits(), me.personalityTraits(), other.personalityTraits());
        // 4. 나이 (15점)
        double age_s = ageScore(f.ageDiffMonths());
        // 5. 체급 (12점)
        double size_s = sizeScore(f.sizeStepDiff());
        // 6. 견종 (8점)
        double breed_s = breedScore(f.breed());

        RecommendationProperties.Weights w = recommendationProperties.weights();
        double total = w.distance() * distance_s
                + w.timeSlot() * timeSlot_s
                + w.personality() * personality_s
                + w.age() * age_s
                + w.size() * size_s
                + w.breed() * breed_s;

        return new Scored(other, total, f);
    }

    private double breedScore(BreedRelation relation) {
        return switch (relation) {
            case SAME -> 1.0;
            case SAME_GROUP -> 0.6;
            case DIFFERENT -> 0.2;
            case UNPAIRABLE -> 0.3;
        };
    }

    private double sizeScore(Integer sizeStepDiff) {
        if (sizeStepDiff == null) { return recommendationProperties.neutralScore(); }
        return switch (sizeStepDiff) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            default -> 0.0;
        };
    }

    private double ageScore(Integer ageDiffMonths) {
        if (ageDiffMonths == null) { return recommendationProperties.neutralScore(); }
        return Math.max(0, 1 - ((double) ageDiffMonths/ recommendationProperties.ageToleranceMonths()));
    }

    private double slotScore(List<String> sharedList, List<String> myList, List<String> otherList) {
        if (sharedList == null) { return recommendationProperties.neutralScore(); }
        return (double) sharedList.size() / Math.min(myList.size(), otherList.size());
    }

    private double distanceScore(double meters) {
        double km = meters / 1000.0;
        return Math.max(0, 1 - km / recommendationProperties.maxRadiusKm());
    }

}
