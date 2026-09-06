package com.kkodong.server.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 견주 관련 설정. 현재는 산책 시간대뿐이다.
 *
 * <p>{@link DogProperties}(성향 태그)와 같은 역할이지만 한 가지가 다르다:
 * 성향 태그는 <b>키가 곧 DB에 저장되는 값이자 표시값</b>인 반면, 시간대는
 * <b>키가 API·DB 계약이고 값이 표시용</b>이다. 그래서 자유롭게 바꿀 수 있는 것은
 * 표시 이름뿐이고 키는 아니다 — 아래 키 검증이 그 계약을 지킨다.
 */
@ConfigurationProperties(prefix = "kkodong.user")
@Validated
public record UserProperties(@Valid @NotNull WalkTimeSlot walkTimeSlot) {

    public record WalkTimeSlot(@NotEmpty Map<String, String> slots) {

        /**
         * API_SPEC.md 0절이 정한 6개. users.walk_time_slots(JSONB)에 이 키가 그대로
         * 저장되므로 목록이 어긋나면 데이터가 오염된다.
         */
        private static final Set<String> REQUIRED_KEYS =
                Set.of("dawn", "morning", "noon", "afternoon", "evening", "night");

        /**
         * ⚠️ 기동을 막는 것이 맞다. 성향 태그(한글)와 달리 키가 영문이라
         * {@code "moring"} 같은 오타가 눈에 띄지 않는데, 잘못된 키로 뜨면 오염된 값이
         * 쌓이고 그건 나중에 마이그레이션으로만 고칠 수 있다.
         */
        public WalkTimeSlot {
            if (slots != null && !slots.keySet().equals(REQUIRED_KEYS)) {
                throw new IllegalStateException(
                        "kkodong.user.walk-time-slot.slots 키가 API 명세와 다릅니다. "
                                + "기대=" + REQUIRED_KEYS + ", 실제=" + slots.keySet());
            }
        }

        /**
         * 입력 검증용. 온보딩·프로필에서 받은 값이 전부 정의된 슬롯인지 확인한다.
         *
         * <p>단건이 아니라 컬렉션을 받는 이유: 호출부마다 루프를 반복해서 쓰게 되고,
         * 그러면 "하나라도 틀리면 거부"라는 규칙이 호출부에 흩어진다.
         * BusinessException이 커스텀 메시지를 받지 않아 어느 값이 틀렸는지는
         * 어차피 응답에 실리지 않으므로, boolean만 돌려주면 충분하다.
         *
         * <p>null은 통과시킨다 — PATCH에서 "변경하지 않음"을 뜻한다.
         */
        public boolean areValidKeys(Collection<String> keys) {
            return keys == null || slots.keySet().containsAll(keys);
        }

        /** 추천 이유 문장용. {@code evening} → {@code 저녁} ("저녁에 산책하는 것도 같아요") */
        public String displayOf(String key) {
            return slots.get(key);
        }
    }
}
