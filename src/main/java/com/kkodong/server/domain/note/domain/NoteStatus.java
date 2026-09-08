package com.kkodong.server.domain.note.domain;

/**
 * 알림장 발송 상태. daily_notes.status CHECK와 짝을 이룬다.
 *
 * <p>⚠️ {@code DRAFT}는 보호자 조회 응답에서 반드시 제외해야 한다.
 * 쓰다 만 알림장이 새면 그 자체로 CS가 된다("우리 아이만 내용이 없어요").
 */
public enum NoteStatus {
    DRAFT, SENT
}
