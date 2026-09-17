package com.kkodong.server.domain.merchant.repository;

import com.kkodong.server.domain.merchant.domain.MerchantStaff;
import com.kkodong.server.domain.merchant.domain.StaffRole;
import com.kkodong.server.domain.merchant.domain.StaffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantStaffRepository extends JpaRepository<MerchantStaff, UUID> {

    /**
     * PN-01 로그인 직후 "내가 속한 매장" — 점주 앱의 첫 질의다.
     * 승인 대기(PENDING)도 보여줘야 선생님이 자기 상태를 알 수 있으므로 상태로 거르지 않는다.
     */
    List<MerchantStaff> findAllByUserId(UUID userId);

    /**
     * 권한 검증의 진입점(PC-12 — RLS가 없으므로 Service 계층에서 직접 확인한다).
     * 조회 결과에 {@link MerchantStaff#can(String)}을 걸어 쓸 것.
     */
    Optional<MerchantStaff> findByMerchantIdAndUserId(UUID merchantId, UUID userId);

    /** PN-18 스태프 목록 + 승인 대기 배지. */
    List<MerchantStaff> findAllByMerchantIdAndStatus(UUID merchantId, StaffStatus status);

    /**
     * 마지막 원장의 퇴사를 막기 위한 카운트(FR-PN18-02 관련).
     * "active director가 1명뿐이면 퇴사 거부"는 DB 제약으로 표현할 수 없어 여기서 센다.
     */
    long countByMerchantIdAndRoleAndStatus(UUID merchantId, StaffRole role, StaffStatus status);
}
