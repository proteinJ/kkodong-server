package com.kkodong.server.domain.merchant.domain;

/**
 * 이용권 상품 유형. merchant_products.product_type / passes.product_type CHECK와 짝을 이룬다.
 *
 * <ul>
 *   <li>{@code COUNT} — 횟수권. 총 N회. 등원할 때마다 1회씩 차감된다</li>
 *   <li>{@code PERIOD} — 기간권. 유효기간 내 무제한. 차감할 회차가 없다</li>
 * </ul>
 *
 * <p>발급된 이용권(passes)에도 같은 값이 스냅샷으로 복사된다 — 상품이 나중에 바뀌거나
 * 판매 중단돼도 이미 팔린 이용권의 성격은 그대로여야 하기 때문이다.
 */
public enum ProductType {
    COUNT, PERIOD
}
