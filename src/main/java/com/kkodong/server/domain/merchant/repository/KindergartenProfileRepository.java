package com.kkodong.server.domain.merchant.repository;

import com.kkodong.server.domain.merchant.domain.KindergartenProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * PK가 곧 merchant_id 이므로 {@code findById(merchantId)} 가 매장별 조회다.
 * 별도 조회 메서드를 두지 않는다.
 */
public interface KindergartenProfileRepository extends JpaRepository<KindergartenProfile, UUID> {
}
