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

    /**
     * 이 강아지가 이 매장에 이미 재원 중인지(PN-07 승인 전 검사).
     *
     * <p>승인은 원생을 만드는 행위라, 중복 승인하면 같은 강아지의 원생이 둘로 갈린다.
     * 이용권과 출석이 각각 다른 원생에 붙어 어느 쪽이 진짜인지 알 수 없게 된다.
     *
     * <p>DB의 {@code uq_enrollments_active_dog}와 같은 규칙을 서버에서 먼저 봐서
     * 500 대신 409를 준다. 퇴원(WITHDRAWN)은 제외해야 재등록이 가능하다.
     */
    boolean existsByMerchantIdAndDogIdAndStatusNot(
            UUID merchantId, UUID dogId, EnrollmentStatus status);
}
