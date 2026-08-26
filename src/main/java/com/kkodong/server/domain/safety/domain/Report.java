package com.kkodong.server.domain.safety.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "target_type", nullable = false)
    private TargetType targetType; // TargetTypeConverter가 자동 적용됨(autoApply)
    // ⚠️ @Enumerated를 붙이면 컨버터가 무시되고 ordinal(정수) 저장으로 조용히 바뀐다. 붙이지 말 것.

    @Column(nullable = false)
    private ReportReason reason; // ReportReasonConverter 자동 적용

    private String details;

    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING; // ReportStatusConverter 자동 적용

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
