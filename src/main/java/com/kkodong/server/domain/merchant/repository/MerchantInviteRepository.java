package com.kkodong.server.domain.merchant.repository;

import com.kkodong.server.domain.merchant.domain.MerchantInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantInviteRepository extends JpaRepository<MerchantInvite, UUID> {

    /**
     * QR·딥링크 진입점(KG-05, 흐름 F-12). 코드로 매장을 찾는다.
     *
     * <p>⚠️ 조회에 성공했다고 통과가 아니다 — 폐기·만료 판정은
     * {@link MerchantInvite#isUsableAt} 로 반드시 한 번 더 거를 것.
     */
    Optional<MerchantInvite> findByCode(String code);

    /** PN-05 매장별 발급 이력. */
    List<MerchantInvite> findAllByMerchantId(UUID merchantId);
}
