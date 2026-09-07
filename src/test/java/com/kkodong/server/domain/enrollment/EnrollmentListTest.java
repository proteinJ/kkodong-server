package com.kkodong.server.domain.enrollment;

import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.dto.EnrollmentRequest;
import com.kkodong.server.domain.enrollment.dto.EnrollmentResponse;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.enrollment.service.EnrollmentQueryService;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
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
 * 원생 목록의 집계와 정렬을 검증한다(PN-10, FR-PN10-01).
 *
 * <p><b>왜 이 테스트가 필요한가</b>: 목록은 원생 + 이용권 집계 + 출석 집계를 한 네이티브
 * 쿼리로 읽는다. 네이티브 쿼리의 인터페이스 프로젝션은 <b>컴파일도 되고 예외도 안 나면서
 * 값만 null로 오는</b> 실패가 흔하다 — Postgres가 따옴표 없는 별칭을 소문자로 접기 때문에
 * getter 이름과 어긋날 수 있다. 그래서 집계값이 실제로 채워지는지부터 확인한다.
 *
 * <p>정렬은 SQL이 아니라 서버에서 하므로, 그 비교자가 의도대로 줄을 세우는지도 함께 본다.
 */
@SpringBootTest
@ActiveProfiles("local")
class EnrollmentListTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private EnrollmentQueryService service;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private PassRepository passRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository merchantStaffRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private UUID merchantId;
    private UUID directorUserId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            directorUserId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                    .setParameter(1, directorUserId)
                    .setParameter(2, directorUserId + "@test.local").executeUpdate();

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("목록테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장").status(MerchantStatus.ACTIVE)
                    .build()).getId();

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(directorUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE).build());
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
            em.createNativeQuery("DELETE FROM merchant_staff WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, directorUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("집계 컬럼이 실제로 채워진다 — 프로젝션 별칭이 어긋나면 여기서 null이 잡힌다")
    void projectionColumnsAreActuallyPopulated() {
        UUID id = newEnrollment("두부", "포메라니안", "김보호자");
        // 이용권 두 장(각 3회, 5회) + 등원 2건 → 잔여 8, 등원 2가 나와야 한다.
        newCountPass(id, 3, TODAY.plusDays(10));
        newCountPass(id, 5, TODAY.plusDays(40));
        newAttendance(id, TODAY.minusDays(2), AttendanceStatus.ATTENDED);
        newAttendance(id, TODAY.minusDays(1), AttendanceStatus.ATTENDED);
        // 등원하지 않은 예정 건은 세면 안 된다.
        newAttendance(id, TODAY, AttendanceStatus.SCHEDULED);

        List<EnrollmentResponse.listItem> list = service.getList(merchantId, directorUserId, null, null, null);

        assertThat(list).singleElement().satisfies(i -> {
            assertThat(i.dogName()).isEqualTo("두부");
            assertThat(i.dogBreed()).isEqualTo("포메라니안");
            assertThat(i.ownerName()).isEqualTo("김보호자");
            assertThat(i.status()).isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(i.enrolledOn()).isNotNull();

            // 이용권 두 장의 합. 곱집합으로 부풀려지면(출석 2건 × 이용권 2장) 16이 나온다.
            assertThat(i.remainingCount()).isEqualTo(8);
            assertThat(i.nearestExpiry()).isEqualTo(TODAY.plusDays(10));
            assertThat(i.hasActivePass()).isTrue();
            assertThat(i.daysUntilExpiry()).isEqualTo(10);

            // 마찬가지로 이용권 장수에 곱해지면 4가 나온다. 실제 등원만 2건이어야 한다.
            assertThat(i.attendedCount()).isEqualTo(2);
            assertThat(i.lastAttendedOn()).isEqualTo(TODAY.minusDays(1));
        });
    }

    @Test
    @DisplayName("기간권만 가진 원생은 잔여가 null이다 — 0(다 씀)과 구분되어야 한다")
    void periodPassGivesNullRemaining() {
        UUID id = newEnrollment("초코", null, null);
        txTemplate.executeWithoutResult(tx -> passRepository.save(Pass.builder()
                .enrollmentId(id).productNameSnapshot("한달권")
                .productType(ProductType.PERIOD).expiresOn(TODAY.plusDays(30))
                .build()));

        var item = service.getList(merchantId, directorUserId, null, null, null).get(0);

        assertThat(item.remainingCount()).isNull();
        assertThat(item.hasActivePass()).isTrue();
        // 만료가 30일 남았고 회차 개념이 없으므로 재결제 대상이 아니다.
        assertThat(item.paymentDue()).isFalse();
    }

    @Test
    @DisplayName("결제 임박순은 급한 순서로 줄을 세운다 — 사실상 영업 리스트다")
    void paymentDueSortPutsUrgentFirst() {
        UUID noPass = newEnrollment("무권이", null, null);            // 이용권 없음 — 가장 급함
        UUID lowCount = newEnrollment("잔여1", null, null);
        newCountPass(lowCount, 1, TODAY.plusDays(60));
        UUID expiringSoon = newEnrollment("만료임박", null, null);
        newCountPass(expiringSoon, 8, TODAY.plusDays(3));
        UUID healthy = newEnrollment("여유", null, null);
        newCountPass(healthy, 9, TODAY.plusDays(80));

        var list = service.getList(merchantId, directorUserId, null, EnrollmentSort.PAYMENT_DUE, null);

        assertThat(list).hasSize(4);
        // 대상 3명이 먼저, 여유 있는 원생이 마지막.
        assertThat(list).extracting(EnrollmentResponse.listItem::paymentDue)
                .containsExactly(true, true, true, false);
        assertThat(list.get(3).id()).isEqualTo(healthy);

        // 이용권이 없는 원생이 맨 앞이어야 한다 — 등원 처리 자체가 막히므로 가장 급하다.
        assertThat(list.get(0).id()).isEqualTo(noPass);
        assertThat(list.get(0).hasActivePass()).isFalse();
    }

    @Test
    @DisplayName("최근 등원순·등원 빈도순이 각각 다르게 정렬된다")
    void attendanceSorts() {
        UUID recent = newEnrollment("최근에온애", null, null);
        newAttendance(recent, TODAY, AttendanceStatus.ATTENDED);

        UUID frequent = newEnrollment("자주오는애", null, null);
        newAttendance(frequent, TODAY.minusDays(5), AttendanceStatus.ATTENDED);
        newAttendance(frequent, TODAY.minusDays(6), AttendanceStatus.ATTENDED);
        newAttendance(frequent, TODAY.minusDays(7), AttendanceStatus.ATTENDED);

        assertThat(service.getList(merchantId, directorUserId, null, EnrollmentSort.RECENT_ATTENDANCE, null))
                .first().extracting(EnrollmentResponse.listItem::id).isEqualTo(recent);

        assertThat(service.getList(merchantId, directorUserId, null, EnrollmentSort.ATTENDANCE_FREQUENCY, null))
                .first().extracting(EnrollmentResponse.listItem::id).isEqualTo(frequent);
    }

    @Test
    @DisplayName("검색은 강아지 이름과 보호자 이름 양쪽에서 찾는다")
    void keywordSearchesBothNames() {
        newEnrollment("두부", null, "김보호자");
        newEnrollment("초코", null, "박보호자");

        assertThat(service.getList(merchantId, directorUserId, null, null, "두부")).hasSize(1);
        assertThat(service.getList(merchantId, directorUserId, null, null, "박보호")).hasSize(1);
        assertThat(service.getList(merchantId, directorUserId, null, null, "없는이름")).isEmpty();
    }

    @Test
    @DisplayName("상태를 생략하면 재원 중만 나오고, 퇴원생은 필터로만 볼 수 있다")
    void defaultsToActiveOnly() {
        newEnrollment("재원중", null, null);
        UUID leaving = newEnrollment("퇴원생", null, null);
        service.withdraw(merchantId, directorUserId, leaving);

        assertThat(service.getList(merchantId, directorUserId, null, null, null))
                .extracting(EnrollmentResponse.listItem::dogName).containsExactly("재원중");
        assertThat(service.getList(merchantId, directorUserId, EnrollmentStatus.WITHDRAWN, null, null))
                .extracting(EnrollmentResponse.listItem::dogName).containsExactly("퇴원생");
    }

    @Test
    @DisplayName("휴원한 원생은 예약 대상에서 빠지고, 퇴원은 되돌릴 수 없다")
    void statusTransitions() {
        UUID id = newEnrollment("두부", null, null);

        service.pause(merchantId, directorUserId, id);
        assertThat(enrollmentRepository.findById(id).orElseThrow().isReservable()).isFalse();

        service.resume(merchantId, directorUserId, id);
        assertThat(enrollmentRepository.findById(id).orElseThrow().isReservable()).isTrue();

        service.withdraw(merchantId, directorUserId, id);
        assertThatThrownBy(() -> service.resume(merchantId, directorUserId, id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ENROLLMENT_STATUS);
    }

    @Test
    @DisplayName("상세는 이용권과 등원 이력을 함께 주고, 메모는 수정된다")
    void detailCarriesPassesAndHistory() {
        UUID id = newEnrollment("두부", "포메라니안", "김보호자");
        newCountPass(id, 5, TODAY.plusDays(30));
        newAttendance(id, TODAY.minusDays(1), AttendanceStatus.ATTENDED);

        var detail = service.getDetail(merchantId, directorUserId, id);
        assertThat(detail.passes()).hasSize(1);
        assertThat(detail.recentAttendances()).hasSize(1);
        // 견주 탈퇴 대비 스냅샷은 항상 있어야 한다.
        assertThat(detail.dogNameSnapshot()).isEqualTo("두부");

        var updated = service.updateMemo(merchantId, directorUserId, id,
                new EnrollmentRequest.updateMemo("낯선 사람을 무서워함"));
        assertThat(updated.staffMemo()).isEqualTo("낯선 사람을 무서워함");
    }

    // ---- 픽스처 ----

    private UUID newEnrollment(String dogName, String breed, String ownerName) {
        return txTemplate.execute(tx -> enrollmentRepository.save(Enrollment.builder()
                .merchantId(merchantId)
                .dogNameSnapshot(dogName)
                .dogBreedSnapshot(breed)
                .ownerNameSnapshot(ownerName)
                .build()).getId());
    }

    private void newCountPass(UUID enrollmentId, int remaining, LocalDate expiresOn) {
        txTemplate.executeWithoutResult(tx -> passRepository.save(Pass.builder()
                .enrollmentId(enrollmentId).productNameSnapshot("테스트권")
                .productType(ProductType.COUNT)
                .remainingCount(remaining).totalCount(remaining)
                .expiresOn(expiresOn).build()));
    }

    private void newAttendance(UUID enrollmentId, LocalDate date, AttendanceStatus status) {
        txTemplate.executeWithoutResult(tx -> attendanceRepository.save(Attendance.builder()
                .merchantId(merchantId).enrollmentId(enrollmentId)
                .attendanceDate(date).status(status)
                .checkedInAt(status == AttendanceStatus.ATTENDED
                        ? java.time.OffsetDateTime.now() : null)
                .build()));
    }
}
