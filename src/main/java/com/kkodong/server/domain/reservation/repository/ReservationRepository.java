package com.kkodong.server.domain.reservation.repository;

import com.kkodong.server.domain.reservation.domain.Reservation;
import com.kkodong.server.domain.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** PN-19 캘린더 — 매장의 기간별 예약. 날짜·시작시각 순으로 그대로 그린다. */
    List<Reservation> findAllByMerchantIdAndServiceDateBetweenOrderByServiceDateAscStartsAtAsc(
            UUID merchantId, LocalDate from, LocalDate to);

    /** PN-19 상태 필터(승인 대기함 등). */
    List<Reservation> findAllByMerchantIdAndStatusOrderByCreatedAtDesc(
            UUID merchantId, ReservationStatus status);

    /** PN-08 오늘 현황 — 하루치. */
    List<Reservation> findAllByMerchantIdAndServiceDateOrderByStartsAtAsc(
            UUID merchantId, LocalDate serviceDate);

    /**
     * 일일 정원 검사용 카운트.
     *
     * <p>⚠️ 반드시 {@code MerchantDayLock.acquire()} 이후에 부를 것. 락 없이 세면
     * 동시 승인이 둘 다 통과한다 — 그 이유는 MerchantDayLock 주석 참조.
     */
    @Query("""
            select count(r) from Reservation r
            where r.merchantId = :merchantId
              and r.serviceDate = :serviceDate
              and r.status in :statuses
            """)
    long countByStatuses(@Param("merchantId") UUID merchantId,
                         @Param("serviceDate") LocalDate serviceDate,
                         @Param("statuses") Collection<ReservationStatus> statuses);

    /**
     * 같은 원생이 같은 날 살아있는 예약을 이미 갖고 있는지.
     *
     * <p>DB의 {@code uq_reservations_active_per_day}와 같은 규칙을 서버에서 먼저 본다 —
     * 제약에 걸리면 500이 나가지만 여기서 걸면 409로 알려줄 수 있다.
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.enrollmentId = :enrollmentId
              and r.serviceDate = :serviceDate
              and r.status in (
                    com.kkodong.server.domain.reservation.domain.ReservationStatus.REQUESTED,
                    com.kkodong.server.domain.reservation.domain.ReservationStatus.CONFIRMED,
                    com.kkodong.server.domain.reservation.domain.ReservationStatus.COMPLETED)
            """)
    boolean existsActiveForEnrollmentOn(@Param("enrollmentId") UUID enrollmentId,
                                        @Param("serviceDate") LocalDate serviceDate);
}
