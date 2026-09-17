package com.kkodong.server.domain.merchant.dto;

import com.kkodong.server.domain.merchant.domain.BusinessHour;
import com.kkodong.server.domain.merchant.domain.MerchantType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public class MerchantRequest {

    /**
     * 매장 개설(PN-02). 사업자 진위확인을 통과해야만 생성된다(FR-PN02-01).
     *
     * <p>⚠️ 개설자를 요청 본문으로 받지 않는다 — JWT에서 꺼낸 유저가 곧 원장이 된다
     * (PC-14와 같은 원칙: 소유자 식별자를 클라이언트가 보내게 하면 위변조가 가능하다).
     */
    public record create(
            @NotBlank(message = "상호는 필수입니다.")
            @Schema(description = "상호", example = "햇살 강아지유치원", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @NotBlank(message = "사업자등록번호는 필수입니다.")
            @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 하이픈 없는 10자리 숫자입니다.")
            @Schema(description = "사업자등록번호(하이픈 없는 10자리)", example = "1234567890",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String businessRegistrationNumber,

            @NotBlank(message = "대표자명은 필수입니다.")
            @Schema(description = "대표자명(진위확인 대조값)", example = "김원장",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String representativeName,

            @Schema(description = "개업일(진위확인 대조값)", example = "2024-03-02")
            LocalDate businessOpenedOn,

            @Schema(description = "업종. 생략하면 kindergarten", example = "KINDERGARTEN")
            MerchantType merchantType
    ) {}

    /**
     * 매장 정보 수정(PN-04). 모든 필드가 선택이며 null은 "안 바꿈"을 뜻한다.
     *
     * <p>⚠️ 사업자등록번호·대표자명·업종은 여기 없다. 진위확인을 통과한 값이라
     * 일반 수정으로 바뀌어서는 안 된다.
     */
    public record update(
            @Schema(description = "상호") String name,
            @Schema(description = "주소") String address,
            @Schema(description = "위도", example = "35.1796") Double latitude,
            @Schema(description = "경도", example = "129.0756") Double longitude,
            @Schema(description = "연락처", example = "051-123-4567") String phone,
            @Schema(description = "매장 소개") String description,
            @Schema(description = "매장 사진 URL 목록") List<String> imageUrls,
            @Schema(description = "요일별 운영시간") List<BusinessHour> businessHours,
            @Schema(description = "임시 휴무일(ISO 날짜)", example = "[\"2026-09-30\"]") List<String> closedDates
    ) {
        /**
         * 위경도는 한 쌍으로만 의미가 있다. 한쪽만 온 요청은 좌표를 옮기려다 만 것이므로
         * 조용히 무시하지 않고 검증에서 걸러낸다.
         */
        @AssertTrue(message = "위도와 경도는 함께 보내야 합니다.")
        public boolean isCoordinatePaired() {
            return (latitude == null) == (longitude == null);
        }
    }

    /** 유치원 수용 조건 설정(PN-04, FR-PN04-02). 견주 앱 KG-02 적합도 안내의 근거가 된다. */
    public record kindergartenProfile(
            @Schema(description = "수용 가능 체급(small/medium/large)", example = "[\"small\",\"medium\"]")
            List<String> acceptedSizes,

            @Positive(message = "일일 정원은 1 이상이어야 합니다.")
            @Schema(description = "일일 정원. 생략하면 무제한", example = "20")
            Integer dailyCapacity,

            @Schema(description = "중성화 필수 여부") Boolean requiresNeutered,
            @Schema(description = "요구 접종 항목") List<String> requiredVaccinations
    ) {}

    /** 이용권 상품 등록(PN-04, FR-PN04-01). */
    public record createProduct(
            @NotBlank(message = "상품명은 필수입니다.")
            @Schema(description = "상품명", example = "주 3회 10회권", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @NotNull(message = "상품 유형은 필수입니다.")
            @Schema(description = "COUNT(횟수권) 또는 PERIOD(기간권)", example = "COUNT",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            com.kkodong.server.domain.merchant.domain.ProductType productType,

            @Positive(message = "총 회차는 1 이상이어야 합니다.")
            @Schema(description = "횟수권의 총 회차. 기간권이면 생략", example = "10")
            Integer totalCount,

            @Positive(message = "유효일수는 1 이상이어야 합니다.")
            @Schema(description = "발급일로부터의 유효일수. 생략하면 무기한", example = "90")
            Integer validDays,

            @NotNull(message = "가격은 필수입니다.")
            @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
            @Schema(description = "가격(원)", example = "300000", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer price
    ) {}

    /** 원생 모집 QR 발급(PN-05, FR-PN05-01). */
    public record createInvite(
            @Positive(message = "유효일수는 1 이상이어야 합니다.")
            @Schema(description = "유효일수. 생략하면 무기한", example = "30")
            Integer validDays
    ) {}

    /** 선생님 소속 신청(PN-03). 유치원을 검색해 고른 뒤 보낸다. */
    public record joinStaff(
            @NotNull(message = "매장 ID는 필수입니다.")
            @Schema(description = "소속 신청할 매장 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            java.util.UUID merchantId
    ) {}

    /** 스태프 승인 및 권한 부여(PN-18, FR-PN18-01). */
    public record approveStaff(
            @Schema(description = "부여할 권한. attendance·daily_note·enrollment·pass",
                    example = "[\"attendance\",\"daily_note\"]")
            List<String> permissions
    ) {}
}
