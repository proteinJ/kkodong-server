package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.PassLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PassLedgerRepository extends JpaRepository<PassLedger, UUID> {

    /** KG-13 차감 이력 · PN-12 감사 로그 — 항상 시간 역순으로 읽는다. */
    List<PassLedger> findAllByPassIdOrderByCreatedAtDesc(UUID passId);

    /** 되돌리기 시 이 등원으로 생긴 차감 행을 찾는다. */
    List<PassLedger> findAllBySourceAttendanceId(UUID sourceAttendanceId);
}
