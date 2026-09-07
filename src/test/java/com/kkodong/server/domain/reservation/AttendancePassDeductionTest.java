package com.kkodong.server.domain.reservation;

import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.repository.PassLedgerRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.dto.AttendanceRequest;
import com.kkodong.server.domain.reservation.dto.AttendanceResponse;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.reservation.service.AttendanceService;
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

/**
 * 등원 확정과 이용권 차감의 <b>원자성</b>과 되돌리기의 <b>복원</b>을 검증한다(FR-PN09-03/04).
 *
 * <p><b>왜 중요한가</b>: 이 둘이 갈라지면 "등원은 됐는데 회차가 안 깎인" 행이나
 * "되돌렸는데 회차가 안 돌아온" 행이 남는데, 둘 다 조회로 찾아낼 방법이 없다.
 * 매출 데이터라 나중에 발견하면 어느 쪽이 맞는지 판단할 근거조차 없다.
 *
 * <p>⚠️ {@code @Transactional}이 아니다 — 등원 처리가 {@code REQUIRES_NEW}로 별도
 * 트랜잭션을 열기 때문에, 테스트 트랜잭션으로 감싸면 그 안의 데이터를 보지 못한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class AttendancePassDeductionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private PassRepository passRepository;
    @Autowired private PassLedgerRepository passLedgerRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository merchantStaffRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private UUID merchantId;
    private UUID staffUserId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            staffUserId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                    .setParameter(1, staffUserId)
                    .setParameter(2, staffUserId + "@test.local")
                    .executeUpdate();

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("차감테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장")
                    .status(MerchantStatus.ACTIVE)
                    .build()).getId();

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(staffUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE)
                    .build());
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
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, staffUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("등원 처리하면 회차가 정확히 1 차감되고 차감 로그가 남는다")
    void checkInDeductsExactlyOnce() {
        Fixture f = createFixture("두부", 10, TODAY);

        var result = attendanceService.checkIn(merchantId, staffUserId,
                new AttendanceRequest.bulk(List.of(f.attendanceId)));

        assertThat(result.succeeded()).containsExactly(f.attendanceId);
        assertThat(result.failed()).isEmpty();

        Pass pass = passRepository.findById(f.passId).orElseThrow();
        assertThat(pass.getRemainingCount()).isEqualTo(9);

        List<PassLedger> ledger = passLedgerRepository.findAllByPassIdOrderByCreatedAtDesc(f.passId);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getEntryType()).isEqualTo(PassEntryType.DEDUCT);
        assertThat(ledger.get(0).getDelta()).isEqualTo(-1);
        // 잔여 스냅샷이 실제 잔여와 일치해야 한다 — 어긋나면 원장을 신뢰할 수 없다.
        assertThat(ledger.get(0).getBalanceAfter()).isEqualTo(9);
        assertThat(ledger.get(0).getSourceAttendanceId()).isEqualTo(f.attendanceId);

        Attendance attendance = attendanceRepository.findById(f.attendanceId).orElseThrow();
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(attendance.getPassId()).isEqualTo(f.passId);
        assertThat(attendance.getPassLedgerId()).isEqualTo(ledger.get(0).getId());
    }

    @Test
    @DisplayName("이용권이 없으면 등원 자체가 막힌다 — 차감 없이 등원시키지 않는다")
    void checkInWithoutPassIsRejected() {
        Fixture f = createFixtureWithoutPass("초코", TODAY);

        var result = attendanceService.checkIn(merchantId, staffUserId,
                new AttendanceRequest.bulk(List.of(f.attendanceId)));

        assertThat(result.succeeded()).isEmpty();
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).code()).isEqualTo("PA001");

        // ★ 실패했으면 등원 상태도 그대로여야 한다. 여기가 ATTENDED면 매출이 새는 것이다.
        assertThat(attendanceRepository.findById(f.attendanceId).orElseThrow().getStatus())
                .isEqualTo(AttendanceStatus.SCHEDULED);
    }

    @Test
    @DisplayName("여러 마리 중 하나가 실패해도 나머지는 확정된다 (부분 성공)")
    void bulkCheckInAllowsPartialSuccess() {
        Fixture ok1 = createFixture("두부", 5, TODAY);
        Fixture noPass = createFixtureWithoutPass("초코", TODAY);
        Fixture ok2 = createFixture("보리", 5, TODAY);

        var result = attendanceService.checkIn(merchantId, staffUserId,
                new AttendanceRequest.bulk(List.of(ok1.attendanceId, noPass.attendanceId, ok2.attendanceId)));

        // ⚠️ 한 트랜잭션으로 묶였다면 여기가 비어 있을 것이다 —
        //    10마리 중 1마리 때문에 9마리를 되돌리면 점주는 원인을 모른 채 다시 눌러야 한다.
        assertThat(result.succeeded()).containsExactlyInAnyOrder(ok1.attendanceId, ok2.attendanceId);
        assertThat(result.failed()).hasSize(1);

        assertThat(passRepository.findById(ok1.passId).orElseThrow().getRemainingCount()).isEqualTo(4);
        assertThat(passRepository.findById(ok2.passId).orElseThrow().getRemainingCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("되돌리면 회차가 복원되고, 차감 기록은 지워지지 않고 복원 기록이 덧붙는다")
    void revertRestoresCountAndAppendsLedger() {
        Fixture f = createFixture("두부", 10, TODAY);
        attendanceService.checkIn(merchantId, staffUserId,
                new AttendanceRequest.bulk(List.of(f.attendanceId)));

        AttendanceResponse.item reverted = attendanceService.revert(
                merchantId, staffUserId, f.attendanceId, new AttendanceRequest.revert("잘못 눌렀음"));

        assertThat(reverted.status()).isEqualTo(AttendanceStatus.SCHEDULED);
        assertThat(reverted.revertedAt()).isNotNull();
        assertThat(passRepository.findById(f.passId).orElseThrow().getRemainingCount()).isEqualTo(10);

        // ★ append-only: 차감 행이 남아 있고 복원 행이 새로 붙어야 한다.
        //   차감 행을 지우면 "원래 등원했다가 취소된 것"과 "처음부터 없던 것"을 구분할 수 없다.
        List<PassLedger> ledger = passLedgerRepository.findAllByPassIdOrderByCreatedAtDesc(f.passId);
        assertThat(ledger).hasSize(2);
        assertThat(ledger).extracting(PassLedger::getEntryType)
                .containsExactlyInAnyOrder(PassEntryType.DEDUCT, PassEntryType.RESTORE);
        assertThat(ledger).filteredOn(l -> l.getEntryType() == PassEntryType.RESTORE)
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.getDelta()).isEqualTo(1);
                    assertThat(l.getBalanceAfter()).isEqualTo(10);
                    assertThat(l.getReason()).isEqualTo("잘못 눌렀음");
                });
    }

    @Test
    @DisplayName("마지막 회차를 쓰면 이용권이 소진 상태가 되고, 되돌리면 다시 사용 가능해진다")
    void lastCountExhaustsAndRestoreReactivates() {
        Fixture f = createFixture("두부", 1, TODAY);

        attendanceService.checkIn(merchantId, staffUserId,
                new AttendanceRequest.bulk(List.of(f.attendanceId)));
        Pass exhausted = passRepository.findById(f.passId).orElseThrow();
        assertThat(exhausted.getRemainingCount()).isZero();
        assertThat(exhausted.getStatus()).isEqualTo(PassStatus.EXHAUSTED);
        assertThat(exhausted.isUsableOn(TODAY)).isFalse();

        attendanceService.revert(merchantId, staffUserId, f.attendanceId,
                new AttendanceRequest.revert(null));

        Pass restored = passRepository.findById(f.passId).orElseThrow();
        assertThat(restored.getRemainingCount()).isEqualTo(1);
        // 만료가 아니라 회차 때문에 닫혔던 것이므로 회차가 생기면 다시 열려야 한다.
        assertThat(restored.getStatus()).isEqualTo(PassStatus.ACTIVE);
        assertThat(restored.isUsableOn(TODAY)).isTrue();
    }

    @Test
    @DisplayName("만료된 이용권은 등원에 쓰이지 않는다")
    void expiredPassIsNotUsed() {
        Fixture f = createFixture("두부", 10, TODAY.minusDays(1)); // 어제 만료

        var result = attendanceService.checkIn(merchantId, staffUserId,
                new AttendanceRequest.bulk(List.of(f.attendanceId)));

        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).code()).isEqualTo("PA001");
        assertThat(passRepository.findById(f.passId).orElseThrow().getRemainingCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("대시보드는 이용권 없는 원생을 감추지 않고 이유와 함께 보여준다")
    void dashboardMarksUncheckInableItems() {
        createFixture("두부", 5, TODAY);
        createFixtureWithoutPass("초코", TODAY);

        var dashboard = attendanceService.getDashboard(merchantId, staffUserId, TODAY);

        assertThat(dashboard.scheduledCount()).isEqualTo(2);
        assertThat(dashboard.items()).hasSize(2);

        // FR-PN09-02 — 목록에서 빼지 않고 이유를 준다. 점주가 "왜 안 보이지" 대신
        // "이용권을 발급해야겠다"로 바로 가게 하려는 것이다.
        assertThat(dashboard.items()).filteredOn(i -> "초코".equals(i.dogName()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.checkInAvailable()).isFalse();
                    assertThat(i.blockedReason()).isNotBlank();
                });
        assertThat(dashboard.items()).filteredOn(i -> "두부".equals(i.dogName()))
                .singleElement()
                .satisfies(i -> assertThat(i.checkInAvailable()).isTrue());
    }

    @Test
    @DisplayName("잔여 1인 이용권을 두 등원이 동시에 쓰면 한 건만 성공한다 (lost update 방지)")
    void concurrentCheckInsOnSamePassDeductOnlyOnce() throws Exception {
        // 같은 원생의 서로 다른 날짜 등원 두 건이 하나의 이용권을 공유한다.
        // (어제 놓친 등원을 처리하는 동안 오늘 등원이 들어오는 상황)
        UUID enrollmentId = txTemplate.execute(tx -> newEnrollment("두부"));
        UUID passId = txTemplate.execute(tx -> passRepository.save(Pass.builder()
                .enrollmentId(enrollmentId)
                .productNameSnapshot("마지막1회권")
                .productType(ProductType.COUNT)
                .remainingCount(1).totalCount(1)
                .build()).getId());

        UUID a1 = txTemplate.execute(tx -> newAttendanceOn(enrollmentId, TODAY));
        UUID a2 = txTemplate.execute(tx -> newAttendanceOn(enrollmentId, TODAY.minusDays(1)));

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate = new java.util.concurrent.CountDownLatch(1);
        var succeeded = new java.util.concurrent.atomic.AtomicInteger();

        for (UUID attendanceId : List.of(a1, a2)) {
            pool.submit(() -> {
                gate.await();
                var r = attendanceService.checkIn(merchantId, staffUserId,
                        new AttendanceRequest.bulk(List.of(attendanceId)));
                succeeded.addAndGet(r.succeeded().size());
                return null;
            });
        }
        gate.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        // ★ 비관적 락이 없으면 둘 다 잔여 1을 읽고 둘 다 성공해 회차가 한 번만 깎인다.
        assertThat(succeeded.get())
                .as("잔여 1인 이용권으로 두 건이 등원되면 안 된다 — 비관적 락이 동작하지 않는 것이다")
                .isEqualTo(1);
        assertThat(passRepository.findById(passId).orElseThrow().getRemainingCount()).isZero();
        // 차감 로그도 정확히 하나여야 한다.
        assertThat(passLedgerRepository.findAllByPassIdOrderByCreatedAtDesc(passId)).hasSize(1);
    }

    // ---- 픽스처 ----

    private record Fixture(UUID enrollmentId, UUID passId, UUID attendanceId) {}

    private Fixture createFixture(String dogName, int remaining, LocalDate expiresOn) {
        return txTemplate.execute(tx -> {
            UUID enrollmentId = newEnrollment(dogName);
            UUID passId = passRepository.save(Pass.builder()
                    .enrollmentId(enrollmentId)
                    .productNameSnapshot("테스트권")
                    .productType(ProductType.COUNT)
                    .remainingCount(remaining)
                    .totalCount(remaining)
                    .expiresOn(expiresOn)
                    .build()).getId();
            return new Fixture(enrollmentId, passId, newAttendance(enrollmentId));
        });
    }

    private Fixture createFixtureWithoutPass(String dogName, LocalDate date) {
        return txTemplate.execute(tx -> {
            UUID enrollmentId = newEnrollment(dogName);
            return new Fixture(enrollmentId, null, newAttendance(enrollmentId));
        });
    }

    private UUID newEnrollment(String dogName) {
        return enrollmentRepository.save(Enrollment.builder()
                .merchantId(merchantId)
                .dogNameSnapshot(dogName)
                .build()).getId();
    }

    private UUID newAttendance(UUID enrollmentId) {
        return newAttendanceOn(enrollmentId, TODAY);
    }

    private UUID newAttendanceOn(UUID enrollmentId, LocalDate date) {
        return attendanceRepository.save(Attendance.builder()
                .merchantId(merchantId)
                .enrollmentId(enrollmentId)
                .attendanceDate(date)
                .status(AttendanceStatus.SCHEDULED)
                .build()).getId();
    }
}
