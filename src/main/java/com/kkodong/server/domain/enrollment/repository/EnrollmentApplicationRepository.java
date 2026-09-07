package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.ApplicationStatus;
import com.kkodong.server.domain.enrollment.domain.EnrollmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnrollmentApplicationRepository extends JpaRepository<EnrollmentApplication, UUID> {

    /** PN-07 접수함. 최근 신청부터 본다. */
    List<EnrollmentApplication> findAllByMerchantIdAndStatusOrderByCreatedAtDesc(
            UUID merchantId, ApplicationStatus status);

    List<EnrollmentApplication> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /** 홈 배지 카운트(FR-PN07-01 진입 경로). */
    long countByMerchantIdAndStatus(UUID merchantId, ApplicationStatus status);
}
