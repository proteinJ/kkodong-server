package com.kkodong.server.domain.reservation.service;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.domain.Pass;
import com.kkodong.server.domain.enrollment.domain.PassLedger;
import com.kkodong.server.domain.enrollment.domain.PassStatus;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.repository.PassLedgerRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.dto.AttendanceRequest;
import com.kkodong.server.domain.reservation.dto.AttendanceResponse;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.reservation.repository.ReservationRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 출석 대시보드(PN-08)와 출석 체크(PN-09).
 *
 * <p>예약 승인으로 쌓인 등원 예정(attendances)을 실제로 소비하는 쪽이다.
 * 등원 확정과 이용권 차감의 원자성은 {@link AttendanceCheckInProcessor}가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final ReservationRepository reservationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PassRepository passRepository;
    private final PassLedgerRepository passLedgerRepository;
    private final MerchantAccessGuard accessGuard;
    private final AttendanceCheckInProcessor checkInProcessor;

    /**
     * PN-08 출석 대시보드. 점주 앱 로그인 직후 첫 화면의 질의다.
     *
     * <p>각 항목에 "지금 등원 처리할 수 있는가"를 함께 계산해 내려준다(FR-PN09-02).
     * 이용권이 만료·소진된 원생을 목록에서 감추지 않고 이유와 함께 보여주는 이유는,
     * 점주가 "왜 이 아이가 안 보이지"를 겪는 대신 "이용권을 발급해야겠다"로 바로 가게
     * 하려는 것이다.
     */
    public AttendanceResponse.dashboard getDashboard(UUID merchantId, UUID userId, LocalDate date) {
        accessGuard.requireStaff(merchantId, userId);

        List<Attendance> attendances =
                attendanceRepository.findAllByMerchantIdAndAttendanceDate(merchantId, date);
        if (attendances.isEmpty()) {
            return new AttendanceResponse.dashboard(date, 0, 0, 0, 0, 0, List.of());
        }

        // 원생·이용권을 건별로 조회하면 N+1이 된다. id를 모아 한 번에 읽는다.
        List<UUID> enrollmentIds = attendances.stream().map(Attendance::getEnrollmentId).distinct().toList();
        Map<UUID, Enrollment> enrollments = enrollmentRepository.findAllById(enrollmentIds).stream()
                .collect(Collectors.toMap(Enrollment::getId, Function.identity()));

        Map<UUID, List<Pass>> passesByEnrollment = new HashMap<>();
        for (UUID enrollmentId : enrollmentIds) {
            passesByEnrollment.put(enrollmentId,
                    passRepository.findUsableCandidates(enrollmentId, PassStatus.ACTIVE));
        }

        List<AttendanceResponse.item> items = attendances.stream()
                .map(a -> {
                    Enrollment enrollment = enrollments.get(a.getEnrollmentId());
                    String dogName = enrollment == null ? null : enrollment.getDogNameSnapshot();

                    // 이미 등원했으면 이용권 판정은 의미가 없다 — 다시 차감할 일이 없다.
                    boolean pending = a.getStatus() == AttendanceStatus.SCHEDULED;
                    boolean hasPass = passesByEnrollment.getOrDefault(a.getEnrollmentId(), List.of())
                            .stream().anyMatch(p -> p.isUsableOn(date));

                    boolean available = !pending || hasPass;
                    String reason = available ? null : ErrorCode.NO_USABLE_PASS.getMessage();
                    return AttendanceResponse.item.of(a, dogName, available, reason);
                })
                .toList();

        return new AttendanceResponse.dashboard(
                date,
                count(attendances, a -> a.getStatus() == AttendanceStatus.SCHEDULED),
                count(attendances, a -> a.getStatus() == AttendanceStatus.ATTENDED && !a.isCheckedOut()),
                count(attendances, a -> a.getStatus() == AttendanceStatus.ATTENDED && a.isCheckedOut()),
                count(attendances, a -> a.getStatus() == AttendanceStatus.ABSENT),
                count(attendances, a -> a.getStatus() == AttendanceStatus.CANCELLED),
                items);
    }

    /**
     * 다중 등원 처리(PN-09, FR-PN09-01). 여러 마리를 한 번에 등원시킨다.
     *
     * <p>⚠️ 이 메서드에 {@code @Transactional}을 걸지 않는다. 건별로 트랜잭션이 갈려야
     * 한 마리의 이용권 만료가 나머지를 되돌리지 않는다({@link AttendanceCheckInProcessor} 참조).
     * 여기에 트랜잭션을 걸면 성공한 건들이 마지막 실패와 함께 전부 롤백된다.
     */
    public AttendanceResponse.bulkResult checkIn(
            UUID merchantId, UUID userId, AttendanceRequest.bulk request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Map<UUID, Attendance> targets = loadTargets(merchantId, request.attendanceIds());
        Map<UUID, String> dogNames = loadDogNames(targets.values());

        List<UUID> succeeded = new ArrayList<>();
        List<AttendanceResponse.bulkResult.failure> failed = new ArrayList<>();

        for (UUID attendanceId : request.attendanceIds()) {
            try {
                checkInProcessor.checkIn(attendanceId, userId);
                succeeded.add(attendanceId);
            } catch (BusinessException e) {
                Attendance a = targets.get(attendanceId);
                failed.add(new AttendanceResponse.bulkResult.failure(
                        attendanceId,
                        a == null ? null : dogNames.get(a.getEnrollmentId()),
                        e.getErrorCode().getCode(),
                        e.getMessage()));
            }
        }
        return new AttendanceResponse.bulkResult(succeeded, failed);
    }

    /**
     * 다중 하원 처리(PN-09). 이용권과 무관하므로 한 트랜잭션으로 묶어도 안전하다 —
     * 등원과 달리 실패 원인이 "상태가 안 맞음" 하나뿐이고 부분 성공의 이득이 없다.
     */
    @Transactional
    public AttendanceResponse.bulkResult checkOut(
            UUID merchantId, UUID userId, AttendanceRequest.bulk request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Map<UUID, Attendance> targets = loadTargets(merchantId, request.attendanceIds());
        Map<UUID, String> dogNames = loadDogNames(targets.values());

        List<UUID> succeeded = new ArrayList<>();
        List<AttendanceResponse.bulkResult.failure> failed = new ArrayList<>();

        for (UUID attendanceId : request.attendanceIds()) {
            Attendance a = targets.get(attendanceId);
            if (a == null) {
                failed.add(failure(attendanceId, null, ErrorCode.ATTENDANCE_NOT_FOUND));
                continue;
            }
            try {
                a.checkOut(userId);
                succeeded.add(attendanceId);
            } catch (BusinessException e) {
                failed.add(failure(attendanceId, dogNames.get(a.getEnrollmentId()), e.getErrorCode()));
            }
        }
        return new AttendanceResponse.bulkResult(succeeded, failed);
    }

    /**
     * 등원 되돌리기(PN-09, FR-PN09-04). 차감된 이용권도 함께 복원한다.
     *
     * <p>⚠️ 복원은 차감 행을 지우는 것이 아니라 반대 방향 행을 넣는 것이다 —
     * 원장은 append-only이며, 되돌린 이력 자체가 분쟁 대응의 근거다.
     */
    @Transactional
    public AttendanceResponse.item revert(
            UUID merchantId, UUID userId, UUID attendanceId, AttendanceRequest.revert request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Attendance attendance = findInMerchant(merchantId, attendanceId);
        UUID passId = attendance.getPassId();

        attendance.revert(userId); // ATTENDED가 아니면 여기서 AT002

        // 기간권 등원이면 passId는 있어도 차감된 회차가 없다 — restore()가 null을 준다.
        if (passId != null) {
            Pass pass = passRepository.findByIdForUpdate(passId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PASS_NOT_FOUND));
            Integer balanceAfter = pass.restore();
            if (balanceAfter != null) {
                passLedgerRepository.save(PassLedger.restoration(
                        passId, balanceAfter, userId, attendanceId, request.reason()));
            }
        }

        Enrollment enrollment = enrollmentRepository.findById(attendance.getEnrollmentId()).orElse(null);
        return AttendanceResponse.item.of(attendance,
                enrollment == null ? null : enrollment.getDogNameSnapshot(), true, null);
    }

    /** 결석 처리(PN-08). 이용권은 차감하지 않는다 — 오지 않았으므로 서비스도 없었다. */
    @Transactional
    public AttendanceResponse.item markAbsent(UUID merchantId, UUID userId, UUID attendanceId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ATTENDANCE);

        Attendance attendance = findInMerchant(merchantId, attendanceId);
        attendance.markAbsent();

        Enrollment enrollment = enrollmentRepository.findById(attendance.getEnrollmentId()).orElse(null);
        return AttendanceResponse.item.of(attendance,
                enrollment == null ? null : enrollment.getDogNameSnapshot(), false, null);
    }

    /** ⚠️ 남의 매장 등원 ID를 넣어도 통과하지 않게 소속을 대조한다. */
    private Attendance findInMerchant(UUID merchantId, UUID attendanceId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));
        if (!attendance.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
        return attendance;
    }

    /**
     * 요청된 id를 한 번에 읽고 <b>이 매장 것만</b> 남긴다.
     * 남의 매장 id가 섞여 있으면 조용히 무시하지 않고 "없음"으로 실패시킨다.
     */
    private Map<UUID, Attendance> loadTargets(UUID merchantId, List<UUID> ids) {
        return attendanceRepository.findAllById(ids).stream()
                .filter(a -> a.getMerchantId().equals(merchantId))
                .collect(Collectors.toMap(Attendance::getId, Function.identity()));
    }

    private Map<UUID, String> loadDogNames(Collection<Attendance> attendances) {
        if (attendances.isEmpty()) return Map.of();
        List<UUID> ids = attendances.stream().map(Attendance::getEnrollmentId).distinct().toList();
        return enrollmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Enrollment::getId, Enrollment::getDogNameSnapshot));
    }

    private AttendanceResponse.bulkResult.failure failure(UUID id, String dogName, ErrorCode code) {
        return new AttendanceResponse.bulkResult.failure(id, dogName, code.getCode(), code.getMessage());
    }

    private long count(List<Attendance> list, java.util.function.Predicate<Attendance> p) {
        return list.stream().filter(p).count();
    }
}
