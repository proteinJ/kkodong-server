package com.kkodong.server.global.common;

import jakarta.persistence.AttributeConverter;

/**
 * enum ↔ 소문자 문자열 변환의 공통 규칙.
 *
 * <p><b>왜 이 클래스가 있는가</b>: 이 프로젝트의 enum은 전부 DB에 소문자 문자열로 저장된다
 * (마이그레이션의 {@code CHECK (status IN ('pending', 'active', ...))}와 짝을 이룬다).
 * 그 변환 로직이 컨버터마다 복붙되어 스무 곳에 같은 네 줄이 있었다 — 한 곳만 고치면
 * 나머지 열아홉 곳과 조용히 어긋나고, 그 어긋남은 DB CHECK 제약에 걸려서야 드러난다.
 *
 * <p><b>왜 서브클래스는 여전히 필요한가</b>: {@code @Converter(autoApply = true)}는 변환 대상
 * 타입을 제네릭 파라미터에서 읽는다. 타입 소거 때문에 이 추상 클래스 자체에는 붙일 수 없고,
 * enum마다 구체 타입을 박은 서브클래스가 있어야 한다. 다만 그 서브클래스는 생성자 한 줄이면
 * 되므로, 실제 규칙은 여기 한 곳에만 존재한다.
 *
 * <p>사용법:
 * <pre>
 * &#64;Converter(autoApply = true)
 * public class StaffRoleConverter extends LowercaseEnumConverter&lt;StaffRole&gt; {
 *     public StaffRoleConverter() { super(StaffRole.class); }
 * }
 * </pre>
 *
 * <p>⚠️ 엔티티 필드에 {@code @Enumerated}를 붙이지 말 것. 붙이면 컨버터가 무시되고
 * ordinal(정수) 저장으로 조용히 바뀐다 — enum 순서를 바꾸는 순간 기존 데이터의 의미가
 * 통째로 어긋난다(Report.java에 남아 있던 경고).
 *
 * @param <E> 변환할 enum 타입
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected LowercaseEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    /**
     * ⚠️ DB에 정의되지 않은 값이 있으면 {@link IllegalArgumentException}이 난다.
     * 그대로 두는 것이 맞다 — 조용히 null로 바꾸면 잘못된 데이터가 정상인 척 흘러다닌다.
     * 값 세트를 바꿀 때는 enum과 DB CHECK 제약을 함께 옮겨야 한다.
     */
    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(enumType, dbData.toUpperCase());
    }
}
