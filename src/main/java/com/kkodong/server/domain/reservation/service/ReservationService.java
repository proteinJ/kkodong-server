package com.kkodong.server.domain.reservation.service;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.domain.KindergartenProfile;
import com.kkodong.server.domain.merchant.repository.KindergartenProfileRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.reservation.domain.*;
import com.kkodong.server.domain.reservation.dto.ReservationRequest;
import com.kkodong.server.domain.reservation.dto.ReservationResponse;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.reservation.repository.ReservationRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 예약 관리(PN-19)와 예약 → 등원 전환.
 *
 * <p>권한 검증은 {@link MerchantAccessGuard}를 통한다(PC-12 — RLS 없음).
 * 출석 권한({@code attendance})을 가진 스태프면 예약을 다룰 수 있게 했다 —
 * 예약 확인·승인은 현장에서 선생님이 하는 일이라 원장 전용으로 묶으면 매장이 돌지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    /** 매장 로컬 날짜 기준. 지금은 한국만 서비스하므로 상수로 둔다 — 해외 확장 시 매장별 설정으로 뺄 것. */
    private static final ZoneId MERCHANT_ZONE = ZoneId.of("Asia/Seoul");

    private final ReservationRepository reservationRepository;
    private final AttendanceRepository attendanceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final KindergartenProfileRepository kindergartenProfileRepository;
    private final MerchantAccessGuard accessGuard;
    private final MerchantDayLock merchantDayLock;

    /**
     * 점주가 대신 등록하는 예약(PN-19). 전화로 받은 예약을 넣는 경로다.
     *
     * <p>넣는 즉시 CONFIRMED가 된다 — 점주가 직접 넣었다는 것 자체가 승인이므로
     * 자기 예약을 다시 승인하게 만들 이유가 없다. 그래서 정원 검사도 여기서 함께 한다.
     */
    @Transactional
    public ReservationResponse.confirmResult createByPartner(
            UUID merchantId, UUID userId, ReservationRequest.create request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Enrollment enrollment = findEnrollmentInMerchant(merchantId, request.enrollmentId());
        if (!enrollment.isReservable()) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_ACTIVE);
        }
        if (reservationRepository.existsActiveForEnrollmentOn(enrollment.getId(), request.serviceDate())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESERVATION);
        }

        // ⚠️ 정원 검사보다 먼저 락을 잡는다. 락 없이 세면 동시 요청이 둘 다 통과한다.
        merchantDayLock.acquire(merchantId, request.serviceDate());
        ensureCapacityAvailable(merchantId, request.serviceDate());

        OffsetDateTime startsAt = request.isAllDay()
                ? request.serviceDate().atStartOfDay(MERCHANT_ZONE).toOffsetDateTime()
                : request.startsAt();
        OffsetDateTime endsAt = request.isAllDay()
                ? request.serviceDate().plusDays(1).atStartOfDay(MERCHANT_ZONE).toOffsetDateTime()
                : request.endsAt();
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD);
        }

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .merchantId(merchantId)
                .dogId(enrollment.getDogId())
                .requestedByUserId(enrollment.getOwnerUserId())
                .dogNameSnapshot(enrollment.getDogNameSnapshot())
                .enrollmentId(enrollment.getId())
                .serviceDate(request.serviceDate())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .isAllDay(request.isAllDay())
                .source(ReservationSource.PARTNER)
                .note(request.note())
                .build());

        reservation.confirm(userId);
        UUID attendanceId = createScheduledAttendance(reservation);

        return new ReservationResponse.confirmResult(
                ReservationResponse.detailInfo.from(reservation), attendanceId, null);
    }

    /**
     * 예약 승인(PN-19). ★ 여기가 예약 → 등원 전환 지점이다.
     *
     * <p>승인과 등원 예정 생성은 하나의 트랜잭션이다. 승인만 되고 등원 예정이 안 생기면
     * 그 예약은 출석 대시보드(PN-08)에 영영 나타나지 않고, 점주는 예약이 있다고 믿는데
     * 현장에는 없는 상태가 된다.
     *
     * <p>이용권은 여기서 차감하지 않는다 — 등원 확정(PN-09) 시점에 차감한다.
     * 그 대가로 "승인은 됐는데 등원일에 잔여 0"이 가능한데, 이는 정상 상황이며
     * 응답의 passWarning과 출석 체크 시 제외(FR-PN09-02)로 받는다.
     */
    @Transactional
    public ReservationResponse.confirmResult confirm(UUID merchantId, UUID userId, UUID reservationId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Reservation reservation = findReservationInMerchant(merchantId, reservationId);

        // 락이 가장 먼저다 — 상태 판정과 정원 검사가 같은 임계 구역 안에 있어야
        // 같은 예약에 승인 요청이 두 번 들어와도 한쪽만 통과한다.
        merchantDayLock.acquire(merchantId, reservation.getServiceDate());

        // ⚠️ 정원보다 상태를 먼저 본다. 이미 확정된 예약은 스스로 정원을 차지하고 있어서,
        //    순서가 반대면 재승인 시 "정원 초과(RV004)"라는 엉뚱한 답이 나간다 —
        //    점주는 버튼을 두 번 눌렀을 뿐인데 그날이 꽉 찼다는 안내를 받는다.
        reservation.ensureConfirmable();
        ensureCapacityAvailable(merchantId, reservation.getServiceDate());

        reservation.confirm(userId);
        UUID attendanceId = createScheduledAttendance(reservation);

        return new ReservationResponse.confirmResult(
                ReservationResponse.detailInfo.from(reservation), attendanceId, null);
    }

    /** 예약 거절(PN-19). 사유는 견주에게 그대로 전달된다. */
    @Transactional
    public ReservationResponse.detailInfo reject(
            UUID merchantId, UUID userId, UUID reservationId, ReservationRequest.reject request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Reservation reservation = findReservationInMerchant(merchantId, reservationId);
        reservation.reject(userId, request.reason());
        return ReservationResponse.detailInfo.from(reservation);
    }

    /**
     * 예약 취소(PN-19). 승인 후에도 취소할 수 있다 — 당일 사정으로 못 오는 일이 정상이다.
     *
     * <p>승인된 예약을 취소하면 함께 만들어진 등원 예정도 접는다. 이미 등원한 뒤라면
     * 등원 기록은 건드리지 않는다 — 온 사실과 차감된 회차를 되돌리는 것은 취소가 아니라
     * 되돌리기(PN-09)의 몫이고 거기엔 이용권 복원이 따라붙어야 한다.
     */
    @Transactional
    public ReservationResponse.detailInfo cancel(
            UUID merchantId, UUID userId, UUID reservationId, ReservationRequest.cancel request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Reservation reservation = findReservationInMerchant(merchantId, reservationId);
        reservation.cancel(userId, request.reason());

        attendanceRepository.findByReservationId(reservationId)
                .ifPresent(Attendance::cancelIfScheduled);

        return ReservationResponse.detailInfo.from(reservation);
    }

    /** 예약 상세(PN-19). */
    public ReservationResponse.detailInfo get(UUID merchantId, UUID userId, UUID reservationId) {
        accessGuard.requireStaff(merchantId, userId);
        return ReservationResponse.detailInfo.from(findReservationInMerchant(merchantId, reservationId));
    }

    /** PN-19 캘린더 — 기간별 예약 목록. */
    public List<ReservationResponse.detailInfo> getCalendar(
            UUID merchantId, UUID userId, LocalDate from, LocalDate to) {
        accessGuard.requireStaff(merchantId, userId);
        return reservationRepository
                .findAllByMerchantIdAndServiceDateBetweenOrderByServiceDateAscStartsAtAsc(merchantId, from, to)
                .stream()
                .map(ReservationResponse.detailInfo::from)
                .toList();
    }

    /** 상태별 목록(PN-19 승인 대기함, 홈 배지). */
    public List<ReservationResponse.detailInfo> getByStatus(
            UUID merchantId, UUID userId, ReservationStatus status) {
        accessGuard.requireStaff(merchantId, userId);
        return reservationRepository
                .findAllByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, status)
                .stream()
                .map(ReservationResponse.detailInfo::from)
                .toList();
    }

    /** 날짜별 집계(PN-19). 정원 대비 얼마나 찼는지 보여준다. */
    public ReservationResponse.dailySummary getDailySummary(
            UUID merchantId, UUID userId, LocalDate date) {
        accessGuard.requireStaff(merchantId, userId);

        long requested = reservationRepository.countByStatuses(
                merchantId, date, Set.of(ReservationStatus.REQUESTED));
        long confirmed = countOccupied(merchantId, date);
        Integer capacity = kindergartenProfileRepository.findById(merchantId)
                .map(KindergartenProfile::getDailyCapacity)
                .orElse(null);

        // 정원이 무제한이면 남은 자리도 무제한이라 null이다. 초과 상태(음수)는 0으로 보여준다 —
        // 수동 조정 등으로 정원을 넘은 뒤 "남은 자리 -2"를 그리면 화면이 이상해진다.
        Integer remaining = capacity == null ? null : (int) Math.max(0, capacity - confirmed);

        return new ReservationResponse.dailySummary(date, requested, confirmed, capacity, remaining);
    }

    /**
     * 정원이 남았는지 확인한다.
     *
     * <p>⚠️ 반드시 {@link MerchantDayLock#acquire} 이후에 부를 것 — 락 없이 세면
     * 동시 승인이 둘 다 통과한다.
     */
    private void ensureCapacityAvailable(UUID merchantId, LocalDate date) {
        Integer capacity = kindergartenProfileRepository.findById(merchantId)
                .map(KindergartenProfile::getDailyCapacity)
                .orElse(null);
        if (capacity == null) return; // 무제한 (미용실·병원은 프로필 자체가 없다)

        if (countOccupied(merchantId, date) >= capacity) {
            throw new BusinessException(ErrorCode.DAILY_CAPACITY_EXCEEDED);
        }
    }

    private long countOccupied(UUID merchantId, LocalDate date) {
        return reservationRepository.countByStatuses(merchantId, date,
                Set.of(ReservationStatus.CONFIRMED, ReservationStatus.COMPLETED));
    }

    /**
     * 예약 승인에 따른 등원 예정 생성. PN-08 대시보드에 나타나는 것이 이 행이다.
     *
     * @return 생성된 등원 예정 ID. 유치원이 아니면(원생 없음) null
     */
    private UUID createScheduledAttendance(Reservation reservation) {
        if (reservation.getEnrollmentId() == null) {
            return null; // 미용실·병원은 등원 개념이 없다 — 예약이 곧 이용이다
        }
        Attendance attendance = attendanceRepository.save(Attendance.builder()
                .merchantId(reservation.getMerchantId())
                .enrollmentId(reservation.getEnrollmentId())
                .reservationId(reservation.getId())
                .attendanceDate(reservation.getServiceDate())
                .status(AttendanceStatus.SCHEDULED)
                .build());
        return attendance.getId();
    }

    /** ⚠️ 남의 매장 예약 ID를 넣어도 통과하지 않게 소속을 대조한다. */
    private Reservation findReservationInMerchant(UUID merchantId, UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return reservation;
    }

    private Enrollment findEnrollmentInMerchant(UUID merchantId, UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!enrollment.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        return enrollment;
    }
}
