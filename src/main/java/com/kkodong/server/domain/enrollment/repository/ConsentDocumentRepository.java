package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.ConsentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentDocumentRepository extends JpaRepository<ConsentDocument, UUID> {

    Optional<ConsentDocument> findByMerchantIdAndIsActiveTrue(UUID merchantId);

    List<ConsentDocument> findAllByMerchantIdOrderByVersionDesc(UUID merchantId);
}
