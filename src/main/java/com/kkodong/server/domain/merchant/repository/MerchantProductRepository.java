package com.kkodong.server.domain.merchant.repository;

import com.kkodong.server.domain.merchant.domain.MerchantProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MerchantProductRepository extends JpaRepository<MerchantProduct, UUID> {

    /** PN-04 상품 관리 화면 — 판매 중단분까지 함께 본다. */
    List<MerchantProduct> findAllByMerchantId(UUID merchantId);

    /** 견주 앱 KG-02 요금표 · PN-12 발급 화면 — 판매 중인 상품만. */
    List<MerchantProduct> findAllByMerchantIdAndIsActiveTrue(UUID merchantId);
}
