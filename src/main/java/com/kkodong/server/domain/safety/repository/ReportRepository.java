package com.kkodong.server.domain.safety.repository;

import com.kkodong.server.domain.safety.domain.Report;
import com.kkodong.server.domain.safety.domain.ReportStatus;
import com.kkodong.server.domain.safety.domain.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    /**
     * 같은 신고자가 같은 대상을 <b>처리 대기 중에</b> 또 신고했는지.
     * 처리 완료(reviewed/action_taken/dismissed) 후 재신고는 허용한다.
     *
     * <p>DB의 부분 유니크 인덱스({@code uq_reports_pending}, V4)와 같은 규칙을 서버에서도
     * 검사하는 이유는 <b>409를 내려주기 위해서</b>다. 이 검사를 빼면 제약 위반이
     * {@code DataIntegrityViolationException}으로 올라가 메시지가 뭉개진다.
     * (동시 요청으로 이 검사를 통과해 충돌하는 경우는 GlobalExceptionHandler가 409로 받는다.)
     */
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            UUID reporterId, TargetType targetType, UUID targetId, ReportStatus status);
}
