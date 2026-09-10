package com.kkodong.server.domain.merchant.service;

import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.dto.MerchantRequest;
import com.kkodong.server.domain.merchant.dto.MerchantResponse;
import com.kkodong.server.domain.merchant.repository.*;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.util.Locations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 매장 개설·정보 관리(PN-02, PN-04, PN-05).
 *
 * <p>권한 검증은 전부 {@link MerchantAccessGuard}를 통한다 — 흩어뜨리지 말 것(PC-12).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantStaffRepository merchantStaffRepository;
    private final KindergartenProfileRepository kindergartenProfileRepository;
    private final MerchantProductRepository merchantProductRepository;
    private final MerchantInviteRepository merchantInviteRepository;
    private final MerchantAccessGuard accessGuard;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 매장 개설(PN-02, 흐름 F-17). 개설자가 곧 원장이 된다.
     *
     * <p>⚠️ 매장 생성과 원장 소속 생성은 하나의 트랜잭션이어야 한다. 매장만 만들어지고
     * 원장이 안 붙으면 아무도 접근할 수 없는 매장이 남고, 사업자등록번호가 UNIQUE라
     * 같은 번호로 다시 만들 수도 없다 — 손으로 고쳐야 하는 상태가 된다.
     *
     * <p>TODO(FR-PN02-01): 국세청 진위확인 API 연동. 지금은 형식 검증만 하고 통과시킨다.
     *   연동 전까지 verify()를 호출하므로 매장이 즉시 ACTIVE가 된다 — 실제 출시 전에
     *   반드시 검증 단계를 끼워 넣을 것.
     */
    @Transactional
    public MerchantResponse.detailInfo create(UUID userId, MerchantRequest.create request) {
        if (merchantRepository.existsByBusinessRegistrationNumber(request.businessRegistrationNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_REGISTRATION);
        }

        Merchant merchant = merchantRepository.save(Merchant.builder()
                .merchantType(request.merchantType() == null ? MerchantType.KINDERGARTEN : request.merchantType())
                .name(request.name())
                .businessRegistrationNumber(request.businessRegistrationNumber())
                .representativeName(request.representativeName())
                .businessOpenedOn(request.businessOpenedOn())
                .build());

        merchant.verify(); // TODO: 진위확인 성공 시에만 호출하도록 바꿀 것

        merchantStaffRepository.save(MerchantStaff.builder()
                .merchantId(merchant.getId())
                .userId(userId)
                .role(StaffRole.DIRECTOR)
                .status(StaffStatus.ACTIVE) // 개설자는 승인 절차 없이 바로 활성
                .build());

        // 유치원이면 수용 조건 행을 함께 만든다. 없으면 예약 정원 검사가 매번 null 분기를 타야 한다.
        if (merchant.getMerchantType() == MerchantType.KINDERGARTEN) {
            kindergartenProfileRepository.save(KindergartenProfile.builder()
                    .merchantId(merchant.getId())
                    .build());
        }

        return MerchantResponse.detailInfo.from(merchant);
    }

    /** 매장 상세(PN-04). 스태프만 볼 수 있다. */
    public MerchantResponse.detailInfo get(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return MerchantResponse.detailInfo.from(findMerchant(merchantId));
    }

    /**
     * PN-01 로그인 직후 "내가 속한 매장" 목록.
     *
     * <p>승인 대기 소속도 함께 내려준다 — 선생님이 자기 신청 상태를 알아야 한다.
     * 매장을 건별로 조회하면 N+1이 되므로 id를 모아 한 번에 읽는다(BlockService와 같은 방식).
     */
    public List<MerchantResponse.myMerchantItem> getMyMerchants(UUID userId) {
        List<MerchantStaff> memberships = merchantStaffRepository.findAllByUserId(userId);
        if (memberships.isEmpty()) return List.of();

        var merchants = merchantRepository
                .findAllById(memberships.stream().map(MerchantStaff::getMerchantId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Merchant::getId, java.util.function.Function.identity()));

        return memberships.stream()
                .map(s -> MerchantResponse.myMerchantItem.of(s, merchants.get(s.getMerchantId())))
                .toList();
    }

    /** 매장 정보 수정(PN-04). 원장 전용. */
    @Transactional
    public MerchantResponse.detailInfo update(UUID merchantId, UUID userId, MerchantRequest.update request) {
        accessGuard.requireDirector(merchantId, userId);

        Merchant merchant = findMerchant(merchantId);
        merchant.patch(new MerchantUpdate(
                request.name(), request.address(),
                // 위경도는 짝으로만 온다(DTO에서 검증). ⚠️ Locations.of(lat, lng) 순서 주의.
                request.latitude() == null ? null : Locations.of(request.latitude(), request.longitude()),
                request.phone(), request.description(),
                request.imageUrls(), request.businessHours(), request.closedDates()
        ));
        return MerchantResponse.detailInfo.from(merchant);
    }

    /** 유치원 수용 조건 조회(PN-04). */
    public MerchantResponse.kindergartenProfileInfo getKindergartenProfile(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return MerchantResponse.kindergartenProfileInfo.from(findProfile(merchantId));
    }

    /** 유치원 수용 조건 수정(PN-04, FR-PN04-02). 원장 전용. */
    @Transactional
    public MerchantResponse.kindergartenProfileInfo updateKindergartenProfile(
            UUID merchantId, UUID userId, MerchantRequest.kindergartenProfile request) {
        accessGuard.requireDirector(merchantId, userId);

        KindergartenProfile profile = findProfile(merchantId);
        // 부분 수정 — null은 "안 바꿈". dailyCapacity의 null은 "무제한"이라는 값이기도 해서
        // 이 API로는 무제한으로 되돌릴 수 없다. 되돌리는 화면이 아직 없어 지금은 이대로 둔다.
        KindergartenProfile updated = KindergartenProfile.builder()
                .merchantId(merchantId)
                .acceptedSizes(request.acceptedSizes() != null ? request.acceptedSizes() : profile.getAcceptedSizes())
                .dailyCapacity(request.dailyCapacity() != null ? request.dailyCapacity() : profile.getDailyCapacity())
                .requiresNeutered(request.requiresNeutered() != null ? request.requiresNeutered() : profile.getRequiresNeutered())
                .requiredVaccinations(request.requiredVaccinations() != null ? request.requiredVaccinations() : profile.getRequiredVaccinations())
                .build();

        return MerchantResponse.kindergartenProfileInfo.from(kindergartenProfileRepository.save(updated));
    }

    /** 이용권 상품 목록(PN-04). */
    public List<MerchantResponse.productInfo> getProducts(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return merchantProductRepository.findAllByMerchantId(merchantId).stream()
                .map(MerchantResponse.productInfo::from)
                .toList();
    }

    /**
     * 이용권 상품 등록(PN-04, FR-PN04-01). 원장 전용.
     *
     * <p>DB의 {@code chk_merchant_products_shape}와 같은 규칙을 서버에서 먼저 본다 —
     * DB 제약에 걸리면 500이 나가지만, 여기서 걸면 무엇이 잘못됐는지 알려줄 수 있다.
     */
    @Transactional
    public MerchantResponse.productInfo createProduct(
            UUID merchantId, UUID userId, MerchantRequest.createProduct request) {
        accessGuard.requireDirector(merchantId, userId);

        boolean shapeValid = switch (request.productType()) {
            case COUNT -> request.totalCount() != null;
            case PERIOD -> request.validDays() != null;
        };
        if (!shapeValid) throw new BusinessException(ErrorCode.INVALID_PRODUCT_SHAPE);

        MerchantProduct product = merchantProductRepository.save(MerchantProduct.builder()
                .merchantId(merchantId)
                .name(request.name())
                .productType(request.productType())
                .totalCount(request.totalCount())
                .validDays(request.validDays())
                .price(request.price())
                .build());

        return MerchantResponse.productInfo.from(product);
    }

    /** 이용권 상품 판매 중단(PN-04). 원장 전용. 발급분이 참조하므로 삭제하지 않는다. */
    @Transactional
    public void deactivateProduct(UUID merchantId, UUID userId, UUID productId) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantProduct product = merchantProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_PRODUCT_NOT_FOUND));
        // ⚠️ 남의 매장 상품 ID를 넣어도 통과하지 않게 소속을 대조한다.
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.MERCHANT_PRODUCT_NOT_FOUND);
        }
        product.deactivate();
    }

    /** 모집 QR 발급(PN-05, FR-PN05-01). 원장 전용. */
    @Transactional
    public MerchantResponse.inviteInfo createInvite(
            UUID merchantId, UUID userId, MerchantRequest.createInvite request) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantInvite invite = merchantInviteRepository.save(MerchantInvite.builder()
                .merchantId(merchantId)
                .code(generateInviteCode())
                .expiresAt(request.validDays() == null ? null
                        : OffsetDateTime.now().plusDays(request.validDays()))
                .createdByUserId(userId)
                .build());

        return MerchantResponse.inviteInfo.from(invite);
    }

    /** 발급한 QR 목록(PN-05). */
    public List<MerchantResponse.inviteInfo> getInvites(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return merchantInviteRepository.findAllByMerchantId(merchantId).stream()
                .map(MerchantResponse.inviteInfo::from)
                .toList();
    }

    /** QR 폐기(PN-05). 원장 전용. 이미 인쇄돼 나간 QR을 무효화하는 유일한 수단이다. */
    @Transactional
    public void revokeInvite(UUID merchantId, UUID userId, UUID inviteId) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantInvite invite = merchantInviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_NOT_USABLE));
        if (!invite.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.INVITE_NOT_USABLE);
        }
        invite.revoke();
    }

    private Merchant findMerchant(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_NOT_FOUND));
    }

    private KindergartenProfile findProfile(UUID merchantId) {
        return kindergartenProfileRepository.findById(merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_NOT_FOUND));
    }

    /**
     * QR·링크에 실을 코드. ⚠️ merchantId를 싣지 않는다 — 코드를 폐기해도 매장 식별자는
     * 바뀌지 않으므로, id를 실으면 한 번 유출된 링크를 영원히 막을 수 없다.
     */
    private String generateInviteCode() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
