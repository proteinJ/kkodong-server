package com.kkodong.server.domain.merchant.dto;

import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.global.util.Locations;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class MerchantResponse {

    /** 매장 상세(PN-04). 점주 본인이 보는 화면이라 미검증 상태·사업자번호까지 내려준다. */
    public record detailInfo(
            @Schema(description = "매장 ID") UUID id,
            @Schema(description = "업종") MerchantType merchantType,
            @Schema(description = "상호", example = "햇살 강아지유치원") String name,
            @Schema(description = "사업자등록번호") String businessRegistrationNumber,
            @Schema(description = "대표자명") String representativeName,
            @Schema(description = "개업일") LocalDate businessOpenedOn,
            @Schema(description = "주소") String address,
            @Schema(description = "위도") Double latitude,
            @Schema(description = "경도") Double longitude,
            @Schema(description = "연락처") String phone,
            @Schema(description = "매장 소개") String description,
            @Schema(description = "매장 사진 URL 목록") List<String> imageUrls,
            @Schema(description = "요일별 운영시간") List<BusinessHour> businessHours,
            @Schema(description = "임시 휴무일") List<String> closedDates,
            @Schema(description = "매장 상태") MerchantStatus status,
            @Schema(description = "사업자 진위확인 시각. null이면 미검증") OffsetDateTime verifiedAt
    ) {
        public static detailInfo from(Merchant m) {
            return new detailInfo(
                    m.getId(), m.getMerchantType(), m.getName(),
                    m.getBusinessRegistrationNumber(), m.getRepresentativeName(), m.getBusinessOpenedOn(),
                    m.getAddress(),
                    m.getLocation() == null ? null : Locations.latOf(m.getLocation()),
                    m.getLocation() == null ? null : Locations.lngOf(m.getLocation()),
                    m.getPhone(), m.getDescription(),
                    m.getImageUrls(), m.getBusinessHours(), m.getClosedDates(),
                    m.getStatus(), m.getVerifiedAt()
            );
        }
    }

    /**
     * 점주 앱 PN-01 로그인 직후의 "내가 속한 매장" 한 줄.
     *
     * <p>승인 대기(PENDING) 소속도 포함해 내려준다 — 선생님이 자기 신청이 어떤 상태인지
     * 알아야 하고, 그걸 감추면 "신청했는데 아무 일도 안 일어나는" 화면이 된다.
     */
    public record myMerchantItem(
            @Schema(description = "매장 ID") UUID merchantId,
            @Schema(description = "상호") String name,
            @Schema(description = "업종") MerchantType merchantType,
            @Schema(description = "매장 상태") MerchantStatus merchantStatus,
            @Schema(description = "내 역할") StaffRole role,
            @Schema(description = "내 소속 상태") StaffStatus staffStatus,
            @Schema(description = "내 권한 목록. 원장은 비어 있어도 전권이다") List<String> permissions
    ) {
        public static myMerchantItem of(MerchantStaff staff, Merchant merchant) {
            return new myMerchantItem(
                    staff.getMerchantId(),
                    merchant == null ? null : merchant.getName(),
                    merchant == null ? null : merchant.getMerchantType(),
                    merchant == null ? null : merchant.getStatus(),
                    staff.getRole(), staff.getStatus(), staff.getPermissions()
            );
        }
    }

    /** 유치원 수용 조건(PN-04). */
    public record kindergartenProfileInfo(
            @Schema(description = "수용 가능 체급") List<String> acceptedSizes,
            @Schema(description = "일일 정원. null이면 무제한") Integer dailyCapacity,
            @Schema(description = "중성화 필수 여부") Boolean requiresNeutered,
            @Schema(description = "요구 접종 항목") List<String> requiredVaccinations
    ) {
        public static kindergartenProfileInfo from(KindergartenProfile p) {
            return new kindergartenProfileInfo(
                    p.getAcceptedSizes(), p.getDailyCapacity(),
                    p.getRequiresNeutered(), p.getRequiredVaccinations());
        }
    }

    /** 이용권 상품(PN-04, KG-02 요금표). */
    public record productInfo(
            @Schema(description = "상품 ID") UUID id,
            @Schema(description = "상품명") String name,
            @Schema(description = "상품 유형") ProductType productType,
            @Schema(description = "총 회차. 기간권이면 null") Integer totalCount,
            @Schema(description = "유효일수. null이면 무기한") Integer validDays,
            @Schema(description = "가격(원)") Integer price,
            @Schema(description = "판매 중인지") Boolean isActive
    ) {
        public static productInfo from(MerchantProduct p) {
            return new productInfo(p.getId(), p.getName(), p.getProductType(),
                    p.getTotalCount(), p.getValidDays(), p.getPrice(), p.getIsActive());
        }
    }

    /**
     * 모집 QR / 링크(PN-05).
     *
     * <p>{@code code}는 그대로 QR에 실리는 값이다 — 클라이언트가 매장 ID를 조합해
     * 만들어 쓰지 않도록 서버가 완성된 코드를 내려준다.
     */
    public record inviteInfo(
            @Schema(description = "초대 ID") UUID id,
            @Schema(description = "QR·링크에 실을 코드") String code,
            @Schema(description = "만료 시각. null이면 무기한") OffsetDateTime expiresAt,
            @Schema(description = "폐기 시각. null이면 유효") OffsetDateTime revokedAt,
            @Schema(description = "지금 사용 가능한지") Boolean usable
    ) {
        public static inviteInfo from(MerchantInvite i) {
            return new inviteInfo(i.getId(), i.getCode(), i.getExpiresAt(), i.getRevokedAt(),
                    i.isUsableAt(OffsetDateTime.now()));
        }
    }

    /** 스태프 목록 항목(PN-18). */
    public record staffItem(
            @Schema(description = "스태프 ID") UUID id,
            @Schema(description = "유저 ID") UUID userId,
            @Schema(description = "닉네임") String displayName,
            @Schema(description = "역할") StaffRole role,
            @Schema(description = "소속 상태") StaffStatus status,
            @Schema(description = "권한 목록") List<String> permissions,
            @Schema(description = "승인 시각") OffsetDateTime approvedAt
    ) {
        /** @param displayName 유저가 탈퇴하면 null일 수 있다 — 방어적으로 허용한다. */
        public static staffItem of(MerchantStaff s, String displayName) {
            return new staffItem(s.getId(), s.getUserId(), displayName,
                    s.getRole(), s.getStatus(), s.getPermissions(), s.getApprovedAt());
        }
    }
}
