package com.kkodong.server.domain.merchant.domain;

/**
 * 매장 내 역할. merchant_staff.role CHECK와 짝을 이룬다.
 *
 * <p>⚠️ 이름에 {@code OWNER}를 쓰지 않는다. 이 코드베이스에서 owner는 이미
 * {@code dogs.owner_id} = 견주를 뜻한다(PC-14, FR-PT03-02). 점주 쪽에 owner를 또 쓰면
 * 같은 단어가 정반대를 가리키게 되므로, 요구사항 문서 용어 그대로
 * director(원장) / staff(선생님)를 쓴다.
 *
 * <p>⚠️ {@code DIRECTOR}는 {@code MerchantStaff.permissions}를 보지 않고 전권으로 취급한다 —
 * 원장에게서 권한을 뺏는 상태를 만들면 매장이 잠긴다. Service 계층에서 role을 먼저 볼 것.
 */
public enum StaffRole {
    DIRECTOR, STAFF
}
