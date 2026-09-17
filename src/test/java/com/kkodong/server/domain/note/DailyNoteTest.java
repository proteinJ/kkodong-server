package com.kkodong.server.domain.note;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.domain.note.domain.NoteContent;
import com.kkodong.server.domain.note.domain.NoteStatus;
import com.kkodong.server.domain.note.dto.DailyNoteRequest;
import com.kkodong.server.domain.note.dto.DailyNoteResponse;
import com.kkodong.server.domain.note.repository.DailyNoteRepository;
import com.kkodong.server.domain.note.service.DailyNoteService;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
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

/**
 * 알림장 작성·일괄작성·발송과 템플릿을 검증한다(PN-14, FR-PN14-01).
 *
 * <p>중점은 두 가지다:
 * <ul>
 *   <li><b>일괄 작성이 원생 수만큼 개별 행을 만드는가</b> — 공유 본문 하나로 만들면
 *       개별 수정·읽음 시각·통합 타임라인이 전부 특수 케이스가 된다(V9 daily_notes 주석)</li>
 *   <li><b>JSONB에 담은 {@link NoteContent} record가 왕복하는가</b> — 타입만으로는 알 수 없고,
 *       메타데이터 검사(SchemaDriftTest)로도 잡히지 않는다</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
class DailyNoteTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private DailyNoteService service;
    @Autowired private DailyNoteRepository noteRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private AttendanceRepository attendanceRepository;
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
                    .setParameter(2, staffUserId + "@test.local").executeUpdate();

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("알림장테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장").status(MerchantStatus.ACTIVE).build()).getId();

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(staffUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE).build());
        });
    }

    @AfterEach
    void tearDown() {
        txTemplate.executeWithoutResult(tx -> {
            for (String t : List.of("daily_notes", "note_templates", "attendances", "enrollments")) {
                em.createNativeQuery("DELETE FROM " + t + " WHERE merchant_id = ?1")
                        .setParameter(1, merchantId).executeUpdate();
            }
            em.createNativeQuery("DELETE FROM merchant_staff WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, staffUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("다섯 항목이 그대로 저장되고 다시 읽힌다")
    void noteContentRoundTrip() {
        UUID e = newEnrollment("두부");

        var saved = service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                e, TODAY, null,
                new NoteContent("실내 놀이터에서 뛰어놀았어요", "사료 완식", "대변 1회", "아주 좋음", "발톱이 조금 길어요")));

        // 서비스가 이미 커밋했으므로 여기서 읽으면 DB에서 새로 온다.
        var reloaded = noteRepository.findById(saved.id()).orElseThrow().content();
        assertThat(reloaded.activity()).isEqualTo("실내 놀이터에서 뛰어놀았어요");
        assertThat(reloaded.meal()).isEqualTo("사료 완식");
        assertThat(reloaded.bathroom()).isEqualTo("대변 1회");
        assertThat(reloaded.condition()).isEqualTo("아주 좋음");
        assertThat(reloaded.remark()).isEqualTo("발톱이 조금 길어요");
        assertThat(saved.status()).isEqualTo(NoteStatus.DRAFT);
    }

    @Test
    @DisplayName("템플릿을 불러오고 일부만 고치면 나머지는 템플릿 값이 남는다")
    void templateOverlay() {
        UUID e = newEnrollment("두부");
        var template = service.saveTemplate(merchantId, staffUserId, new DailyNoteRequest.saveTemplate(
                "평범한 하루",
                new NoteContent("신나게 놀았어요", "사료 완식", "대변 1회", "좋음", null)));

        // 컨디션만 고친다 — FR-PN14-01의 "입력 부담 최소화"가 이 흐름이다.
        var note = service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                e, TODAY, template.id(),
                new NoteContent(null, null, null, "조금 피곤해 보여요", null)));

        assertThat(note.content().activity()).isEqualTo("신나게 놀았어요");  // 템플릿 유지
        assertThat(note.content().meal()).isEqualTo("사료 완식");
        assertThat(note.content().condition()).isEqualTo("조금 피곤해 보여요"); // 덮어씀
    }

    @Test
    @DisplayName("일괄 작성은 원생 수만큼 개별 알림장을 만든다 — 공유 본문 하나가 아니다")
    void bulkWriteCreatesIndividualNotes() {
        UUID a = newEnrollment("두부");
        UUID b = newEnrollment("초코");
        UUID c = newEnrollment("보리");

        var result = service.writeBulk(merchantId, staffUserId, new DailyNoteRequest.writeBulk(
                List.of(a, b, c), TODAY, null,
                new NoteContent("다 같이 산책 다녀왔어요", null, null, null, null)));

        assertThat(result.succeeded()).hasSize(3);
        assertThat(noteRepository.findAllByMerchantIdAndNoteDate(merchantId, TODAY)).hasSize(3);

        // ★ 한 명만 따로 고칠 수 있어야 한다. 공유 본문이면 셋 다 바뀐다.
        service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                b, TODAY, null, new NoteContent("혼자 조용히 쉬었어요", null, null, null, null)));

        assertThat(noteRepository.findByEnrollmentIdAndNoteDate(a, TODAY).orElseThrow()
                .getActivity()).isEqualTo("다 같이 산책 다녀왔어요");
        assertThat(noteRepository.findByEnrollmentIdAndNoteDate(b, TODAY).orElseThrow()
                .getActivity()).isEqualTo("혼자 조용히 쉬었어요");
        assertThat(noteRepository.findByEnrollmentIdAndNoteDate(c, TODAY).orElseThrow()
                .getActivity()).isEqualTo("다 같이 산책 다녀왔어요");
    }

    @Test
    @DisplayName("일괄 작성은 이미 발송된 알림장을 덮어쓰지 않고 건너뛴다")
    void bulkWriteSkipsSentNotes() {
        UUID sent = newEnrollment("먼저보낸애");
        UUID draft = newEnrollment("아직초안");

        var first = service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                sent, TODAY, null, new NoteContent("개별로 먼저 쓴 내용", null, null, null, null)));
        service.send(merchantId, staffUserId, new DailyNoteRequest.send(List.of(first.id())));

        var result = service.writeBulk(merchantId, staffUserId, new DailyNoteRequest.writeBulk(
                List.of(sent, draft), TODAY, null,
                new NoteContent("일괄 내용", null, null, null, null)));

        assertThat(result.succeeded()).hasSize(1);
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> assertThat(s.code()).isEqualTo("DN002"));

        // ⚠️ 보호자가 이미 읽었을 수 있는 내용이 일괄 작업에 조용히 덮이면 안 된다.
        assertThat(noteRepository.findByEnrollmentIdAndNoteDate(sent, TODAY).orElseThrow()
                .getActivity()).isEqualTo("개별로 먼저 쓴 내용");
    }

    @Test
    @DisplayName("빈 알림장은 발송되지 않는다")
    void emptyNoteCannotBeSent() {
        UUID e = newEnrollment("두부");
        var note = service.write(merchantId, staffUserId,
                new DailyNoteRequest.write(e, TODAY, null, NoteContent.empty()));

        var result = service.send(merchantId, staffUserId, new DailyNoteRequest.send(List.of(note.id())));

        assertThat(result.succeeded()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> assertThat(s.code()).isEqualTo("DN004"));
        assertThat(noteRepository.findById(note.id()).orElseThrow().getStatus())
                .isEqualTo(NoteStatus.DRAFT);
    }

    @Test
    @DisplayName("발송하면 상태와 시각이 남고, 이후 수정은 막힌다")
    void sendThenLocked() {
        UUID e = newEnrollment("두부");
        var note = service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                e, TODAY, null, new NoteContent("잘 놀았어요", null, null, null, null)));

        service.send(merchantId, staffUserId, new DailyNoteRequest.send(List.of(note.id())));

        var sent = noteRepository.findById(note.id()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NoteStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getReadAt()).isNull(); // 보호자가 아직 안 읽음

        // 개별 수정도 막혀야 한다 — 일괄만 막고 개별은 뚫리면 의미가 없다.
        var result = service.writeBulk(merchantId, staffUserId, new DailyNoteRequest.writeBulk(
                List.of(e), TODAY, null, new NoteContent("바꾼 내용", null, null, null, null)));
        assertThat(result.skipped()).singleElement()
                .satisfies(s -> assertThat(s.code()).isEqualTo("DN002"));
    }

    @Test
    @DisplayName("작성 현황은 등원했는데 알림장이 없는 원생을 pending으로 보여준다")
    void workspaceShowsPending() {
        UUID written = newEnrollment("작성완료");
        UUID notYet = newEnrollment("아직안씀");
        UUID absent = newEnrollment("결석");

        newAttendance(written, AttendanceStatus.ATTENDED);
        newAttendance(notYet, AttendanceStatus.ATTENDED);
        newAttendance(absent, AttendanceStatus.ABSENT);

        service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                written, TODAY, null, new NoteContent("잘 놀았어요", null, null, null, null)));

        var ws = service.getWorkspace(merchantId, staffUserId, TODAY);

        assertThat(ws.attendedCount()).isEqualTo(2); // 결석은 세지 않는다
        assertThat(ws.writtenCount()).isEqualTo(1);
        assertThat(ws.sentCount()).isZero();

        // ★ 이 목록이 비는 것이 하루 마감 조건이다. 결석한 원생은 여기 없어야 한다.
        assertThat(ws.pending()).singleElement()
                .satisfies(p -> {
                    assertThat(p.enrollmentId()).isEqualTo(notYet);
                    assertThat(p.dogName()).isEqualTo("아직안씀");
                });
    }

    @Test
    @DisplayName("알림장은 그날의 등원 기록과 이어진다 — 통합 타임라인의 연결 고리다")
    void noteLinksToAttendance() {
        UUID e = newEnrollment("두부");
        UUID attendanceId = newAttendance(e, AttendanceStatus.ATTENDED);

        var note = service.write(merchantId, staffUserId, new DailyNoteRequest.write(
                e, TODAY, null, new NoteContent("잘 놀았어요", null, null, null, null)));

        assertThat(noteRepository.findById(note.id()).orElseThrow().getAttendanceId())
                .isEqualTo(attendanceId);
    }

    // ---- 픽스처 ----

    private UUID newEnrollment(String dogName) {
        return txTemplate.execute(tx -> enrollmentRepository.save(Enrollment.builder()
                .merchantId(merchantId).dogNameSnapshot(dogName).build()).getId());
    }

    private UUID newAttendance(UUID enrollmentId, AttendanceStatus status) {
        return txTemplate.execute(tx -> attendanceRepository.save(Attendance.builder()
                .merchantId(merchantId).enrollmentId(enrollmentId)
                .attendanceDate(TODAY).status(status)
                .checkedInAt(status == AttendanceStatus.ATTENDED ? OffsetDateTime.now() : null)
                .build()).getId());
    }
}
