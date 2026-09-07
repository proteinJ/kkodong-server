package com.kkodong.server.domain.merchant.service;

import com.kkodong.server.domain.merchant.domain.MerchantStaff;
import com.kkodong.server.domain.merchant.domain.StaffRole;
import com.kkodong.server.domain.merchant.domain.StaffStatus;
import com.kkodong.server.domain.merchant.dto.MerchantRequest;
import com.kkodong.server.domain.merchant.dto.MerchantResponse;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
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
 * 선생님 소속 신청과 스태프 관리(PN-03, PN-18).
 *
 * <p>흐름 F-18: 선생님이 유치원을 검색해 소속 신청 → 원장이 승인하며 권한 부여 →
 * 권한 범위 내 화면만 노출.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantStaffService {

    private final MerchantStaffRepository merchantStaffRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * 선생님 소속 신청(PN-03). 승인 대기 상태로 만들어진다.
     *
     * <p>권한은 신청 시점에 정하지 않는다 — 원장이 승인하며 부여한다(FR-PN18-01).
     * 신청자가 자기 권한을 고르게 두면 권한 부여의 의미가 사라진다.
     */
    @Transactional
    public MerchantResponse.staffItem apply(UUID userId, MerchantRequest.joinStaff request) {
        if (!merchantRepository.existsById(request.merchantId())) {
            throw new BusinessException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        // 재신청·중복 신청 모두 여기서 막힌다. DB의 uq_merchant_staff와 같은 규칙을
        // 서버에서 먼저 봐서 500 대신 409를 준다.
        if (merchantStaffRepository.findByMerchantIdAndUserId(request.merchantId(), userId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_MERCHANT_STAFF);
        }

        MerchantStaff staff = merchantStaffRepository.save(MerchantStaff.builder()
                .merchantId(request.merchantId())
                .userId(userId)
                .role(StaffRole.STAFF)
                .status(StaffStatus.PENDING)
                .build());

        return MerchantResponse.staffItem.of(staff, null);
    }

    /** 스태프 목록(PN-18). 상태로 걸러 승인 대기 배지에도 쓴다. */
    public List<MerchantResponse.staffItem> getStaff(UUID merchantId, UUID userId, StaffStatus status) {
        accessGuard.requireStaff(merchantId, userId);

        List<MerchantStaff> staff = status == null
                ? merchantStaffRepository.findAllByMerchantIdAndStatus(merchantId, StaffStatus.ACTIVE)
                : merchantStaffRepository.findAllByMerchantIdAndStatus(merchantId, status);
        if (staff.isEmpty()) return List.of();

        // 유저를 건별로 조회하면 N+1이 되므로 한 번에 읽는다(BlockService와 같은 방식).
        Map<UUID, User> users = userRepository
                .findAllById(staff.stream().map(MerchantStaff::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return staff.stream()
                .map(s -> {
                    User u = users.get(s.getUserId());
                    return MerchantResponse.staffItem.of(s, u == null ? null : u.getDisplayName());
                })
                .toList();
    }

    /** 스태프 승인 및 권한 부여(PN-18, FR-PN18-01). 원장 전용. */
    @Transactional
    public MerchantResponse.staffItem approve(
            UUID merchantId, UUID userId, UUID staffId, MerchantRequest.approveStaff request) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantStaff staff = findStaffInMerchant(merchantId, staffId);
        staff.approve(userId);
        if (request.permissions() != null) {
            staff.grantPermissions(request.permissions());
        }
        return MerchantResponse.staffItem.of(staff, null);
    }

    /** 권한 변경(PN-18). 원장 전용. 승인 이후에도 권한만 따로 조정한다. */
    @Transactional
    public MerchantResponse.staffItem updatePermissions(
            UUID merchantId, UUID userId, UUID staffId, MerchantRequest.approveStaff request) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantStaff staff = findStaffInMerchant(merchantId, staffId);
        staff.grantPermissions(request.permissions() == null ? List.of() : request.permissions());
        return MerchantResponse.staffItem.of(staff, null);
    }

    /**
     * 퇴사 처리(PN-18, FR-PN18-02). 원장 전용. 권한만 회수하고 작성 이력은 보존한다.
     *
     * <p>⚠️ 마지막 원장은 내보낼 수 없다. 내보내면 매장을 관리할 사람이 사라져
     * 손으로 DB를 고쳐야 하는 상태가 된다. 행 단위 제약으로 표현할 수 없어 여기서 센다.
     */
    @Transactional
    public void resign(UUID merchantId, UUID userId, UUID staffId) {
        accessGuard.requireDirector(merchantId, userId);

        MerchantStaff staff = findStaffInMerchant(merchantId, staffId);
        if (staff.getRole() == StaffRole.DIRECTOR
                && merchantStaffRepository.countByMerchantIdAndRoleAndStatus(
                        merchantId, StaffRole.DIRECTOR, StaffStatus.ACTIVE) <= 1) {
            throw new BusinessException(ErrorCode.LAST_DIRECTOR_CANNOT_RESIGN);
        }
        staff.resign();
    }

    /**
     * ⚠️ 남의 매장 스태프 ID를 넣어도 통과하지 않게 소속을 대조한다.
     * staffId만으로 찾으면 다른 매장 원장이 우리 선생님을 퇴사시킬 수 있다.
     */
    private MerchantStaff findStaffInMerchant(UUID merchantId, UUID staffId) {
        MerchantStaff staff = merchantStaffRepository.findById(staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STAFF_NOT_FOUND));
        if (!staff.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.STAFF_NOT_FOUND);
        }
        return staff;
    }
}
