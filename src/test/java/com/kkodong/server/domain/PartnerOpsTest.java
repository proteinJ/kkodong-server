package com.kkodong.server.domain;

import com.kkodong.server.domain.assignment.dto.AssignmentRequest;
import com.kkodong.server.domain.assignment.dto.AssignmentResponse;
import com.kkodong.server.domain.assignment.repository.StaffAssignmentRepository;
import com.kkodong.server.domain.assignment.service.AssignmentService;
import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.domain.review.domain.MerchantReview;
import com.kkodong.server.domain.review.dto.ReviewRequest;
import com.kkodong.server.domain.review.repository.MerchantReviewRepository;
import com.kkodong.server.domain.review.service.ReviewService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 담당 배정(PN-13)과 리뷰 답글(PN-20)을 검증한다.
 *
 * <p>둘 다 규모가 작아 한 클래스에 묶었다. 중점은 두 가지다:
 * <ul>
 *   <li><b>배정 마릿수</b> — 이 화면의 목적은 배정 자체가 아니라 한 명에게 몰리지 않게 하는
 *       것이라, 카운트와 정렬이 맞아야 기능이 성립한다(FR-PN13-01)</li>
 *   <li><b>리뷰 요약 집계</b> — 평균·건수를 한 쿼리로 받는데, 스칼라 여러 개를 반환하는
 *       JPQL은 타입이 어긋나도 컴파일이 통과하고 실행 시에야 터진다</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class PartnerOpsTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private AssignmentService assignmentService;
    @Autowired private ReviewService reviewService;
    @Autowired private StaffAssignmentRepository assignmentRepository;
    @Autowired private MerchantReviewRepository reviewRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository staffRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private UUID merchantId;
    private UUID directorUserId;
    private UUID directorStaffId;
    private UUID teacherStaffId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            directorUserId = insertUser("원장");
            UUID teacherUserId = insertUser("선생님");

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("운영테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장").status(MerchantStatus.ACTIVE).build()).getId();

            directorStaffId = staffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(directorUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE).build()).getId();
            teacherStaffId = staffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(teacherUserId)
                    .role(StaffRole.STAFF).status(StaffStatus.ACTIVE)
                    .permissions(List.of("attendance")).build()).getId();
        });
    }

    @AfterEach
    void tearDown() {
        txTemplate.executeWithoutResult(tx -> {
            for (String t : List.of("staff_assignments", "merchant_reviews", "attendances",
                    "enrollments", "merchant_staff")) {
                em.createNativeQuery("DELETE FROM " + t + " WHERE merchant_id = ?1")
                        .setParameter(1, merchantId).executeUpdate();
            }
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE email LIKE '%@ops.test'").executeUpdate();
        });
    }

    // ---------- PN-13 담당 배정 ----------

    @Test
    @DisplayName("배정하면 담당 마릿수가 잡히고, 담당이 적은 선생님이 앞에 온다")
    void assignCountsAndSorts() {
        UUID a = attendedEnrollment("두부");
        UUID b = attendedEnrollment("초코");
        UUID c = attendedEnrollment("보리");

        var board = assignmentService.assign(merchantId, directorUserId,
                new AssignmentRequest.assign(teacherStaffId, List.of(a, b), TODAY));

        assertThat(board.attendedCount()).isEqualTo(3);
        assertThat(board.unassigned()).singleElement()
                .satisfies(d -> assertThat(d.enrollmentId()).isEqualTo(c));

        // ★ 담당이 적은 순 — 다음에 누구에게 맡길지가 목록 맨 위에 있어야 한다.
        assertThat(board.staff()).hasSize(2);
        assertThat(board.staff().get(0).count()).isZero();
        assertThat(board.staff().get(0).staffId()).isEqualTo(directorStaffId);
        assertThat(board.staff().get(1).count()).isEqualTo(2);
        assertThat(board.staff().get(1).dogs())
                .extracting(AssignmentResponse.board.assignedDog::dogName)
                .containsExactlyInAnyOrder("두부", "초코");
    }

    @Test
    @DisplayName("이미 배정된 강아지를 다시 배정하면 옮겨진다 — 먼저 해제할 필요가 없다")
    void reassignMovesInsteadOfFailing() {
        UUID a = attendedEnrollment("두부");
        assignmentService.assign(merchantId, directorUserId,
                new AssignmentRequest.assign(teacherStaffId, List.of(a), TODAY));

        UUID firstAssignmentId = assignmentRepository
                .findByEnrollmentIdAndAssignedDate(a, TODAY).orElseThrow().getId();

        var board = assignmentService.assign(merchantId, directorUserId,
                new AssignmentRequest.assign(directorStaffId, List.of(a), TODAY));

        assertThat(board.staff()).filteredOn(s -> s.staffId().equals(directorStaffId))
                .singleElement().satisfies(s -> assertThat(s.count()).isEqualTo(1));
        assertThat(board.staff()).filteredOn(s -> s.staffId().equals(teacherStaffId))
                .singleElement().satisfies(s -> assertThat(s.count()).isZero());

        // 행을 지웠다 만들지 않고 옮겼는지 — 배정 시각이 보존돼야 한다.
        assertThat(assignmentRepository.findByEnrollmentIdAndAssignedDate(a, TODAY).orElseThrow().getId())
                .isEqualTo(firstAssignmentId);
    }

    @Test
    @DisplayName("등원하지 않은 원생은 배정 화면에 나오지 않는다")
    void onlyAttendedAppearOnBoard() {
        attendedEnrollment("등원함");
        UUID absent = txTemplate.execute(tx -> enrollmentRepository.save(Enrollment.builder()
                .merchantId(merchantId).dogNameSnapshot("결석").build()).getId());
        txTemplate.executeWithoutResult(tx -> attendanceRepository.save(Attendance.builder()
                .merchantId(merchantId).enrollmentId(absent)
                .attendanceDate(TODAY).status(AttendanceStatus.ABSENT).build()));

        var board = assignmentService.getBoard(merchantId, directorUserId, TODAY);

        // 결석한 아이를 배정해 봐야 현장에 없다.
        assertThat(board.attendedCount()).isEqualTo(1);
        assertThat(board.unassigned()).singleElement()
                .satisfies(d -> assertThat(d.dogName()).isEqualTo("등원함"));
    }

    @Test
    @DisplayName("담당 선생님이 퇴사하면 그 강아지는 미배정으로 되돌아온다")
    void resignedStaffLeavesDogsUnassigned() {
        UUID a = attendedEnrollment("두부");
        assignmentService.assign(merchantId, directorUserId,
                new AssignmentRequest.assign(teacherStaffId, List.of(a), TODAY));

        txTemplate.executeWithoutResult(tx ->
                staffRepository.findById(teacherStaffId).orElseThrow().resign());

        var board = assignmentService.getBoard(merchantId, directorUserId, TODAY);

        // 아무도 안 맡은 상태다 — 배정 행은 남아 있지만 화면에서는 미배정이어야 한다.
        assertThat(board.unassigned()).singleElement()
                .satisfies(d -> assertThat(d.enrollmentId()).isEqualTo(a));
        assertThat(board.staff()).noneMatch(s -> s.staffId().equals(teacherStaffId));
    }

    // ---------- PN-20 리뷰 ----------

    @Test
    @DisplayName("리뷰 요약이 평균·건수·미답변 수를 함께 준다")
    void reviewSummaryAggregates() {
        newReview((short) 5, "최고예요", null);
        newReview((short) 3, "보통이에요", null);
        newReview((short) 4, "좋아요", "감사합니다");

        var summary = reviewService.getReviews(merchantId, directorUserId, false);

        // ⚠️ 스칼라 여러 개를 한 쿼리로 받는 부분 — 타입이 어긋나면 여기서 터진다.
        assertThat(summary.averageRating()).isEqualTo(4.0);
        assertThat(summary.totalCount()).isEqualTo(3);
        assertThat(summary.unansweredCount()).isEqualTo(2);
        assertThat(summary.reviews()).hasSize(3);
    }

    @Test
    @DisplayName("미답변만 걸러도 평균과 전체 건수는 전체 기준을 유지한다")
    void summaryStaysGlobalWhenFiltered() {
        newReview((short) 5, "최고", null);
        newReview((short) 1, "별로", "죄송합니다");

        var filtered = reviewService.getReviews(merchantId, directorUserId, true);

        assertThat(filtered.reviews()).hasSize(1);
        // ★ 필터로 걸렀다고 평균이 5.0으로 바뀌면 화면이 거짓말을 한다.
        assertThat(filtered.averageRating()).isEqualTo(3.0);
        assertThat(filtered.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("리뷰가 없으면 평균은 0이고 목록은 비어 있다")
    void emptyReviewsSummary() {
        var summary = reviewService.getReviews(merchantId, directorUserId, false);

        assertThat(summary.averageRating()).isZero();
        assertThat(summary.totalCount()).isZero();
        assertThat(summary.reviews()).isEmpty();
    }

    @Test
    @DisplayName("답글은 작성과 수정을 겸하고, 철회하면 다시 미답변이 된다")
    void replyCreateUpdateRemove() {
        UUID reviewId = newReview((short) 4, "좋아요", null);

        var replied = reviewService.reply(merchantId, directorUserId, reviewId,
                new ReviewRequest.reply("감사합니다"));
        assertThat(replied.replyBody()).isEqualTo("감사합니다");
        assertThat(replied.unanswered()).isFalse();
        assertThat(replied.repliedAt()).isNotNull();

        // 같은 엔드포인트가 수정을 겸한다 — 답글은 하나뿐이라 나눌 이유가 없다.
        var edited = reviewService.reply(merchantId, directorUserId, reviewId,
                new ReviewRequest.reply("소중한 후기 감사합니다"));
        assertThat(edited.replyBody()).isEqualTo("소중한 후기 감사합니다");

        var removed = reviewService.removeReply(merchantId, directorUserId, reviewId);
        assertThat(removed.replyBody()).isNull();
        assertThat(removed.unanswered()).isTrue();
    }

    @Test
    @DisplayName("삭제된 리뷰에는 답글을 달 수 없다")
    void cannotReplyToDeletedReview() {
        UUID reviewId = newReview((short) 1, "부적절한 내용", null);
        txTemplate.executeWithoutResult(tx ->
                em.createNativeQuery("UPDATE merchant_reviews SET deleted_at = now() WHERE id = ?1")
                        .setParameter(1, reviewId).executeUpdate());

        assertThatThrownBy(() -> reviewService.reply(merchantId, directorUserId, reviewId,
                new ReviewRequest.reply("답글")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_DELETED);

        // 삭제된 리뷰는 목록과 집계 양쪽에서 빠진다.
        assertThat(reviewService.getReviews(merchantId, directorUserId, false).totalCount()).isZero();
    }

    // ---- 픽스처 ----

    private UUID attendedEnrollment(String dogName) {
        return txTemplate.execute(tx -> {
            UUID enrollmentId = enrollmentRepository.save(Enrollment.builder()
                    .merchantId(merchantId).dogNameSnapshot(dogName).build()).getId();
            attendanceRepository.save(Attendance.builder()
                    .merchantId(merchantId).enrollmentId(enrollmentId)
                    .attendanceDate(TODAY).status(AttendanceStatus.ATTENDED)
                    .checkedInAt(OffsetDateTime.now()).build());
            return enrollmentId;
        });
    }

    /** 리뷰 작성은 견주 앱 몫이라 서버에 엔드포인트가 없다 — 테스트에서 직접 만든다. */
    private UUID newReview(short rating, String content, String reply) {
        return txTemplate.execute(tx -> {
            MerchantReview review = reviewRepository.save(MerchantReview.builder()
                    .merchantId(merchantId)
                    .authorNameSnapshot("견주")
                    .rating(rating)
                    .content(content)
                    .build());
            if (reply != null) review.reply(reply, directorUserId);
            return review.getId();
        });
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                        "INSERT INTO users (id, email, password_hash, display_name) VALUES (?1, ?2, 'x', ?3)")
                .setParameter(1, id).setParameter(2, id + "@ops.test").setParameter(3, name)
                .executeUpdate();
        return id;
    }
}
