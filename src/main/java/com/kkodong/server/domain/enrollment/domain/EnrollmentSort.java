package com.kkodong.server.domain.enrollment.domain;

/**
 * 원생 목록 정렬(PN-10, FR-PN10-01).
 *
 * <p>⚠️ {@link #PAYMENT_DUE}가 사실상 <b>영업 리스트</b>다. 점주가 이 화면을 여는 가장 큰
 * 이유는 "누구에게 재결제를 안내해야 하나"이며, 그 답이 매출로 직결된다.
 * PN-17(결제 임박 관리)이 이 정렬을 자동 집계로 확장한 것이다.
 */
public enum EnrollmentSort {
    /** 등록일 최신순. 기본값. */
    ENROLLED_DESC,
    /** 결제 임박순 — 잔여가 적고 만료가 가까운 원생부터. */
    PAYMENT_DUE,
    /** 최근 등원순 — 오래 안 온 원생을 찾을 때는 역순으로 보면 된다. */
    RECENT_ATTENDANCE,
    /** 등원 빈도순 — 자주 오는 원생부터. */
    ATTENDANCE_FREQUENCY
}
