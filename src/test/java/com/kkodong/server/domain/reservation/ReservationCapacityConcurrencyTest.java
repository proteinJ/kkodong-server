package com.kkodong.server.domain.reservation;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.*;
import com.kkodong.server.domain.reservation.domain.Reservation;
import com.kkodong.server.domain.reservation.domain.ReservationStatus;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.reservation.repository.ReservationRepository;
import com.kkodong.server.domain.reservation.service.ReservationService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 일일 정원 상한이 <b>동시 승인</b>에서도 지켜지는지 검증한다.
 *
 * <p><b>왜 이 테스트가 필요한가</b>: "이 날짜의 확정 예약 ≤ N"은 DB 제약으로 표현할 수
 * 없다(행 단위 CHECK로는 다른 행을 셀 수 없고, UNIQUE로는 카운트 상한이 안 된다).
 * 그래서 서버가 "센다 → 비교한다 → 승인한다"를 하는데, 이 사이에 다른 트랜잭션이
 * 끼어들면 둘 다 정원 미달을 보고 둘 다 통과한다. 순차 테스트로는 절대 잡히지 않고
 * 실제 매장에서 정원 초과로만 드러나는 종류의 버그다.
 *
 * <p>{@code MerchantDayLock}의 advisory lock이 그 임계 구역을 직렬화하는지를
 * 실제 스레드로 확인한다.
 *
 * <p>⚠️ 이 테스트는 {@code @Transactional}이 아니다. 테스트 트랜잭션으로 감싸면
 * 다른 스레드가 그 안의 데이터를 못 보고, 락도 테스트 트랜잭션 하나에 묶여 경쟁 자체가
 * 일어나지 않는다. 그래서 커밋된 데이터로 돌리고 뒷정리를 직접 한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class ReservationCapacityConcurrencyTest {

    private static final int CAPACITY = 2;
    private static final int CONCURRENT_REQUESTS = 8;
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 9, 10);

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository merchantStaffRepository;
    @Autowired private KindergartenProfileRepository kindergartenProfileRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private UUID merchantId;
    private UUID directorUserId;
    private final List<UUID> reservationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            directorUserId = insertUser();

            Merchant merchant = merchantRepository.save(Merchant.builder()
                    .name("정원테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장")
                    .status(MerchantStatus.ACTIVE)
                    .build());
            merchantId = merchant.getId();

            kindergartenProfileRepository.save(KindergartenProfile.builder()
                    .merchantId(merchantId)
                    .dailyCapacity(CAPACITY)
                    .build());

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(directorUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE)
                    .build());

            // 정원(2)보다 많은 신청을 만들어 둔다. 이들이 동시에 승인을 시도한다.
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                Enrollment enrollment = enrollmentRepository.save(Enrollment.builder()
                        .merchantId(merchantId)
                        .dogNameSnapshot("강아지" + i)
                        .build());

                Reservation reservation = reservationRepository.save(Reservation.builder()
                        .merchantId(merchantId)
                        .dogNameSnapshot("강아지" + i)
                        .enrollmentId(enrollment.getId())
                        .serviceDate(SERVICE_DATE)
                        .startsAt(OffsetDateTime.parse("2026-09-10T09:00:00+09:00"))
                        .endsAt(OffsetDateTime.parse("2026-09-10T18:00:00+09:00"))
                        .status(ReservationStatus.REQUESTED)
                        .build());
                reservationIds.add(reservation.getId());
            }
        });
    }

    @AfterEach
    void tearDown() {
        // FK 역순으로 지운다. 매장을 지우면 CASCADE로 나머지가 따라가지만,
        // 원생·예약은 SET NULL로 설계돼 있어 매장 CASCADE만으로는 정리되지 않는다.
        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("DELETE FROM attendances WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM reservations WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM enrollments WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, directorUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("정원 2인 날짜에 8건이 동시에 승인을 시도해도 확정은 정확히 2건이다")
    void concurrentConfirmsRespectDailyCapacity() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        // 모든 스레드를 같은 순간에 풀어놔야 실제로 경쟁이 일어난다.
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger capacityRejected = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (UUID reservationId : reservationIds) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    reservationService.confirm(merchantId, directorUserId, reservationId);
                    confirmed.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.DAILY_CAPACITY_EXCEEDED) {
                        capacityRejected.incrementAndGet();
                    } else {
                        otherFailures.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherFailures.incrementAndGet();
                }
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // ★ 핵심: 락이 없으면 여기서 2보다 큰 수가 나온다.
        assertThat(confirmed.get())
                .as("확정된 예약 수가 정원을 넘으면 안 된다 — advisory lock이 동작하지 않는 것이다")
                .isEqualTo(CAPACITY);
        assertThat(capacityRejected.get()).isEqualTo(CONCURRENT_REQUESTS - CAPACITY);
        assertThat(otherFailures.get())
                .as("정원 초과 외의 예외는 없어야 한다")
                .isZero();

        // DB에 실제로 남은 것도 확인한다 — 카운터만 맞고 데이터가 틀릴 수 있다.
        long confirmedInDb = reservationRepository.countByStatuses(
                merchantId, SERVICE_DATE, java.util.Set.of(ReservationStatus.CONFIRMED));
        assertThat(confirmedInDb).isEqualTo(CAPACITY);

        // 확정된 예약마다 등원 예정이 하나씩 생겼는지 — 예약 → 등원 전환의 확인.
        long scheduled = attendanceRepository
                .findAllByMerchantIdAndAttendanceDate(merchantId, SERVICE_DATE).size();
        assertThat(scheduled)
                .as("확정 예약 수와 등원 예정 수가 같아야 한다")
                .isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("정원이 찬 날 이미 승인된 예약을 다시 승인하면 정원 초과가 아니라 상태 오류를 준다")
    void reconfirmOnFullDayReportsStatusNotCapacity() {
        // 정원(2)을 모두 채운다.
        UUID first = reservationIds.get(0);
        reservationService.confirm(merchantId, directorUserId, first);
        reservationService.confirm(merchantId, directorUserId, reservationIds.get(1));

        // ⚠️ 회귀 방지: 정원 검사가 상태 검사보다 먼저 돌면 여기서 RV004가 나온다.
        //    확정된 예약은 스스로 정원을 차지하고 있어서, 정원이 꽉 찬 날이면 재승인이
        //    "정원 초과"로 보인다 — 점주는 버튼을 두 번 눌렀을 뿐인데 그날이 꽉 찼다는
        //    안내를 받게 되고, 정원을 늘려도 해결되지 않아 원인을 찾기 어렵다.
        assertThatThrownBy(() -> reservationService.confirm(merchantId, directorUserId, first))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS);

        // 재승인이 실패해도 등원 예정이 늘지 않아야 한다(이중 차감의 씨앗이 된다).
        assertThat(attendanceRepository.findAllByMerchantIdAndAttendanceDate(merchantId, SERVICE_DATE))
                .hasSize(CAPACITY);
    }

    @Transactional
    UUID insertUser() {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                .setParameter(1, id)
                .setParameter(2, id + "@test.local")
                .executeUpdate();
        return id;
    }
}
