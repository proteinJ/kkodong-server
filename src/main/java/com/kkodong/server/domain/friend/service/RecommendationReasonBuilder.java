package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.friend.domain.BreedRelation;
import com.kkodong.server.domain.friend.domain.MatchFacts;
import com.kkodong.server.domain.friend.domain.Subject;
import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.config.RecommendationProperties;
import com.kkodong.server.global.config.UserProperties;
import com.kkodong.server.global.util.KoreanParticle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * reason 문장 조립 (템플릿 · 우선순위)
 */
@Component
@RequiredArgsConstructor
public class RecommendationReasonBuilder {

    private final DogProperties dogProperties;
    private final RecommendationProperties recommendationProperties;
    private final UserProperties userProperties;

    private String timeSlotClause(MatchFacts f) {
        List<String> slots = f.sharedTimeSlots();
        if (slots == null || slots.isEmpty()) {
            return null;
        }

        String slotNames = slots.stream()
                .map(s -> userProperties.walkTimeSlot().displayOf(s))
                .collect(Collectors.joining("·"));

        return slotNames + "에 산책하는 것도 같아요";
    }

    private String personalityClause(MatchFacts f) {
        List<String> traits = f.sharedTraits();

        if (traits == null || traits.isEmpty()) {
            return null;
        }

        if (recommendationProperties.reason().personalityManyTags() <= f.sharedTraits().size()) {
            String traitsNames = traits.subList(0, traits.size()-1).stream()
                    .map(t -> dogProperties.personality().stemOf(t))
                    .collect(Collectors.joining("·"));

            String lastTraitName = traits.get(traits.size()-1);
            lastTraitName = dogProperties.personality().adnominalOf(lastTraitName);
            return String.join("·", traitsNames, lastTraitName) + " 성격이 우리 아이랑 닮았어요";
        } else {
            String sharedTrait = traits.get(0);
            return "둘 다 " + dogProperties.personality().adnominalOf(sharedTrait) + " 성격이에요";
        }
    }

    private String breedClause(MatchFacts f, Subject other) {
        BreedRelation relation = f.breed();

        return switch (relation) {
            case SAME -> "우리 아이랑 같은 " + KoreanParticle.iyeyo(other.breed());
            case SAME_GROUP -> "우리 아이랑 비슷한 종류예요";
            case DIFFERENT, UNPAIRABLE -> null;
        };
    }

    private String ageClause(MatchFacts f) {
        Integer ageDiffMonths = f.ageDiffMonths();
        if (ageDiffMonths == null) {
            return null;
        }

        if (ageDiffMonths <= recommendationProperties.reason().ageVeryCloseMonths()) {
            return "나이가 거의 같아요";
        } else if (ageDiffMonths <= recommendationProperties.reason().ageCloseMonths()) {
            return "나이가 비슷해요";
        } else {
            return null;
        }
    }

    private String sizeClause(MatchFacts f) {
        Integer sizeDiffStep = f.sizeStepDiff();

        if (sizeDiffStep == null) {
          return null;
        } else if (sizeDiffStep == 0) {
            return "체급이 비슷해서 같이 놀기 좋아요";
        }

        return null;
    }

    public String build(MatchFacts f, Subject other) {
        String resultClause = Stream.of(
                    timeSlotClause(f),
                    personalityClause(f),
                    breedClause(f, other),
                    ageClause(f),
                    sizeClause(f))
                .filter(Objects::nonNull)
                .limit(recommendationProperties.reason().maxClauses())
                .collect(Collectors.joining(", "));

        if (resultClause.isEmpty()) {
            return distanceClause(f);
        }

        return resultClause;
    }

    private String distanceClause(MatchFacts f) {
        double km = f.distanceMeters() / 1000.0;
        if (km <= recommendationProperties.reason().walkDistanceKm()) {
            return "걸어서 만날 수 있는 거리예요";
        } else {
            return "우리 동네 가까이 사는 친구예요";
        }
    }

}
