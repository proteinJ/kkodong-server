package com.kkodong.server.domain.note.repository;

import com.kkodong.server.domain.note.domain.DailyNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyNoteRepository extends JpaRepository<DailyNote, UUID> {

    /**
     * PN-14 작성 화면 — 오늘 이 매장의 알림장 전부(초안 포함).
     * 누가 아직 안 썼는지는 출석 목록과 이 목록의 차집합으로 나온다.
     */
    List<DailyNote> findAllByMerchantIdAndNoteDate(UUID merchantId, LocalDate noteDate);

    /** 하루 한 장이므로 (원생, 날짜)로 유일하다 — DB의 uq_daily_notes_per_day가 보장한다. */
    Optional<DailyNote> findByEnrollmentIdAndNoteDate(UUID enrollmentId, LocalDate noteDate);

    /** 여러 원생의 같은 날 알림장을 한 번에 — 일괄 작성에서 기존 초안을 찾을 때 쓴다. */
    List<DailyNote> findAllByEnrollmentIdInAndNoteDate(List<UUID> enrollmentIds, LocalDate noteDate);

    /** PN-11 원생 상세 · KG-10 알림장 목록. 날짜 역순이 기본이다. */
    List<DailyNote> findTop30ByEnrollmentIdOrderByNoteDateDesc(UUID enrollmentId);
}
