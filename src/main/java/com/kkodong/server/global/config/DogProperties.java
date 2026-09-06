package com.kkodong.server.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Map;

@ConfigurationProperties(prefix = "kkodong.dog")
@Validated
public record DogProperties(@Valid @NotNull Personality personality) {

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
}
