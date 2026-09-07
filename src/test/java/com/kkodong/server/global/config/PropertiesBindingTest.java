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
    @DisplayName("견종 목록이 별도 파일(config/breed-groups.yml)에서 바인딩된다")
    void breedGroupsBind() {
        var b = dogProperties.breed();
        // spring.config.import 로 읽어들인 파일이라, 이 단언이 곧 import 동작 확인이다.
        // ⚠️ containsExactly 는 순서까지 본다 — 그룹 나열 순서가 곧 선택 화면의 섹션
        //    순서이고, Spring 의 Map 바인딩 순서 보존은 문서화된 계약이 아니라서 고정한다.
        assertThat(b.groups().keySet()).containsExactly("small_companion", "terrier",
                "sporting", "herding", "korean", "mixed", "etc");
        assertThat(b.displayOf("small_companion")).isEqualTo("소형 반려견");
        assertThat(b.displayOf("korean")).isEqualTo("한국 견종");
        assertThat(b.displayOf("없는그룹")).isNull();
        assertThat(b.allBreeds()).hasSizeGreaterThan(80).contains("말티즈", "진돗개", "믹스", "기타");

        assertThat(b.groupOf("말티즈")).isEqualTo("small_companion");
        assertThat(b.groupOf("진돗개")).isEqualTo("korean");
        assertThat(b.groupOf("없는견종")).isEqualTo(DogProperties.Breed.OTHER);

        assertThat(b.isValid("말티즈")).isTrue();
        assertThat(b.isValid("몰티즈")).isFalse();   // 표기 변형은 허용하지 않는다 — 선택지에서 고르게 한다
        assertThat(b.isValid(null)).isTrue();        // PATCH 미변경

        // 믹스·기타·미입력은 그룹 매칭에서 빠진다(항상 중립). breed_score 의 전제다.
        assertThat(b.isUnpairable("믹스")).isTrue();
        assertThat(b.isUnpairable("기타")).isTrue();
        assertThat(b.isUnpairable(null)).isTrue();
        assertThat(b.isUnpairable("말티즈")).isFalse();
    }

    @Test
    @DisplayName("견종이 두 그룹에 중복되면 기동을 막는다")
    void duplicateBreedFailsFast() {
        // 슈나우저가 두 그룹에 들어간 상황. 실제 yml 이 이렇게 되면 기동이 실패해야 한다 —
        // 어느 그룹이 이기는지가 Map 순회 순서에 달리면 추천 점수가 조용히 달라진다.
        assertThatThrownBy(() -> new DogProperties.Breed(Map.of(
                "terrier",  new DogProperties.Breed.Group("테리어", List.of("슈나우저")),
                "sporting", new DogProperties.Breed.Group("사냥·스포팅", List.of("슈나우저")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("중복 정의");
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
