package com.kkodong.server.domain.merchant.domain;

/**
 * 매장 업종. V6__add_merchant_domain.sql의 merchants.merchant_type CHECK와 짝을 이룬다.
 *
 * <p>값이 늘어나면 마이그레이션이 필요한데 이는 의도된 마찰이다 — 새 업종은 어차피
 * 전용 프로필 테이블(kindergarten_profiles의 형제)과 예약 규칙을 함께 들여야 하므로,
 * 값만 조용히 추가되는 상황 자체가 있어서는 안 된다.
 */
public enum MerchantType {
    KINDERGARTEN, GROOMING, CLINIC
}
