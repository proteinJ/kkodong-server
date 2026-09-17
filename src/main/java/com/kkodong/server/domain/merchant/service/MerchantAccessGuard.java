package com.kkodong.server.domain.merchant.service;

import com.kkodong.server.domain.merchant.domain.MerchantStaff;
import com.kkodong.server.domain.merchant.domain.StaffRole;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 점주 API의 권한 검증 단일 진입점.
 *
 * <p><b>왜 별도 컴포넌트인가</b>: PC-12에 따라 Supabase RLS를 쓰지 않으므로 "이 사람이
 * 이 매장에 손댈 수 있는가"를 매 요청마다 코드로 확인해야 한다. 이 검증이 서비스마다
 * 흩어지면 새 엔드포인트를 추가할 때 빠뜨리기 쉽고, 빠뜨려도 테스트가 통과한다 —
 * 남의 매장 데이터가 조용히 새는 종류의 사고다.
 *
 * <p>점주 API를 새로 만들 때는 반드시 이 클래스의 메서드로 시작할 것.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantAccessGuard {

    private final MerchantStaffRepository merchantStaffRepository;

    /** 권한 이름. 오타가 조용한 권한 누락이 되지 않도록 상수로 고정한다. */
    public static final String PERM_ATTENDANCE = "attendance";
    public static final String PERM_DAILY_NOTE = "daily_note";
    public static final String PERM_ENROLLMENT = "enrollment";
    public static final String PERM_PASS = "pass";

    /**
     * 이 유저가 해당 매장의 활성 스태프인지 확인하고 그 소속을 돌려준다.
     *
     * @throws BusinessException 소속이 아예 없으면 {@code NOT_MERCHANT_STAFF}(403).
     *         승인 대기·퇴사도 같은 코드로 막는다 — "승인 대기 중"임을 알려주는 것은
     *         자기 소속 목록(PN-01) 조회의 몫이지, 매장 데이터 접근의 응답이 아니다.
     */
    public MerchantStaff requireStaff(UUID merchantId, UUID userId) {
        MerchantStaff staff = merchantStaffRepository
                .findByMerchantIdAndUserId(merchantId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_MERCHANT_STAFF));

        // can()이 상태까지 보지만, 권한 없이 소속만 확인하는 경로에도 같은 방어가 필요하다.
        if (staff.getStatus() != com.kkodong.server.domain.merchant.domain.StaffStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_MERCHANT_STAFF);
        }
        return staff;
    }

    /**
     * 특정 권한이 필요한 작업. 원장은 permissions와 무관하게 통과한다.
     *
     * @throws BusinessException 권한이 없으면 {@code MERCHANT_PERMISSION_DENIED}(403)
     */
    public MerchantStaff requirePermission(UUID merchantId, UUID userId, String permission) {
        MerchantStaff staff = requireStaff(merchantId, userId);
        if (!staff.can(permission)) {
            throw new BusinessException(ErrorCode.MERCHANT_PERMISSION_DENIED);
        }
        return staff;
    }

    /**
     * 원장 전용 작업(매장 정보 수정·스태프 승인·상품 등록 등).
     *
     * @throws BusinessException 원장이 아니면 {@code MERCHANT_PERMISSION_DENIED}(403)
     */
    public MerchantStaff requireDirector(UUID merchantId, UUID userId) {
        MerchantStaff staff = requireStaff(merchantId, userId);
        if (staff.getRole() != StaffRole.DIRECTOR) {
            throw new BusinessException(ErrorCode.MERCHANT_PERMISSION_DENIED);
        }
        return staff;
    }
}
