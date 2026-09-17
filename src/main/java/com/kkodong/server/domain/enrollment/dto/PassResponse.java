package com.kkodong.server.domain.enrollment.dto;

import com.kkodong.server.domain.enrollment.domain.Pass;
import com.kkodong.server.domain.enrollment.domain.PassEntryType;
import com.kkodong.server.domain.enrollment.domain.PassLedger;
import com.kkodong.server.domain.enrollment.domain.PassStatus;
import com.kkodong.server.domain.merchant.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class PassResponse {

    /** 이용권 한 건(PN-12, KG-13). */
    public record detailInfo(
            @Schema(description = "이용권 ID") UUID id,
            @Schema(description = "원생 ID") UUID enrollmentId,
            @Schema(description = "발급 근거 상품 ID") UUID productId,
            @Schema(description = "발급 시점 상품명 스냅샷") String productName,
            @Schema(description = "상품 유형") ProductType productType,
            @Schema(description = "발급 시점 가격(원)") Integer price,
            @Schema(description = "잔여 회차. 기간권이면 null") Integer remainingCount,
            @Schema(description = "총 회차. 기간권이면 null") Integer totalCount,
            @Schema(description = "만료일. null이면 무기한") LocalDate expiresOn,
            @Schema(description = "상태") PassStatus status,
            @Schema(description = "오늘 사용 가능한지. 상태·잔여·만료를 모두 본 결과")
            Boolean usableToday,
            @Schema(description = "만료까지 남은 일수. 무기한이면 null. 이미 지났으면 음수")
            Long daysUntilExpiry,
            @Schema(description = "발급 시각") OffsetDateTime issuedAt
    ) {
        public static detailInfo from(Pass p, LocalDate today) {
            return new detailInfo(
                    p.getId(), p.getEnrollmentId(), p.getProductId(),
                    p.getProductNameSnapshot(), p.getProductType(), p.getPriceSnapshot(),
                    p.getRemainingCount(), p.getTotalCount(), p.getExpiresOn(), p.getStatus(),
                    p.isUsableOn(today),
                    p.getExpiresOn() == null ? null
                            : java.time.temporal.ChronoUnit.DAYS.between(today, p.getExpiresOn()),
                    p.getIssuedAt()
            );
        }
    }

    /**
     * 변동 이력 한 줄(PN-12 감사 로그, KG-13 차감 이력).
     *
     * <p>이 목록을 처음부터 더하면 현재 잔여가 나와야 한다 — 어긋나면 어느 지점에서
     * 깨졌는지 {@code balanceAfter}로 찾는다.
     */
    public record ledgerItem(
            @Schema(description = "이력 ID") UUID id,
            @Schema(description = "변동 유형") PassEntryType entryType,
            @Schema(description = "회차 변동량. 차감은 음수, 기간 연장은 0") Integer delta,
            @Schema(description = "적용 직후 잔여. 기간권이면 null") Integer balanceAfter,
            @Schema(description = "사유") String reason,
            @Schema(description = "처리자 유저 ID. 시스템 처리면 null") UUID actorUserId,
            @Schema(description = "이 변동을 일으킨 등원 기록 ID. 차감·복원에만 있다")
            UUID sourceAttendanceId,
            @Schema(description = "발생 시각") OffsetDateTime createdAt
    ) {
        public static ledgerItem from(PassLedger l) {
            return new ledgerItem(l.getId(), l.getEntryType(), l.getDelta(), l.getBalanceAfter(),
                    l.getReason(), l.getActorUserId(), l.getSourceAttendanceId(), l.getCreatedAt());
        }
    }
}
