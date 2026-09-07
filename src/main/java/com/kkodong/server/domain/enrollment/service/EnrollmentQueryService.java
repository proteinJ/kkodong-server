package com.kkodong.server.domain.enrollment.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.domain.EnrollmentSort;
import com.kkodong.server.domain.enrollment.domain.EnrollmentStatus;
import com.kkodong.server.domain.enrollment.dto.EnrollmentRequest;
import com.kkodong.server.domain.enrollment.dto.EnrollmentResponse;
import com.kkodong.server.domain.enrollment.dto.PassResponse;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 원생 목록(PN-10)과 상세(PN-11).
 *
 * <p>원생은 신청 승인(PN-07)으로만 생긴다 — 이 서비스는 <b>조회와 관리</b>만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentQueryService {

    /** 최근 등원 이력을 몇 건까지 보여줄지(PN-11). 더 필요하면 전용 목록 API를 따로 낸다. */
    private static final int RECENT_ATTENDANCE_LIMIT = 20;

    private final EnrollmentRepository enrollmentRepository;
    private final PassRepository passRepository;
    private final AttendanceRepository attendanceRepository;
    private final DogRepository dogRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * 원생 목록(PN-10, FR-PN10-01).
     *
     * <p><b>정렬·검색을 서버 메모리에서 하는 이유</b>: 정렬 기준이 전부 다른 테이블의
     * 집계값(잔여·만료·마지막 등원·등원 횟수)이라 SQL로 하려면 정렬마다 다른 쿼리를
     * 쓰거나 복잡한 CASE 식이 필요하다. 반면 한 유치원의 원생은 많아야 수백 명이고
     * 집계는 이미 한 쿼리로 끝나 있다.
     *
     * <p>⚠️ 원생이 수천 명 규모가 되면(체인점 통합 조회 등) 이 판단을 다시 해야 한다 —
     * 그때는 정렬을 SQL로 내리고 페이지네이션을 붙일 것.
     */
    public List<EnrollmentResponse.listItem> getList(
            UUID merchantId, UUID userId,
            EnrollmentStatus status, EnrollmentSort sort, String keyword) {
        accessGuard.requireStaff(merchantId, userId);

        LocalDate today = LocalDate.now();
        // 상태를 생략하면 재원 중만 본다 — 퇴원생까지 섞이면 목록이 매년 불어난다.
        EnrollmentStatus wanted = status == null ? EnrollmentStatus.ACTIVE : status;

        return enrollmentRepository.findRowsByMerchantId(merchantId).stream()
                .map(r -> EnrollmentResponse.listItem.from(r, today))
                .filter(i -> i.status() == wanted)
                .filter(i -> i.matches(keyword))
                .sorted(comparatorFor(sort == null ? EnrollmentSort.ENROLLED_DESC : sort))
                .toList();
    }

    /**
     * ⚠️ null은 항상 뒤로 보낸다. 이용권이 없거나 한 번도 안 온 원생이 정렬 앞뒤로
     * 튀면 목록이 매번 다르게 읽힌다.
     */
    private Comparator<EnrollmentResponse.listItem> comparatorFor(EnrollmentSort sort) {
        return switch (sort) {
            case ENROLLED_DESC -> Comparator
                    .comparing(EnrollmentResponse.listItem::enrolledOn,
                            Comparator.nullsLast(Comparator.reverseOrder()));

            // ★ 사실상 영업 리스트다(FR-PN10-01). 재결제 안내가 급한 순서로 준다:
            //   대상(paymentDue) → 이용권 없음 → 잔여가 적은 순 → 만료가 가까운 순.
            //
            // ⚠️ "이용권 없음"을 별도 단계로 올린 이유: 그런 원생은 remainingCount가 null이라
            //    잔여 비교에서 nullsLast로 밀려 급한 그룹의 맨 뒤로 간다. 하지만 이용권이
            //    아예 없으면 등원 처리 자체가 막히므로(FR-PN09-02) 잔여 1회 남은 원생보다
            //    더 급하다. Boolean 자연순서가 false < true 라 hasActivePass=false가 앞에 온다.
            case PAYMENT_DUE -> Comparator
                    .comparing(EnrollmentResponse.listItem::paymentDue, Comparator.reverseOrder())
                    .thenComparing(EnrollmentResponse.listItem::hasActivePass)
                    .thenComparing(EnrollmentResponse.listItem::remainingCount,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(EnrollmentResponse.listItem::daysUntilExpiry,
                            Comparator.nullsLast(Comparator.naturalOrder()));

            case RECENT_ATTENDANCE -> Comparator
                    .comparing(EnrollmentResponse.listItem::lastAttendedOn,
                            Comparator.nullsLast(Comparator.reverseOrder()));

            case ATTENDANCE_FREQUENCY -> Comparator
                    .comparing(EnrollmentResponse.listItem::attendedCount, Comparator.reverseOrder());
        };
    }

    /**
     * 원생 상세(PN-11). 반려견 정보·이용권·등원 이력·메모를 한 번에 준다.
     *
     * <p>{@code dog}는 견주 탈퇴로 null일 수 있다. 그때도 화면이 비지 않도록
     * 스냅샷 이름을 함께 내려준다({@link Enrollment} 참조).
     */
    public EnrollmentResponse.detailInfo getDetail(UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requireStaff(merchantId, userId);

        Enrollment enrollment = findInMerchant(merchantId, enrollmentId);
        LocalDate today = LocalDate.now();

        Dog dog = enrollment.getDogId() == null ? null
                : dogRepository.findById(enrollment.getDogId()).orElse(null);

        List<PassResponse.detailInfo> passes = passRepository
                .findAllByEnrollmentId(enrollmentId).stream()
                .map(p -> PassResponse.detailInfo.from(p, today))
                .sorted(Comparator
                        .comparing(PassResponse.detailInfo::usableToday).reversed()
                        .thenComparing(PassResponse.detailInfo::daysUntilExpiry,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<EnrollmentResponse.detailInfo.attendanceHistory> attendances = attendanceRepository
                .findTop20ByEnrollmentIdOrderByAttendanceDateDesc(enrollmentId).stream()
                .map(EnrollmentResponse.detailInfo.attendanceHistory::from)
                .toList();

        return EnrollmentResponse.detailInfo.of(enrollment, dog, passes, attendances);
    }

    /** 특이사항 메모 수정(PN-11). ⚠️ 보호자 비공개 값이다. */
    @Transactional
    public EnrollmentResponse.detailInfo updateMemo(
            UUID merchantId, UUID userId, UUID enrollmentId, EnrollmentRequest.updateMemo request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ENROLLMENT);

        findInMerchant(merchantId, enrollmentId).updateMemo(request.staffMemo());
        return getDetail(merchantId, userId, enrollmentId);
    }

    /** 휴원(PN-11). 이용권은 살아 있지만 예약을 받지 않는다. */
    @Transactional
    public EnrollmentResponse.detailInfo pause(UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ENROLLMENT);
        findInMerchant(merchantId, enrollmentId).pause();
        return getDetail(merchantId, userId, enrollmentId);
    }

    /** 복원(PN-11). */
    @Transactional
    public EnrollmentResponse.detailInfo resume(UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ENROLLMENT);
        findInMerchant(merchantId, enrollmentId).resume();
        return getDetail(merchantId, userId, enrollmentId);
    }

    /**
     * 퇴원(PN-11). 되돌릴 수 없다 — 다시 다니려면 신청부터 새로 한다.
     *
     * <p>남은 이용권은 자동으로 환불되지 않는다. 정산은 점주 판단이라
     * 이용권 환불(PN-12)을 별도로 호출해야 한다 — 자동으로 환불하면 되돌릴 수 없는
     * 매출 처리가 퇴원 버튼 하나에 딸려 나간다.
     */
    @Transactional
    public EnrollmentResponse.detailInfo withdraw(UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ENROLLMENT);
        findInMerchant(merchantId, enrollmentId).withdraw();
        return getDetail(merchantId, userId, enrollmentId);
    }

    /** ⚠️ 남의 매장 원생 ID를 넣어도 통과하지 않게 소속을 대조한다. */
    private Enrollment findInMerchant(UUID merchantId, UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!enrollment.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        return enrollment;
    }
}
