package com.kkodong.server.domain.dog.domain;

import java.util.Arrays;

/**
 * 강아지 성향 태그 — 친구 추천 점수의 25점짜리 신호
 * (FRIEND_RECOMMENDATION_SPEC.md 1절, 2026-08-15 확정).
 *
 * <p>DB에는 enum 이름이 아니라 <b>한글 라벨 문자열 배열</b>로 저장한다
 * (예: {@code ["활발함","사교적"]}) — 명세·클라이언트·DB가 같은 값을 보게 하기 위함.
 *
 * <p><b>검증 분담(명세 확정)</b>: 개수 제한(≤3)은 DB CHECK 제약
 * ({@code chk_dogs_personality_traits}), 값 유효성은 이 enum이 담당한다.
 * 태그 세트는 실사용 데이터로 바뀔 수 있어(의미가 겹치는 태그 병합 등)
 * DB 제약에 문자열을 박아두지 않는다.
 */
public enum PersonalityTrait {

    ACTIVE("활발함"),
    CALM("차분함"),
    SOCIABLE("사교적"),
    SHY("낯가림"),
    TIMID("겁많음"),
    PLAYFUL("장난꾸러기"),
    INDEPENDENT("독립적"),
    AFFECTIONATE("애교많음");

    private final String label;

    PersonalityTrait(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static boolean isValidLabel(String label) {
        return Arrays.stream(values()).anyMatch(trait -> trait.label.equals(label));
    }
}
