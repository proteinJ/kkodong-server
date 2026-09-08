package com.kkodong.server.domain.note.service;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.note.domain.DailyNote;
import com.kkodong.server.domain.note.domain.NoteContent;
import com.kkodong.server.domain.note.domain.NoteStatus;
import com.kkodong.server.domain.note.domain.NoteTemplate;
import com.kkodong.server.domain.note.dto.DailyNoteRequest;
import com.kkodong.server.domain.note.dto.DailyNoteResponse;
import com.kkodong.server.domain.note.repository.DailyNoteRepository;
import com.kkodong.server.domain.note.repository.NoteTemplateRepository;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
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
 * 알림장 작성·발송과 템플릿(PN-14).
 *
 * <p><b>이 기능은 매일 원생 수만큼 반복된다.</b> FR-PN14-01이 "입력 부담을 최소화한다"를
 * 요구사항으로 못박은 이유이며, 그래서 일괄 작성과 템플릿이 부가 기능이 아니라 본체다.
 *
 * <p>권한은 {@code daily_note}다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyNoteService {

    private final DailyNoteRepository noteRepository;
    private final NoteTemplateRepository templateRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * PN-14 작성 화면(하루치).
     *
     * <p>오늘 등원한 원생과 이미 쓴 알림장을 대조해 <b>아직 안 쓴 목록</b>을 만든다.
     * 점주가 이 화면에서 실제로 보는 것은 "누구 걸 안 썼나"이고, 그 목록이 비는 것이
     * 하루 마감의 조건이다.
     */
    public DailyNoteResponse.workspace getWorkspace(UUID merchantId, UUID userId, LocalDate date) {
        accessGuard.requireStaff(merchantId, userId);

        // 결석·취소한 원생에게는 알림장을 쓸 일이 없으므로 등원한 원생만 본다.
        List<Attendance> attended = attendanceRepository
                .findAllByMerchantIdAndAttendanceDate(merchantId, date).stream()
                .filter(a -> a.getStatus() == AttendanceStatus.ATTENDED)
                .toList();

        List<DailyNote> notes = noteRepository.findAllByMerchantIdAndNoteDate(merchantId, date);
        Set<UUID> written = notes.stream().map(DailyNote::getEnrollmentId).collect(Collectors.toSet());

        Map<UUID, String> names = dogNames(
                concat(attended.stream().map(Attendance::getEnrollmentId).toList(),
                        notes.stream().map(DailyNote::getEnrollmentId).toList()));

        List<DailyNoteResponse.workspace.pendingItem> pending = attended.stream()
                .filter(a -> !written.contains(a.getEnrollmentId()))
                .map(a -> new DailyNoteResponse.workspace.pendingItem(
                        a.getEnrollmentId(), names.get(a.getEnrollmentId())))
                .toList();

        return new DailyNoteResponse.workspace(
                date,
                attended.size(),
                notes.size(),
                (int) notes.stream().filter(DailyNote::isSent).count(),
                pending,
                notes.stream()
                        .map(n -> DailyNoteResponse.detailInfo.of(n, names.get(n.getEnrollmentId())))
                        .toList());
    }

    /**
     * 개별 작성·수정(PN-14). 같은 (원생, 날짜)에 이미 초안이 있으면 그것을 고친다.
     *
     * <p>하루 한 장이므로 새로 만들지 않고 덮어쓴다 — DB의 {@code uq_daily_notes_per_day}와
     * 같은 규칙이며, 이렇게 해야 화면이 "저장"을 여러 번 눌러도 결과가 같다.
     */
    @Transactional
    public DailyNoteResponse.detailInfo write(
            UUID merchantId, UUID userId, DailyNoteRequest.write request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        Enrollment enrollment = findEnrollmentInMerchant(merchantId, request.enrollmentId());
        NoteContent content = resolveContent(merchantId, request.templateId(), request.content());

        DailyNote note = upsertDraft(merchantId, enrollment.getId(), request.noteDate(), content, userId);
        return DailyNoteResponse.detailInfo.of(note, enrollment.getDogNameSnapshot());
    }

    /**
     * 일괄 작성(PN-14). 여러 원생에게 같은 내용으로 초안을 만든다.
     *
     * <p>⚠️ 만들어지는 것은 원생 수만큼의 개별 알림장이다(V9 daily_notes 주석 참조).
     * 이후 한 명만 따로 고칠 수 있어야 하고, 읽음 시각도 각자 다르다.
     *
     * <p>이미 <b>발송된</b> 알림장은 건드리지 않고 건너뛴다 — 보호자가 이미 읽었을 수 있는
     * 내용을 일괄 작업이 조용히 덮어쓰면 안 된다. 초안은 덮어쓴다.
     */
    @Transactional
    public DailyNoteResponse.bulkResult writeBulk(
            UUID merchantId, UUID userId, DailyNoteRequest.writeBulk request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        NoteContent content = resolveContent(merchantId, request.templateId(), request.content());
        Map<UUID, Enrollment> enrollments = enrollmentRepository.findAllById(request.enrollmentIds())
                .stream()
                .filter(e -> e.getMerchantId().equals(merchantId))
                .collect(Collectors.toMap(Enrollment::getId, Function.identity()));

        List<UUID> succeeded = new ArrayList<>();
        List<DailyNoteResponse.bulkResult.skipped> skipped = new ArrayList<>();

        for (UUID enrollmentId : request.enrollmentIds()) {
            Enrollment enrollment = enrollments.get(enrollmentId);
            if (enrollment == null) {
                skipped.add(skip(enrollmentId, null, ErrorCode.ENROLLMENT_NOT_FOUND));
                continue;
            }
            Optional<DailyNote> existing =
                    noteRepository.findByEnrollmentIdAndNoteDate(enrollmentId, request.noteDate());
            if (existing.isPresent() && existing.get().isSent()) {
                skipped.add(skip(enrollmentId, enrollment.getDogNameSnapshot(),
                        ErrorCode.DAILY_NOTE_ALREADY_SENT));
                continue;
            }
            succeeded.add(
                    upsertDraft(merchantId, enrollmentId, request.noteDate(), content, userId).getId());
        }
        return new DailyNoteResponse.bulkResult(succeeded, skipped);
    }

    /**
     * 발송(PN-14). 이 시점부터 보호자에게 보인다.
     *
     * <p>빈 알림장과 이미 발송된 건은 건너뛴다. 부분 성공을 허용하는 이유는 일괄 작성과 같다 —
     * 한 건 때문에 나머지를 되돌리면 점주가 원인을 모른 채 다시 눌러야 한다.
     */
    @Transactional
    public DailyNoteResponse.bulkResult send(
            UUID merchantId, UUID userId, DailyNoteRequest.send request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        Map<UUID, DailyNote> notes = noteRepository.findAllById(request.noteIds()).stream()
                .filter(n -> n.getMerchantId().equals(merchantId))
                .collect(Collectors.toMap(DailyNote::getId, Function.identity()));
        Map<UUID, String> names = dogNames(
                notes.values().stream().map(DailyNote::getEnrollmentId).toList());

        List<UUID> succeeded = new ArrayList<>();
        List<DailyNoteResponse.bulkResult.skipped> skipped = new ArrayList<>();

        for (UUID noteId : request.noteIds()) {
            DailyNote note = notes.get(noteId);
            if (note == null) {
                skipped.add(skip(noteId, null, ErrorCode.DAILY_NOTE_NOT_FOUND));
                continue;
            }
            try {
                note.send();
                succeeded.add(noteId);
            } catch (BusinessException e) {
                skipped.add(skip(noteId, names.get(note.getEnrollmentId()), e.getErrorCode()));
            }
        }
        return new DailyNoteResponse.bulkResult(succeeded, skipped);
    }

    /** 원생별 알림장 이력(PN-11 상세, KG-10). 최근 30건. */
    public List<DailyNoteResponse.detailInfo> getByEnrollment(
            UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requireStaff(merchantId, userId);
        Enrollment enrollment = findEnrollmentInMerchant(merchantId, enrollmentId);

        return noteRepository.findTop30ByEnrollmentIdOrderByNoteDateDesc(enrollmentId).stream()
                .map(n -> DailyNoteResponse.detailInfo.of(n, enrollment.getDogNameSnapshot()))
                .toList();
    }

    // ---------- 템플릿 (FR-PN14-01) ----------

    public List<DailyNoteResponse.templateInfo> getTemplates(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return templateRepository.findAllByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(DailyNoteResponse.templateInfo::from)
                .toList();
    }

    @Transactional
    public DailyNoteResponse.templateInfo saveTemplate(
            UUID merchantId, UUID userId, DailyNoteRequest.saveTemplate request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        return DailyNoteResponse.templateInfo.from(templateRepository.save(NoteTemplate.builder()
                .merchantId(merchantId)
                .title(request.title())
                .content(request.content())
                .createdByUserId(userId)
                .build()));
    }

    @Transactional
    public void deleteTemplate(UUID merchantId, UUID userId, UUID templateId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        NoteTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_TEMPLATE_NOT_FOUND));
        // ⚠️ 남의 매장 템플릿 ID를 넣어도 지워지지 않게 소속을 대조한다.
        if (!template.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.NOTE_TEMPLATE_NOT_FOUND);
        }
        // 템플릿은 이미 쓰인 알림장이 참조하지 않는다(내용이 복사된다) — 지워도 안전하다.
        templateRepository.delete(template);
    }

    // ---------- 내부 ----------

    /**
     * 템플릿을 바탕으로 하되 직접 입력한 항목이 있으면 그쪽을 쓴다(FR-PN14-01).
     * "템플릿 불러오고 컨디션만 고치기"가 이 한 줄로 표현된다.
     */
    private NoteContent resolveContent(UUID merchantId, UUID templateId, NoteContent override) {
        if (templateId == null) {
            return override == null ? NoteContent.empty() : override;
        }
        NoteTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTE_TEMPLATE_NOT_FOUND));
        if (!template.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.NOTE_TEMPLATE_NOT_FOUND);
        }
        return template.getContent().overlay(override);
    }

    /**
     * 초안을 만들거나 기존 초안을 고친다. 발송된 건은 {@code edit()}이 막는다.
     *
     * <p>그날의 등원 기록을 함께 이어둔다 — KG-14 통합 타임라인이 알림장과 산책·배변
     * 기록을 하루 축에 병합할 때 쓰인다.
     */
    private DailyNote upsertDraft(UUID merchantId, UUID enrollmentId, LocalDate date,
                                  NoteContent content, UUID authorUserId) {
        DailyNote note = noteRepository.findByEnrollmentIdAndNoteDate(enrollmentId, date)
                .orElseGet(() -> noteRepository.save(DailyNote.builder()
                        .merchantId(merchantId)
                        .enrollmentId(enrollmentId)
                        .noteDate(date)
                        .status(NoteStatus.DRAFT)
                        .build()));

        note.edit(content, authorUserId);

        if (note.getAttendanceId() == null) {
            attendanceRepository.findAllByMerchantIdAndAttendanceDate(merchantId, date).stream()
                    .filter(a -> a.getEnrollmentId().equals(enrollmentId))
                    .findFirst()
                    .ifPresent(a -> note.linkAttendance(a.getId()));
        }
        return note;
    }

    private Enrollment findEnrollmentInMerchant(UUID merchantId, UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!enrollment.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        return enrollment;
    }

    /** 원생 이름을 한 번에 읽는다 — 건별 조회하면 알림장 수만큼 쿼리가 늘어난다. */
    private Map<UUID, String> dogNames(List<UUID> enrollmentIds) {
        if (enrollmentIds.isEmpty()) return Map.of();
        return enrollmentRepository.findAllById(enrollmentIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Enrollment::getId, Enrollment::getDogNameSnapshot));
    }

    private static List<UUID> concat(List<UUID> a, List<UUID> b) {
        List<UUID> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private DailyNoteResponse.bulkResult.skipped skip(UUID id, String dogName, ErrorCode code) {
        return new DailyNoteResponse.bulkResult.skipped(id, dogName, code.getCode(), code.getMessage());
    }
}
