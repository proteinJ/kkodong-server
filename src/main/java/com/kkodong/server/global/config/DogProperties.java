package com.kkodong.server.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "kkodong.dog")
@Validated
public record DogProperties(@Valid @NotNull Personality personality,
                            @Valid @NotNull Breed breed) {

    public record Personality(@NotEmpty Map<String, TagForms> tags) {
        /**
         * 입력 검증용. 받은 태그가 전부 정의된 목록에 있는지 확인한다.
         * 단건이 아니라 컬렉션을 받는 이유는 {@code UserProperties.WalkTimeSlot} 참조.
         *
         * <p>null은 통과시킨다 — PATCH에서 "변경하지 않음"을 뜻한다.
         */
        public boolean areValidLabels(Collection<String> labels) {
            return labels == null || tags.keySet().containsAll(labels);
        }

        /** 나열용 어간. "활발·사교적인 성격이…"의 앞쪽 */
        public String stemOf(String label) {
            return tags.get(label).stem();
        }

        /** 명사 수식형. "둘 다 활발한 성격이에요" */
        public String adnominalOf(String label) {
            return tags.get(label).adnominal();
        }
    }

    public record TagForms(@NotBlank String stem, @NotBlank String adnominal) {}

    /**
     * 견종 목록과 그룹. {@code config/breed-groups.yml} 에서 바인딩된다.
     *
     * <p>레코드가 아니라 클래스인 이유: 견종→그룹 역방향 맵을 <b>기동 시 한 번</b> 만들어
     * 들고 있어야 하는데, 레코드는 컴포넌트 외의 인스턴스 필드를 가질 수 없다.
     * 매 조회마다 전체 그룹을 훑는 것도 동작은 하지만, 추천 한 번에 후보 수만큼
     * 반복되는 조회다.
     */
    public static final class Breed {

        public record Group(@NotBlank String display, @NotEmpty List<String> breeds) {}

        /** 이 두 그룹은 '같은 그룹'으로 묶지 않는다 — 항상 중립 취급이다. 근거는 breed-groups.yml 주석. */
        public static final Set<String> UNPAIRABLE = Set.of("mixed", "etc");
        /** 목록에 없는 견종의 그룹 이름. other 끼리는 같은 그룹으로 치지 않는다. */
        public static final String OTHER = "other";

        private final Map<String, Group> groups;
        private final Map<String, String> groupByBreed;

        public Breed(@NotEmpty Map<String, Group> groups) {
            this.groups = groups;
            Map<String, String> index = new HashMap<>();
            groups.forEach((key, group) -> group.breeds().forEach(b -> {
                String previous = index.put(b, key);
                if (previous != null) {
                    // 기동을 막는 것이 맞다. 중복이 있으면 어느 그룹이 이기는지가
                    // Map 순회 순서에 달리게 되어 추천 점수가 조용히 달라진다.
                    throw new IllegalStateException(
                            "견종 '" + b + "' 가 두 그룹에 중복 정의됐습니다: " + previous + ", " + key);
                }
            }));
            this.groupByBreed = Map.copyOf(index);
        }

        /** 그룹 키 → (표시명 + 견종 목록). 나열 순서가 곧 선택 화면의 섹션 순서다. */
        public Map<String, Group> groups() {
            return groups;
        }

        /** 선택 화면의 섹션 제목. 없는 그룹이면 null. */
        public String displayOf(String groupKey) {
            Group group = groups.get(groupKey);
            return group == null ? null : group.display();
        }

        /** 클라이언트에 내려줄 선택지 전체. 자유 입력을 받지 않으므로 이 목록이 곧 계약이다. */
        public Set<String> allBreeds() {
            return groupByBreed.keySet();
        }

        /** 입력 검증용. null 은 통과시킨다 — PATCH 에서 "변경하지 않음"을 뜻한다. */
        public boolean isValid(String breed) {
            return breed == null || groupByBreed.containsKey(breed);
        }

        /** 목록에 없으면 {@link #OTHER}. breed_score 계산의 입력이다. */
        public String groupOf(String breed) {
            return groupByBreed.getOrDefault(breed, OTHER);
        }

        /** 그룹 매칭에서 제외되는 견종인가 (믹스·기타·미입력). */
        public boolean isUnpairable(String breed) {
            return breed == null || UNPAIRABLE.contains(groupOf(breed));
        }

    }
}
