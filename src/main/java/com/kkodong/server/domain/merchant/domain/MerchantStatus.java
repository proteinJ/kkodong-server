package com.kkodong.server.domain.merchant.domain;

/**
 * 매장 상태. merchants.status CHECK와 짝을 이룬다.
 *
 * <ul>
 *   <li>{@code PENDING} — 사업자 진위확인 전. 견주 앱에 노출하지 않는다</li>
 *   <li>{@code ACTIVE} — 정상 운영. KG-01 지도 탐색에 노출된다</li>
 *   <li>{@code SUSPENDED} — 운영 중단. 노출에서 빼되 기존 원생·예약은 유지된다</li>
 *   <li>{@code CLOSED} — 폐업. ⚠️ 행을 지우지 않는다 — 과거 예약·이용권 이력이 매달려 있다</li>
 * </ul>
 */
public enum MerchantStatus {
    PENDING, ACTIVE, SUSPENDED, CLOSED
}
