package com.kkodong.server.global.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 enum 컨버터가 계약을 지키는지 <b>전수</b> 검사한다.
 *
 * <p><b>왜 필요한가</b>: 컨버터는 {@code autoApply = true}로 조용히 적용된다. 하나가 빠지거나
 * 잘못 붙으면 Hibernate가 그 enum을 ordinal(정수)로 저장하는데, 컬럼이 TEXT라 그 순간
 * DB의 CHECK 제약에 걸린다 — 즉 <b>실행 중에야</b> 드러난다. 그리고 어느 enum이 문제인지는
 * 스택트레이스만 봐서는 알기 어렵다.
 *
 * <p>도메인 테스트들은 자기 도메인의 enum만 지나가므로, 안 쓰이는 값이나 테스트가 없는
 * 도메인의 컨버터는 아무도 확인하지 않는다. 여기서 클래스패스를 뒤져 공통 베이스를 쓰는
 * <b>모든</b> 컨버터의 <b>모든</b> enum 상수를 왕복시킨다.
 *
 * <p>새 enum을 추가하면 이 테스트가 자동으로 그것까지 검사한다 — 목록을 손으로 관리하지 않는다.
 */
class EnumConverterContractTest {

    private static final String BASE_PACKAGE = "com.kkodong.server";

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @DisplayName("모든 컨버터가 소문자로 저장하고 원래 값으로 되읽는다")
    void everyConverterRoundTripsAllConstants() throws Exception {
        List<Class<?>> converters = findConverters();

        // 컨버터가 갑자기 사라지면(패키지 이동·삭제) 이 테스트가 조용히 0건을 검사하게 된다.
        assertThat(converters)
                .as("컨버터를 하나도 찾지 못했다 — 스캔 대상 패키지가 바뀌었는지 확인할 것")
                .isNotEmpty();

        for (Class<?> type : converters) {
            AttributeConverter converter =
                    (AttributeConverter) type.getDeclaredConstructor().newInstance();

            assertThat(type.getAnnotation(Converter.class))
                    .as("[%s] @Converter(autoApply = true)가 없으면 Hibernate가 이 컨버터를 쓰지 않고 "
                        + "enum을 ordinal(정수)로 저장한다", type.getSimpleName())
                    .isNotNull()
                    .extracting(Converter::autoApply).isEqualTo(true);

            Class<? extends Enum> enumType = enumTypeOf(type);
            for (Enum<?> constant : enumType.getEnumConstants()) {
                Object stored = converter.convertToDatabaseColumn(constant);

                // DB의 CHECK 제약은 소문자로 쓰여 있다 — 대문자로 저장하면 INSERT가 막힌다.
                assertThat(stored)
                        .as("[%s] %s 는 소문자로 저장돼야 한다", type.getSimpleName(), constant)
                        .isEqualTo(constant.name().toLowerCase());

                assertThat(converter.convertToEntityAttribute(stored))
                        .as("[%s] %s 왕복 실패", type.getSimpleName(), constant)
                        .isEqualTo(constant);
            }

            // null은 양방향 모두 null이어야 한다 — nullable 컬럼(dogs.gender 등)이 여럿 있다.
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }

    /**
     * 점주 앱 도메인의 패키지들. 이 테스트가 상속을 강제하는 범위다.
     *
     * <p>견주 앱 도메인({@code dog}, {@code safety} 등)은 담당자가 다르므로
     * (CONTRIBUTING.md 1절) 여기서 규칙을 강제하지 않는다. 그쪽 컨버터가 같은 베이스를
     * 쓰는 편이 낫다고 판단되면 그 영역 담당자가 결정할 일이다.
     */
    private static final List<String> PARTNER_PACKAGES = List.of(
            "com.kkodong.server.domain.merchant",
            "com.kkodong.server.domain.enrollment",
            "com.kkodong.server.domain.reservation",
            "com.kkodong.server.domain.note",
            "com.kkodong.server.domain.media",
            "com.kkodong.server.domain.assignment",
            "com.kkodong.server.domain.review");

    @Test
    @DisplayName("점주 앱 컨버터는 전부 공통 베이스를 상속한다 — 변환 규칙이 한 곳에만 있어야 한다")
    void everyPartnerConverterExtendsCommonBase() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(AttributeConverter.class));

        Set<BeanDefinition> all = PARTNER_PACKAGES.stream()
                .flatMap(pkg -> provider.findCandidateComponents(pkg).stream())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(all)
                .as("점주 앱 패키지에서 컨버터를 찾지 못했다 — 패키지가 옮겨졌는지 확인할 것")
                .isNotEmpty();

        for (BeanDefinition definition : all) {
            Class<?> type = load(definition.getBeanClassName());
            if (type.equals(LowercaseEnumConverter.class)) continue;

            // 복붙된 컨버터가 다시 생기면 여기서 걸린다. 소문자 규칙이 아닌 변환이 정말
            // 필요해지면 그때 이 테스트를 고치면서 왜 예외인지 남기면 된다.
            assertThat(LowercaseEnumConverter.class.isAssignableFrom(type))
                    .as("[%s] LowercaseEnumConverter를 상속하지 않는다. 변환 로직이 복붙되면 "
                        + "한 곳만 고쳤을 때 나머지와 조용히 어긋난다", type.getSimpleName())
                    .isTrue();
        }
    }

    private List<Class<?>> findConverters() {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AssignableTypeFilter(LowercaseEnumConverter.class));

        return provider.findCandidateComponents(BASE_PACKAGE).stream()
                .<Class<?>>map(d -> load(d.getBeanClassName()))
                .filter(c -> !c.equals(LowercaseEnumConverter.class))
                .toList();
    }

    /** 서브클래스의 제네릭 파라미터에서 enum 타입을 꺼낸다. */
    @SuppressWarnings("unchecked")
    private Class<? extends Enum> enumTypeOf(Class<?> converterType) {
        var superType = (java.lang.reflect.ParameterizedType) converterType.getGenericSuperclass();
        return (Class<? extends Enum>) superType.getActualTypeArguments()[0];
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
