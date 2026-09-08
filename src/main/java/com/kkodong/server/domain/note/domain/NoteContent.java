package com.kkodong.server.domain.note.domain;

import org.springframework.util.StringUtils;

/**
 * 알림장 내용 다섯 항목. 개별 작성·일괄 작성·템플릿이 같은 모양을 쓴다.
 *
 * <p><b>왜 한 덩어리로 묶는가</b>: 일괄 작성과 템플릿이 다루는 단위가 정확히 이것이다.
 * 항목을 개별 인자로 늘어놓으면 같은 타입(String) 다섯 개가 나란히 서서, 순서를 바꿔 넣어도
 * 컴파일이 통과한다 — 활동란에 배변 내용이 들어가는 식의 사고가 조용히 난다.
 *
 * @param activity  활동
 * @param meal      식사
 * @param bathroom  배변
 * @param condition 컨디션
 * @param remark    특이사항
 */
public record NoteContent(
        String activity,
        String meal,
        String bathroom,
        String condition,
        String remark
) {
    /**
     * 값이 하나도 없으면 보낼 것이 없다. 빈 알림장 발송을 막는 데 쓴다.
     *
     * <p>⚠️ {@code @JsonIgnore}가 없으면 JSONB 왕복이 깨진다. Jackson은 {@code isXxx()}를
     * getter로 보고 직렬화할 때 {@code "empty": false}를 끼워 넣는데, 다시 읽을 때
     * record에 없는 속성이라 {@code UnrecognizedPropertyException}이 난다.
     * 이 record는 {@code note_templates.content} 컬럼에 통째로 저장된다.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return !StringUtils.hasText(activity)
                && !StringUtils.hasText(meal)
                && !StringUtils.hasText(bathroom)
                && !StringUtils.hasText(condition)
                && !StringUtils.hasText(remark);
    }

    /**
     * 이 내용을 바탕으로 하되, 인자로 온 값이 있으면 그쪽을 쓴다.
     *
     * <p>템플릿을 불러온 뒤 몇 항목만 고치는 흐름(FR-PN14-01의 입력 부담 최소화)이
     * 이 메서드 하나로 표현된다.
     */
    public NoteContent overlay(NoteContent override) {
        if (override == null) return this;
        return new NoteContent(
                StringUtils.hasText(override.activity) ? override.activity : activity,
                StringUtils.hasText(override.meal) ? override.meal : meal,
                StringUtils.hasText(override.bathroom) ? override.bathroom : bathroom,
                StringUtils.hasText(override.condition) ? override.condition : condition,
                StringUtils.hasText(override.remark) ? override.remark : remark);
    }

    public static NoteContent empty() {
        return new NoteContent(null, null, null, null, null);
    }
}
