package com.kkodong.server.domain.assignment.repository;

import com.kkodong.server.domain.assignment.domain.StaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, UUID> {

    /** PN-13 배정 현황 — 오늘 이 매장의 배정 전부. 마릿수 카운트도 여기서 나온다. */
    List<StaffAssignment> findAllByMerchantIdAndAssignedDate(UUID merchantId, LocalDate assignedDate);

    /**
     * 한 강아지는 하루에 한 선생님이므로 (원생, 날짜)로 유일하다
     * — DB의 uq_staff_assignments가 보장한다. 재배정 시 기존 행을 찾는 데 쓴다.
     */
    Optional<StaffAssignment> findByEnrollmentIdAndAssignedDate(UUID enrollmentId, LocalDate assignedDate);

    List<StaffAssignment> findAllByEnrollmentIdInAndAssignedDate(
            List<UUID> enrollmentIds, LocalDate assignedDate);
}
