package com.kkodong.server.domain.enrollment.service;

import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.dto.PassRequest;
import com.kkodong.server.domain.enrollment.dto.PassResponse;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.repository.PassLedgerRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.merchant.domain.MerchantProduct;
import com.kkodong.server.domain.merchant.repository.MerchantProductRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 이용권 발급·연장·환불·조정과 변동 이력(PN-12).
 *
 * <p><b>매출 데이터를 다루는 곳이다.</b> 모든 변동은 {@link PassLedger}에 한 줄을 남긴다 —
 * 원장을 처음부터 더하면 현재 잔여가 나와야 하고, 어긋나면 어느 지점에서 깨졌는지
 * {@code balanceAfter}로 찾는다(FR-PN12-01).
 *
 * <p>⚠️ 권한은 {@code PERM_PASS}다. FR-PN18-01에 따라 기본적으로 원장 전용이며,
 * 선생님에게 주려면 원장이 명시적으로 켜야 한다({@link MerchantAccessGuard} 참조).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassService {

    private final PassRepository passRepository;
    private final PassLedgerRepository passLedgerRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MerchantProductRepository merchantProductRepository;
    private final MerchantAccessGuard accessGuard;

    @PersistenceContext
    private EntityManager em;

    /**
     * 이용권 발급(PN-12). 흐름 F-13(신청 승인 → 발급)과 F-15(재결제)의 착지점이다.
     *
     * <p>회차·유효기간·가격을 요청으로 받지 않고 상품 정의에서 가져와 <b>발급 시점
     * 스냅샷</b>으로 굳힌다. 상품 가격이 나중에 바뀌어도 이미 팔린 이용권의 조건은
     * 그대로여야 하고, 클라이언트가 조건을 보내게 하면 화면에 보인 가격과 실제 발급분이
     * 달라질 수 있어 매출 분쟁이 된다.
     *
     * <p>판매 중단된 상품({@code isActive=false})도 발급할 수 있게 둔다 — 점주가 지면으로
     * 이미 판 이용권을 뒤늦게 입력하는 일이 실제로 생기며, 막으면 상품을 되살렸다가
     * 다시 내리는 우회를 하게 된다.
     */
    @Transactional
    public PassResponse.detailInfo issue(UUID merchantId, UUID userId, PassRequest.issue request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_PASS);

        Enrollment enrollment = findEnrollmentInMerchant(merchantId, request.enrollmentId());
        MerchantProduct product = merchantProductRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MERCHANT_PRODUCT_NOT_FOUND));
        // ⚠️ 남의 매장 상품 ID로 발급하지 못하게 소속을 대조한다.
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.MERCHANT_PRODUCT_NOT_FOUND);
        }

        LocalDate today = LocalDate.now();
        Pass pass = passRepository.save(Pass.builder()
                .enrollmentId(enrollment.getId())
                .productId(product.getId())
                .productNameSnapshot(product.getName())
                .productType(product.getProductType())
                .priceSnapshot(product.getPrice())
                .remainingCount(product.getTotalCount())
                .totalCount(product.getTotalCount())
                .expiresOn(product.expiryFrom(today))
                .status(PassStatus.ACTIVE)
                .issuedByUserId(userId)
                .build());

        passLedgerRepository.save(
                PassLedger.grant(pass.getId(), product.getTotalCount(), userId, request.reason()));

        // issuedAt·createdAt은 DB DEFAULT now()가 채우므로 INSERT 직후 엔티티에는 null이다.
        // 그대로 내보내면 발급 응답에서는 null이고 목록 조회에서는 값이 있어, 같은 필드가
        // 호출에 따라 달라진다. DB에서 다시 읽어 채운다(BlockService와 같은 방식).
        em.flush();
        em.refresh(pass);

        return PassResponse.detailInfo.from(pass, today);
    }

    /**
     * 원생의 이용권 목록(PN-11 원생 상세, KG-13).
     *
     * <p>사용 가능한 것을 위로, 그다음 만료가 임박한 순으로 준다 — 점주가 화면에서
     * 가장 먼저 봐야 하는 것이 "지금 쓸 수 있는 이용권"이고, 그다음이 "곧 만료될 것"이다.
     */
    public List<PassResponse.detailInfo> getByEnrollment(
            UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requireStaff(merchantId, userId);
        findEnrollmentInMerchant(merchantId, enrollmentId);

        LocalDate today = LocalDate.now();
        return passRepository.findAllByEnrollmentId(enrollmentId).stream()
                .map(p -> PassResponse.detailInfo.from(p, today))
                .sorted(Comparator
                        .comparing(PassResponse.detailInfo::usableToday).reversed()
                        .thenComparing(PassResponse.detailInfo::daysUntilExpiry,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 변동 이력(PN-12 감사 로그, KG-13 차감 이력). 시간 역순.
     *
     * <p>등원으로 인한 차감에는 {@code sourceAttendanceId}가 붙어 있어 어느 날의 등원이
     * 어떤 회차를 썼는지 이어볼 수 있다.
     */
    public List<PassResponse.ledgerItem> getLedger(UUID merchantId, UUID userId, UUID passId) {
        accessGuard.requireStaff(merchantId, userId);
        findPassInMerchant(merchantId, passId);

        return passLedgerRepository.findAllByPassIdOrderByCreatedAtDesc(passId).stream()
                .map(PassResponse.ledgerItem::from)
                .toList();
    }

    /** 기간 연장(PN-12). 회차는 건드리지 않는다. */
    @Transactional
    public PassResponse.detailInfo extend(
            UUID merchantId, UUID userId, UUID passId, PassRequest.extend request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_PASS);

        Pass pass = findPassInMerchant(merchantId, passId);
        LocalDate today = LocalDate.now();
        pass.extend(request.days(), today);

        passLedgerRepository.save(PassLedger.extension(
                passId, pass.getRemainingCount(), userId, request.reason()));

        return PassResponse.detailInfo.from(pass, today);
    }

    /**
     * 환불(PN-12). 되돌릴 수 없는 종료 처리다.
     *
     * <p>⚠️ 남아 있던 회차를 0으로 떨어뜨린다. 남겨두면 환불된 이용권으로 등원이 되어
     * 매출과 장부가 어긋난다.
     */
    @Transactional
    public PassResponse.detailInfo refund(
            UUID merchantId, UUID userId, UUID passId, PassRequest.refund request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_PASS);

        Pass pass = findPassInMerchant(merchantId, passId);
        Integer removed = pass.refund();

        passLedgerRepository.save(PassLedger.refund(passId, removed, userId, request.reason()));

        return PassResponse.detailInfo.from(pass, LocalDate.now());
    }

    /**
     * 수동 조정(PN-12). 착오 정정이나 서비스 회차 제공 등 예외 상황용이다.
     *
     * <p>⚠️ 일상적으로 쓰라고 만든 것이 아니다. 사유가 필수이며 처리자와 함께 원장에 남는다.
     */
    @Transactional
    public PassResponse.detailInfo adjust(
            UUID merchantId, UUID userId, UUID passId, PassRequest.adjust request) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_PASS);

        Pass pass = findPassInMerchant(merchantId, passId);
        Integer balanceAfter = pass.adjust(request.delta());

        passLedgerRepository.save(PassLedger.adjustment(
                passId, request.delta(), balanceAfter, userId, request.reason()));

        return PassResponse.detailInfo.from(pass, LocalDate.now());
    }

    /**
     * ⚠️ 이용권은 매장을 직접 참조하지 않는다(passes → enrollments → merchants).
     * 그래서 원생을 거쳐 소속을 확인한다 — 이 단계를 건너뛰면 다른 매장 원장이
     * 우리 원생의 이용권을 환불할 수 있다.
     */
    private Pass findPassInMerchant(UUID merchantId, UUID passId) {
        Pass pass = passRepository.findById(passId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASS_NOT_FOUND));
        findEnrollmentInMerchant(merchantId, pass.getEnrollmentId());
        return pass;
    }

    private Enrollment findEnrollmentInMerchant(UUID merchantId, UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!enrollment.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        return enrollment;
    }
}
