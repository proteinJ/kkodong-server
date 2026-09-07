package com.kkodong.server.domain.merchant.repository;

import com.kkodong.server.domain.merchant.domain.Merchant;
import com.kkodong.server.domain.merchant.domain.MerchantStatus;
import com.kkodong.server.domain.merchant.domain.MerchantType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    /**
     * 사업자등록번호 중복 확인(PN-02). 진위확인을 통과해도 이미 등록된 사업장이면
     * 새 매장을 만들지 않는다 — 같은 매장이 둘로 생기면 원생·이용권이 갈라진다.
     */
    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);

    Optional<Merchant> findByBusinessRegistrationNumber(String businessRegistrationNumber);

    /** 견주 앱 KG-01 목록. ⚠️ 노출 대상은 ACTIVE 뿐이다 — 호출부에서 상태를 빠뜨리지 말 것. */
    List<Merchant> findAllByMerchantTypeAndStatus(MerchantType merchantType, MerchantStatus status);
}
