package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.ApplicationForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationFormRepository extends JpaRepository<ApplicationForm, UUID> {

    /** 견주 앱(KG-06)이 렌더링할 현재 양식. 매장당 하나만 활성이다. */
    Optional<ApplicationForm> findByMerchantIdAndIsActiveTrue(UUID merchantId);

    /** PN-06 버전 이력. 과거 제출값을 해석하려면 당시 양식이 필요하다. */
    List<ApplicationForm> findAllByMerchantIdOrderByVersionDesc(UUID merchantId);
}
