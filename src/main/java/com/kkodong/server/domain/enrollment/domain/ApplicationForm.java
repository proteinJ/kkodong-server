package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 신청서 양식(PN-06, FR-PN06-01). 점주가 구성하고 <b>견주 앱(KG-06)이 렌더링</b>한다.
 *
 * <p><b>왜 버전을 남기는가</b>: 양식을 고친 뒤 과거 제출값을 보면 어떤 질문에 대한 답인지
 * 알 수 없게 된다. "기타 특이사항"이 3번 항목이었는데 지금은 5번이면 값이 엉뚱한 질문에
 * 붙는다. 그래서 양식은 수정하지 않고 새 버전을 만들며, 제출본은 자신이 쓴 버전을 가리킨다.
 *
 * <p>활성 양식은 매장당 하나뿐이다 — DB의 부분 유니크 인덱스가 강제한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "application_forms")
public class ApplicationForm {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private Integer version;

    /**
     * 추가 질문 정의. {@code [{"key":"pickup","label":"픽업 필요","type":"boolean","required":false}]}
     * 항목 구조가 점주 자유 구성이라 컬럼으로 펼 수 없다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_fields", nullable = false)
    @Builder.Default
    private List<ExtraField> extraFields = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 새 버전이 나오면 이전 버전을 내린다.
     *
     * <p>지우지 않는 이유: 이 버전으로 제출된 신청서들이 참조하고 있고,
     * 그 제출값을 해석하려면 당시 질문 목록이 필요하다.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 점주가 구성하는 추가 질문 한 개.
     *
     * @param key      제출값 맵의 키. ⚠️ 버전 안에서 유일해야 답이 엉뚱한 질문에 붙지 않는다
     * @param label    견주에게 보이는 질문 문구
     * @param type     입력 형태. text·number·boolean·select (값 유효성은 서버 enum)
     * @param required 필수 응답 여부
     * @param options  select일 때의 선택지
     */
    public record ExtraField(
            String key,
            String label,
            String type,
            Boolean required,
            List<String> options
    ) {}
}
