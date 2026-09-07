package com.kkodong.server.domain.enrollment.service;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.dto.ApplicationRequest;
import com.kkodong.server.domain.enrollment.dto.ApplicationResponse;
import com.kkodong.server.domain.enrollment.repository.EnrollmentApplicationRepository;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.domain.user.domain.User;
import com.kkodong.server.domain.user.repository.UserRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 신청 접수함(PN-07). 흐름 F-13의 1~2단계다.
 *
 * <p><b>신청서 제출은 견주 앱(KG-06/07) 몫이고, 여기는 처리하는 쪽이다.</b>
 * 그래서 이 서비스에 신청 생성 메서드가 없다.
 *
 * <p>★ 승인이 곧 원생 생성이다(FR-PN07-01). 이 서비스가 점주 앱 전체에서 유일하게
 * {@link Enrollment}를 만드는 곳이며, 여기서 만들어진 원생이 예약(PN-19)·출석(PN-09)·
 * 이용권(PN-12)의 대상이 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationInboxService {

    private final EnrollmentApplicationRepository applicationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DogRepository dogRepository;
    private final UserRepository userRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * 접수함 목록(PN-07). {@code status}를 생략하면 대기 중만 준다.
     *
     * <p>반려견 정보를 함께 실어 내린다 — 점주가 수용 조건(체급·중성화·접종)에 맞는지
     * 판단하려면 그 정보가 필요하고, 그걸 꼬동 프로필에서 그대로 가져오는 것이
     * FR-PN11-01이 말한 "점주가 꼬동을 쓸 이유 그 자체"다.
     */
    public List<ApplicationResponse.item> getInbox(
            UUID merchantId, UUID userId, ApplicationStatus status) {
        accessGuard.requireStaff(merchantId, userId);

        List<EnrollmentApplication> applications = status == null
                ? applicationRepository.findAllByMerchantIdAndStatusOrderByCreatedAtDesc(
                        merchantId, ApplicationStatus.PENDING)
                : applicationRepository.findAllByMerchantIdAndStatusOrderByCreatedAtDesc(
                        merchantId, status);
        if (applications.isEmpty()) return List.of();

        // 강아지를 건별로 조회하면 N+1이 된다. id를 모아 한 번에 읽는다.
        Map<UUID, Dog> dogs = dogRepository
                .findAllById(applications.stream().map(EnrollmentApplication::getDogId).distinct().toList())
                .stream().collect(Collectors.toMap(Dog::getId, Function.identity()));

        return applications.stream()
                .map(a -> ApplicationResponse.item.of(a, dogs.get(a.getDogId())))
                .toList();
    }

    /** 대기 중 신청 수(PN-07 홈 배지). */
    public long countPending(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return applicationRepository.countByMerchantIdAndStatus(merchantId, ApplicationStatus.PENDING);
    }

    /** 신청 상세(PN-07 1단계 — 반려견 정보와 서약서를 확인한다). */
    public ApplicationResponse.item get(UUID merchantId, UUID userId, UUID applicationId) {
        accessGuard.requireStaff(merchantId, userId);
        EnrollmentApplication application = findInMerchant(merchantId, applicationId);
        return ApplicationResponse.item.of(application, dogRepository.findById(application.getDogId()).orElse(null));
    }

    /**
     * 승인(PN-07, 흐름 F-13 2단계). ★ 이 시점에 원생이 생긴다.
     *
     * <p>⚠️ 신청 승인과 원생 생성은 반드시 하나의 트랜잭션이다. 승인만 되고 원생이 안
     * 생기면 견주에게는 "승인됨"으로 보이는데 매장에는 원생이 없어, 등원도 이용권 발급도
     * 되지 않는 상태가 된다 — 그리고 신청은 이미 처리됨이라 다시 승인할 수도 없다.
     *
     * <p>원생에 이름·견종을 스냅샷으로 복사한다. 견주가 나중에 탈퇴하면 dogs가 지워지고
     * {@code dog_id}는 null이 되는데, 그때도 점주 화면이 "이름 없는 원생"을 보이면 안 된다
     * ({@link Enrollment} 참조).
     */
    @Transactional
    public ApplicationResponse.approveResult approve(
            UUID merchantId, UUID userId, UUID applicationId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ENROLLMENT);

        EnrollmentApplication application = findInMerchant(merchantId, applicationId);

        // 중복 승인하면 같은 강아지의 원생이 둘로 갈리고, 이용권과 출석이 각각 다른
        // 원생에 붙어 어느 쪽이 진짜인지 알 수 없게 된다.
        if (enrollmentRepository.existsByMerchantIdAndDogIdAndStatusNot(
                merchantId, application.getDogId(), EnrollmentStatus.WITHDRAWN)) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        application.approve(userId); // PENDING이 아니면 여기서 AP002

        Dog dog = dogRepository.findById(application.getDogId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOG_NOT_FOUND));
        User owner = userRepository.findById(application.getApplicantUserId()).orElse(null);

        Enrollment enrollment = enrollmentRepository.save(Enrollment.builder()
                .merchantId(merchantId)
                .dogId(dog.getId())
                .ownerUserId(application.getApplicantUserId())
                .dogNameSnapshot(dog.getName())
                .dogBreedSnapshot(dog.getBreed())
                .ownerNameSnapshot(owner == null ? null : owner.getDisplayName())
                .applicationId(application.getId())
                .status(EnrollmentStatus.ACTIVE)
                .build());

        return new ApplicationResponse.approveResult(
                ApplicationResponse.item.of(application, dog), enrollment.getId());
    }

    /** 거절(PN-07, FR-PN07-01). 사유는 견주에게 그대로 전달된다. 원생은 만들지 않는다. */
    @Transactional
    public ApplicationResponse.item reject(
            UUID merchantId, UUID userId, UUID applicationId, ApplicationRequest.reject request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_ENROLLMENT);

        EnrollmentApplication application = findInMerchant(merchantId, applicationId);
        application.reject(userId, request.reason());

        return ApplicationResponse.item.of(application, dogRepository.findById(application.getDogId()).orElse(null));
    }

    /** ⚠️ 남의 매장 신청 ID를 넣어도 통과하지 않게 소속을 대조한다. */
    private EnrollmentApplication findInMerchant(UUID merchantId, UUID applicationId) {
        EnrollmentApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!application.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        return application;
    }
}
