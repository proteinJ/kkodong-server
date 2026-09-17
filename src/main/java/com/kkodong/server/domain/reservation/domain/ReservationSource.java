package com.kkodong.server.domain.reservation.domain;

/**
 * 예약이 어디서 들어왔는지. reservations.source CHECK와 짝을 이룬다.
 *
 * <p>측정용 필드다 — 점주가 전화로 받아 대신 넣는 비중이 계속 높다면 견주 앱의
 * 예약 흐름이 실제로 쓰이지 않는다는 뜻이고, 그건 화면을 고쳐야 한다는 신호다.
 */
public enum ReservationSource {
    /** 견주가 앱에서 직접 신청. */
    OWNER,
    /** 점주가 전화 등으로 받아 대신 등록. */
    PARTNER
}
