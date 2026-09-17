package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /** PN-10 원생 목록. 상태 필터가 기본으로 걸린다. */
    List<Enrollment> findAllByMerchantIdAndStatus(UUID merchantId, EnrollmentStatus status);

    /** 견주 앱 KG-09 "내 유치원". 탈퇴로 ownerUserId가 null이 된 행은 잡히지 않는다. */
    List<Enrollment> findAllByOwnerUserId(UUID ownerUserId);

    /**
     * 이 강아지가 이 매장에 이미 재원 중인지(PN-07 승인 전 검사).
     *
     * <p>승인은 원생을 만드는 행위라, 중복 승인하면 같은 강아지의 원생이 둘로 갈린다.
     * 이용권과 출석이 각각 다른 원생에 붙어 어느 쪽이 진짜인지 알 수 없게 된다.
     *
     * <p>DB의 {@code uq_enrollments_active_dog}와 같은 규칙을 서버에서 먼저 봐서
     * 500 대신 409를 준다. 퇴원(WITHDRAWN)은 제외해야 재등록이 가능하다.
     */
    boolean existsByMerchantIdAndDogIdAndStatusNot(
            UUID merchantId, UUID dogId, EnrollmentStatus status);

    /**
     * 원생 목록 한 화면 분(PN-10). 원생 + 이용권 집계 + 출석 집계를 한 번에 읽는다.
     *
     * <p><b>왜 한 쿼리인가</b>: FR-PN10-01의 정렬 기준(결제 임박·최근 등원·등원 빈도)이
     * 전부 다른 테이블의 집계값이다. 원생을 먼저 읽고 건별로 조회하면 원생 수만큼
     * 쿼리가 늘고(N+1), 그러고도 정렬을 못 한다.
     *
     * <p>서브쿼리로 미리 접은 뒤 LEFT JOIN 하는 이유: 원생 테이블에 바로 조인하면
     * 이용권 N개 × 출석 M개의 곱집합이 만들어져 합계가 부풀려진다.
     *
     * <p>⚠️ 잔여 합계는 <b>횟수권만</b> 센다. 기간권은 회차 개념이 없어 0으로 합치면
     * "다 썼음"과 구분되지 않는다.
     *
     * <p>정렬과 검색은 서버 메모리에서 한다 — 이유는 EnrollmentQueryService 참조.
     */
    @Query(value = """
            SELECT e.id                                AS id,
                   e.dog_id                            AS dogId,
                   e.owner_user_id                     AS ownerUserId,
                   e.dog_name_snapshot                 AS dogName,
                   e.dog_breed_snapshot                AS dogBreed,
                   e.owner_name_snapshot               AS ownerName,
                   e.status                            AS status,
                   e.enrolled_on                       AS enrolledOn,
                   p.remaining_count                   AS remainingCount,
                   p.nearest_expiry                    AS nearestExpiry,
                   COALESCE(p.active_count, 0) > 0     AS hasActivePass,
                   a.last_attended_on                  AS lastAttendedOn,
                   COALESCE(a.attended_count, 0)       AS attendedCount
            FROM enrollments e
            LEFT JOIN (
                SELECT enrollment_id,
                       SUM(remaining_count) FILTER (WHERE product_type = 'count') AS remaining_count,
                       MIN(expires_on)                                            AS nearest_expiry,
                       COUNT(*)                                                   AS active_count
                FROM passes
                WHERE status = 'active'
                GROUP BY enrollment_id
            ) p ON p.enrollment_id = e.id
            LEFT JOIN (
                SELECT enrollment_id,
                       MAX(attendance_date) AS last_attended_on,
                       COUNT(*)             AS attended_count
                FROM attendances
                WHERE status = 'attended'
                GROUP BY enrollment_id
            ) a ON a.enrollment_id = e.id
            WHERE e.merchant_id = :merchantId
            """, nativeQuery = true)
    List<EnrollmentRow> findRowsByMerchantId(@Param("merchantId") UUID merchantId);
}
