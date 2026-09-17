package com.kkodong.server.domain.note.repository;

import com.kkodong.server.domain.note.domain.NoteTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteTemplateRepository extends JpaRepository<NoteTemplate, UUID> {

    /** 매장 단위로 공유한다 — 선생님이 바뀌어도 매장의 말투가 이어져야 한다. */
    List<NoteTemplate> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
