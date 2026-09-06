package com.kkodong.server.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 설정 바인딩 회귀 테스트.
 *
 * <p>왜 필요한가: {@code @ConfigurationProperties}는 바인딩에 실패해도 조용히 null을
 * 남기는 경우가 있어(키 이름이 어긋난 경우 등) 런타임에야 드러난다. 특히 한글 맵 키는
 * Spring의 ConfigurationPropertyName 제약(소문자·숫자·'-'만 허용) 때문에
 * {@code "[활발함]"} 대괄호 표기가 필요한데, 이걸 빠뜨리면 키가 뭉개진다.
 */
@SpringBootTest
@ActiveProfiles("local")
class PropertiesBindingTest {

    @Autowired DogProperties dogProperties;
    @Autowired RecommendationProperties recommendationProperties;
    @Autowired UserProperties userProperties;

    @Test
    @DisplayName("성향 태그가 8종 전부 바인딩되고 두 표시형을 갖는다")
    void dogTagsBind() {
        var p = dogProperties.personality();
        assertThat(p.tags()).hasSize(8);
        assertThat(p.areValidLabels(List.of("활발함", "사교적"))).isTrue();
        assertThat(p.areValidLabels(List.of("활발함", "없는태그"))).isFalse();
        assertThat(p.areValidLabels(List.of())).isTrue();   // 빈 배열 허용
        assertThat(p.areValidLabels(null)).isTrue();        // PATCH 미변경
        assertThat(p.stemOf("사교적")).isEqualTo("사교적");
        assertThat(p.adnominalOf("사교적")).isEqualTo("사교적인");
    }

    @Test
    @DisplayName("산책 시간대 6종이 바인딩되고 표시형을 갖는다")
    void walkTimeSlotsBind() {
        var w = userProperties.walkTimeSlot();
        assertThat(w.slots()).hasSize(6);
        assertThat(w.areValidKeys(List.of("morning", "evening"))).isTrue();
        assertThat(w.areValidKeys(List.of("evening", "moring"))).isFalse();  // 오타 하나라도 있으면 거부
        assertThat(w.areValidKeys(List.of())).isTrue();
        assertThat(w.displayOf("evening")).isEqualTo("저녁");
    }

    @Test
    @DisplayName("슬롯 키가 명세와 다르면 기동을 막는다")
    void walkTimeSlotKeysAreContract() {
        assertThatThrownBy(() -> new UserProperties.WalkTimeSlot(Map.of("moring", "아침")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API 명세와 다릅니다");
    }

    @Test
    @DisplayName("추천 가중치가 바인딩되고 합계가 100이다")
    void weightsBind() {
        var w = recommendationProperties.weights();
        assertThat(w.distance()).isEqualTo(30);
        assertThat(w.timeSlot()).isEqualTo(15);
        assertThat(w.distance() + w.timeSlot() + w.personality()
                + w.age() + w.size() + w.breed() + w.gender() + w.neutered())
                .isEqualTo(100);
        assertThat(recommendationProperties.radiusStepsKm()).containsExactly(1, 2, 3, 5);
        // ⚠️ 요청 limit 상한(RecommendationController @Max(100))보다 커야 한다. 같거나 작으면
        //    후보를 거리순으로 limit개만 뽑는 셈이 되어 거리가 하드 필터가 된다(API_SPEC 2.1 규약 1).
        assertThat(recommendationProperties.candidateCap()).isGreaterThan(100);
    }
}
