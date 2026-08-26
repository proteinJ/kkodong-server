package com.kkodong.server.domain.safety.service;

import com.kkodong.server.domain.safety.domain.Report;
import com.kkodong.server.domain.safety.domain.ReportReason;
import com.kkodong.server.domain.safety.domain.ReportStatus;
import com.kkodong.server.domain.safety.domain.TargetType;
import com.kkodong.server.domain.safety.dto.ReportRequest;
import com.kkodong.server.domain.safety.dto.ReportResponse;
import com.kkodong.server.domain.safety.repository.ReportRepository;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 신고 접수(SAFETY-2). Phase 1은 관리자 UI 없이 {@code reports} 테이블 적재만 하고,
 * 개발자가 Supabase 대시보드에서 직접 status를 갱신한다(API_SPEC.md 11절).
 *
 * <p><b>신고는 자동 차단으로 이어지지 않는다</b> — 오남용 방지(TICKETS_MVP.md SAFETY-2).
 * 클라이언트가 "차단도 함께" 체크박스를 제공한다면 POST /reports 와 POST /blocks 를
 * 각각 호출하는 방식이어야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReportResponse.detailInfo report(UUID reporterId, ReportRequest.create request) {

        TargetType targetType = parseTargetType(request.targetType());
        ReportReason reason = parseReason(request.reason());

        // 대상 존재 검증은 target_type 마다 다른 테이블을 봐야 한다. reports.target_id 는
        // 다형 참조라 FK 를 걸 수 없어(V4 주석) Service 가 유일한 방어선이다.
        // community_post/comment 는 COMMUNITY-1 에서 테이블이 생긴 뒤에 연다 —
        // 지금 통과시키면 존재하지 않는 UUID 가 그대로 적재된다.
        if (targetType != TargetType.USER) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_REPORT_TARGET);
        }

        if (request.targetId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        if (!userRepository.existsById(request.targetId())) {
            throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
        }

        // 처리 대기 중 중복 신고 차단. DB 의 uq_reports_pending 과 이중 방어이며,
        // 여기서 걸러야 500/뭉개진 메시지가 아니라 409 가 나간다.
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                reporterId, targetType, request.targetId(), ReportStatus.PENDING)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        Report saved = reportRepository.save(Report.builder()
                .reporterId(reporterId)
                .targetType(targetType)
                .targetId(request.targetId())
                .reason(reason)
                .details(request.details())
                .build());

        return ReportResponse.detailInfo.from(saved);
    }

    /** 요청은 소문자 스네이크, enum 은 대문자. 변환 실패는 500 이 아니라 400 이어야 한다. */
    private TargetType parseTargetType(String raw) {
        try {
            return TargetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_TARGET_TYPE);
        }
    }

    private ReportReason parseReason(String raw) {
        try {
            return ReportReason.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_REASON);
        }
    }
}
