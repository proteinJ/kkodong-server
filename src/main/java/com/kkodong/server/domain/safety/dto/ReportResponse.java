package com.kkodong.server.domain.safety.dto;

import com.kkodong.server.domain.safety.domain.Report;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public class ReportResponse {

    /**
     * 접수 시각(createdAt)은 넣지 않는다 — Report.createdAt은 DB DEFAULT now()가 채우므로
     * INSERT 직후 엔티티에는 null이다. 값을 채우려면 저장 후 재조회가 필요한데, 클라이언트가
     * 접수 시각을 쓸 화면이 없다(Phase 1은 신고 목록 조회 API 자체가 없다).
     * BlockResponse.detailInfo와 같은 판단.
     */
    public record detailInfo(
            @Schema(description = "접수된 신고 ID") UUID reportId,
            @Schema(description = "처리 상태", example = "pending") String status
    ) {
        public static detailInfo from(Report report) {
            return new detailInfo(
                    report.getId(),
                    report.getStatus().name().toLowerCase()
            );
        }
    }
}
