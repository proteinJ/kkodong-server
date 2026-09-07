package com.kkodong.server.domain.enrollment;

import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.dto.PassRequest;
import com.kkodong.server.domain.enrollment.dto.PassResponse;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.repository.PassLedgerRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.enrollment.service.PassService;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantProductRepository;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.dto.AttendanceRequest;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.reservation.service.AttendanceService;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이용권 원장의 <b>정합성</b>을 검증한다(FR-PN12-01).
 *
 * <p><b>핵심 불변식</b>: 원장(pass_ledger)의 delta를 처음부터 모두 더하면 현재 잔여
 * 회차가 나와야 한다. 이것이 깨지면 원장을 신뢰할 수 없고, 매출 분쟁이 생겼을 때
 * 어느 쪽이 맞는지 판단할 근거 자체가 사라진다.
 *
 * <p>발급 → 등원 차감 → 되돌리기 복원 → 수동 조정 → 기간 연장 → 환불까지
 * 실제 서비스 호출로 한 바퀴 돌리며 매 단계 불변식을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class PassLedgerConsistencyTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private PassService passService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private PassRepository passRepository;
    @Autowired private PassLedgerRepository passLedgerRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository merchantStaffRepository;
    @Autowired private MerchantProductRepository merchantProductRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private UUID merchantId;
    private UUID directorUserId;
    private UUID enrollmentId;
    private UUID countProductId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            directorUserId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                    .setParameter(1, directorUserId)
                    .setParameter(2, directorUserId + "@test.local")
                    .executeUpdate();

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("이용권테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장")
                    .status(MerchantStatus.ACTIVE)
                    .build()).getId();

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(directorUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE)
                    .build());

            enrollmentId = enrollmentRepository.save(Enrollment.builder()
                    .merchantId(merchantId).dogNameSnapshot("두부").build()).getId();

            countProductId = merchantProductRepository.save(MerchantProduct.builder()
                    .merchantId(merchantId)
                    .name("10회권")
                    .productType(ProductType.COUNT)
                    .totalCount(10)
                    .validDays(90)
                    .price(300_000)
                    .build()).getId();
        });
    }

    @AfterEach
    void tearDown() {
        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("""
                    DELETE FROM pass_ledger WHERE pass_id IN
                      (SELECT p.id FROM passes p JOIN enrollments e ON e.id = p.enrollment_id
                       WHERE e.merchant_id = ?1)""").setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM attendances WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("""
                    DELETE FROM passes WHERE enrollment_id IN
                      (SELECT id FROM enrollments WHERE merchant_id = ?1)""")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM enrollments WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchant_products WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, directorUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("발급→차감→복원→조정→연장→환불 전 과정에서 원장 합계가 항상 실제 잔여와 같다")
    void ledgerSumAlwaysMatchesRemaining() {
        // 1) 발급 — 상품 정의에서 조건을 가져와 스냅샷으로 굳힌다
        PassResponse.detailInfo issued = passService.issue(merchantId, directorUserId,
                new PassRequest.issue(enrollmentId, countProductId, "최초 발급"));
        UUID passId = issued.id();

        assertThat(issued.remainingCount()).isEqualTo(10);
        assertThat(issued.productName()).isEqualTo("10회권");
        assertThat(issued.price()).isEqualTo(300_000);
        assertThat(issued.expiresOn()).isEqualTo(TODAY.plusDays(90));
        assertThat(issued.usableToday()).isTrue();
        assertLedgerMatchesRemaining(passId);

        // 2) 등원 차감 — 출석 체크(PN-09)를 거쳐 실제로 깎는다
        UUID attendanceId = txTemplate.execute(tx -> attendanceRepository.save(Attendance.builder()
                .merchantId(merchantId).enrollmentId(enrollmentId)
                .attendanceDate(TODAY).status(AttendanceStatus.SCHEDULED)
                .build()).getId());
        attendanceService.checkIn(merchantId, directorUserId,
                new AttendanceRequest.bulk(List.of(attendanceId)));

        assertThat(remaining(passId)).isEqualTo(9);
        assertLedgerMatchesRemaining(passId);

        // 3) 되돌리기 복원 — 차감 행은 남고 복원 행이 덧붙는다
        attendanceService.revert(merchantId, directorUserId, attendanceId,
                new AttendanceRequest.revert("오처리"));

        assertThat(remaining(passId)).isEqualTo(10);
        assertLedgerMatchesRemaining(passId);

        // 4) 수동 조정 — 서비스 회차 2회 제공
        PassResponse.detailInfo adjusted = passService.adjust(merchantId, directorUserId, passId,
                new PassRequest.adjust(2, "등원 누락분 보정"));

        assertThat(adjusted.remainingCount()).isEqualTo(12);
        assertLedgerMatchesRemaining(passId);

        // 5) 기간 연장 — 회차는 그대로, 기존 만료일 기준으로 늘어난다
        PassResponse.detailInfo extended = passService.extend(merchantId, directorUserId, passId,
                new PassRequest.extend(30, "휴원 보상"));

        assertThat(extended.remainingCount()).isEqualTo(12);
        // ⚠️ 오늘이 아니라 기존 만료일(오늘+90)에서 30일 더해야 한다 —
        //    오늘 기준이면 미리 연장한 사람이 남은 기간을 손해 본다.
        assertThat(extended.expiresOn()).isEqualTo(TODAY.plusDays(120));
        assertLedgerMatchesRemaining(passId);

        // 6) 환불 — 남은 회차를 전부 걷어낸다
        PassResponse.detailInfo refunded = passService.refund(merchantId, directorUserId, passId,
                new PassRequest.refund("이사로 인한 중도 해지"));

        assertThat(refunded.status()).isEqualTo(PassStatus.REFUNDED);
        assertThat(refunded.remainingCount()).isZero();
        assertThat(refunded.usableToday()).isFalse();
        // ★ 최종 불변식: 원장 전체 합이 0이어야 장부가 맞는다.
        assertThat(ledgerSum(passId)).isZero();
        assertLedgerMatchesRemaining(passId);

        // 이력이 전부 남아 있어야 한다 — 감사 로그의 존재 이유다
        List<PassResponse.ledgerItem> ledger =
                passService.getLedger(merchantId, directorUserId, passId);
        assertThat(ledger).extracting(PassResponse.ledgerItem::entryType)
                .containsExactlyInAnyOrder(
                        PassEntryType.GRANT, PassEntryType.DEDUCT, PassEntryType.RESTORE,
                        PassEntryType.ADJUST, PassEntryType.EXTEND, PassEntryType.REFUND);
    }

    @Test
    @DisplayName("환불된 이용권은 등원에 쓰이지 않고 추가 변경도 막힌다")
    void refundedPassIsClosed() {
        UUID passId = passService.issue(merchantId, directorUserId,
                new PassRequest.issue(enrollmentId, countProductId, null)).id();
        passService.refund(merchantId, directorUserId, passId, new PassRequest.refund("해지"));

        // 환불했는데 회차가 남아 있으면 그 회차로 등원이 되어 매출과 장부가 어긋난다.
        UUID attendanceId = txTemplate.execute(tx -> attendanceRepository.save(Attendance.builder()
                .merchantId(merchantId).enrollmentId(enrollmentId)
                .attendanceDate(TODAY).status(AttendanceStatus.SCHEDULED)
                .build()).getId());
        var result = attendanceService.checkIn(merchantId, directorUserId,
                new AttendanceRequest.bulk(List.of(attendanceId)));
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).code()).isEqualTo("PA001");

        assertThatThrownBy(() -> passService.refund(merchantId, directorUserId, passId,
                new PassRequest.refund("두 번째 환불")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASS_ALREADY_CLOSED);
    }

    @Test
    @DisplayName("잔여를 음수로 만드는 조정은 거부된다")
    void adjustCannotGoNegative() {
        UUID passId = passService.issue(merchantId, directorUserId,
                new PassRequest.issue(enrollmentId, countProductId, null)).id();

        assertThatThrownBy(() -> passService.adjust(merchantId, directorUserId, passId,
                new PassRequest.adjust(-11, "과다 차감 시도")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PASS_ADJUSTMENT);

        // 실패했으면 원장에도 아무것도 남지 않아야 한다 — 시도 흔적이 잔여를 흔들면 안 된다.
        assertThat(remaining(passId)).isEqualTo(10);
        assertLedgerMatchesRemaining(passId);
    }

    @Test
    @DisplayName("다른 매장 원장은 우리 원생의 이용권에 손댈 수 없다")
    void otherMerchantCannotTouchOurPass() {
        UUID passId = passService.issue(merchantId, directorUserId,
                new PassRequest.issue(enrollmentId, countProductId, null)).id();

        // 이용권은 매장을 직접 참조하지 않는다(passes → enrollments → merchants).
        // 원생을 거쳐 소속을 확인하지 않으면 여기가 뚫린다.
        UUID outsiderMerchantId = txTemplate.execute(tx -> merchantRepository.save(Merchant.builder()
                .name("남의유치원")
                .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                .representativeName("박원장").status(MerchantStatus.ACTIVE)
                .build()).getId());
        txTemplate.executeWithoutResult(tx -> merchantStaffRepository.save(MerchantStaff.builder()
                .merchantId(outsiderMerchantId).userId(directorUserId)
                .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE).build()));

        assertThatThrownBy(() -> passService.refund(outsiderMerchantId, directorUserId, passId,
                new PassRequest.refund("남의 이용권 환불 시도")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND);

        assertThat(passRepository.findById(passId).orElseThrow().getStatus())
                .isEqualTo(PassStatus.ACTIVE);

        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("DELETE FROM merchant_staff WHERE merchant_id = ?1")
                    .setParameter(1, outsiderMerchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, outsiderMerchantId).executeUpdate();
        });
    }

    // ---- 불변식 검사 ----

    /** ★ 원장의 delta 합계 == 실제 잔여. 이것이 깨지면 원장을 신뢰할 수 없다. */
    private void assertLedgerMatchesRemaining(UUID passId) {
        assertThat(ledgerSum(passId))
                .as("원장 합계가 실제 잔여와 달라졌다 — 어느 변동이 원장을 남기지 않았거나 잘못 남겼다")
                .isEqualTo(remaining(passId));
    }

    private int ledgerSum(UUID passId) {
        return passLedgerRepository.findAllByPassIdOrderByCreatedAtDesc(passId).stream()
                .mapToInt(PassLedger::getDelta).sum();
    }

    private int remaining(UUID passId) {
        return passRepository.findById(passId).orElseThrow().getRemainingCount();
    }
}
