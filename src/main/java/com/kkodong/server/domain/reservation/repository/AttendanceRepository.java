package com.kkodong.server.domain.reservation.repository;

import com.kkodong.server.domain.reservation.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    /**
     * 예약에서 전환된 등원 예정을 찾는다. 예약이 취소될 때 함께 접기 위해 쓴다.
     *
     * <p>DB의 {@code uq_attendances_reservation} 부분 유니크가 예약당 하나임을 보장하므로
     * Optional로 받는다 — 둘 이상이면 그건 제약이 깨진 것이라 조회가 아니라 사고다.
     */
    Optional<Attendance> findByReservationId(UUID reservationId);

    /** PN-08 출석 대시보드 — 오늘 이 매장의 전부. 점주 앱 첫 화면의 질의다. */
    List<Attendance> findAllByMerchantIdAndAttendanceDate(UUID merchantId, LocalDate attendanceDate);
}
