package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    /** PN-10 원생 목록. 상태 필터가 기본으로 걸린다. */
    List<Enrollment> findAllByMerchantIdAndStatus(UUID merchantId, EnrollmentStatus status);

    /** 견주 앱 KG-09 "내 유치원". 탈퇴로 ownerUserId가 null이 된 행은 잡히지 않는다. */
    List<Enrollment> findAllByOwnerUserId(UUID ownerUserId);
}
