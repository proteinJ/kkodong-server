package com.kkodong.server.domain.enrollment.dto;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.enrollment.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ApplicationResponse {

    /** 신청서 양식(PN-06). 견주 앱(KG-06)이 이 정의대로 화면을 그린다. */
    public record formInfo(
            @Schema(description = "양식 ID") UUID id,
            @Schema(description = "버전") Integer version,
            @Schema(description = "추가 질문 목록") List<ApplicationForm.ExtraField> extraFields,
            @Schema(description = "현재 활성 양식인지") Boolean isActive,
            @Schema(description = "발행 시각") OffsetDateTime createdAt
    ) {
        public static formInfo from(ApplicationForm f) {
            return new formInfo(f.getId(), f.getVersion(), f.getExtraFields(),
                    f.getIsActive(), f.getCreatedAt());
        }
    }

    /** 서약서(PN-06, KG-07). */
    public record consentInfo(
            @Schema(description = "서약서 ID") UUID id,
            @Schema(description = "버전") Integer version,
            @Schema(description = "전문") String body,
            @Schema(description = "동의 항목") List<ConsentDocument.ConsentItem> items,
            @Schema(description = "현재 활성 서약서인지") Boolean isActive,
            @Schema(description = "발행 시각") OffsetDateTime createdAt
    ) {
        public static consentInfo from(ConsentDocument c) {
            return new consentInfo(c.getId(), c.getVersion(), c.getBody(), c.getItems(),
                    c.getIsActive(), c.getCreatedAt());
        }
    }

    /**
     * 접수함 항목(PN-07).
     *
     * <p>반려견 정보를 함께 실어 내린다 — {@code FR-PN11-01}이 말한 "점주가 꼬동을 쓸
     * 이유 그 자체"가 이것이다. 똑독은 유치원마다 새로 받아야 하는 정보를,
     * 꼬동은 이미 등록된 프로필에서 그대로 가져온다.
     */
    public record item(
            @Schema(description = "신청 ID") UUID id,
            @Schema(description = "반려견 ID") UUID dogId,
            @Schema(description = "신청자(견주) 유저 ID") UUID applicantUserId,
            @Schema(description = "상태") ApplicationStatus status,
            @Schema(description = "QR/링크 유입이면 초대 ID. 직접 검색이면 null") UUID inviteId,

            @Schema(description = "추가 질문 답변") Map<String, Object> submittedValues,
            @Schema(description = "항목별 동의 결과") Map<String, Boolean> consentedItems,
            @Schema(description = "서명 이미지 URL") String signatureImageUrl,
            @Schema(description = "동의 시각") OffsetDateTime consentedAt,

            @Schema(description = "거절 사유") String rejectReason,
            @Schema(description = "처리 시각") OffsetDateTime reviewedAt,
            @Schema(description = "신청 시각") OffsetDateTime createdAt,

            @Schema(description = "꼬동 프로필에서 가져온 반려견 정보. 강아지가 삭제됐으면 null")
            dogSnapshot dog
    ) {
        /** 신청 검토에 필요한 반려견 정보(FR-PN11-01). 수용 조건 대조에 쓴다. */
        public record dogSnapshot(
                @Schema(description = "이름") String name,
                @Schema(description = "견종") String breed,
                @Schema(description = "생년월일") LocalDate birthDate,
                @Schema(description = "체급") String size,
                @Schema(description = "체중(kg)") java.math.BigDecimal weightKg,
                @Schema(description = "중성화 여부") Boolean neutered,
                @Schema(description = "프로필 사진 URL") String profileImageUrl
        ) {
            public static dogSnapshot from(Dog d) {
                return new dogSnapshot(
                        d.getName(), d.getBreed(), d.getBirthDate(),
                        d.getSize() == null ? null : d.getSize().name().toLowerCase(),
                        d.getWeightKg(), d.getNeutered(), d.getProfileImageUrl());
            }
        }

        /** @param dog 강아지가 삭제(견주 탈퇴)됐을 수 있어 null을 허용한다. */
        public static item of(EnrollmentApplication a, Dog dog) {
            return new item(
                    a.getId(), a.getDogId(), a.getApplicantUserId(), a.getStatus(), a.getInviteId(),
                    a.getSubmittedValues(), a.getConsentedItems(),
                    a.getSignatureImageUrl(), a.getConsentedAt(),
                    a.getRejectReason(), a.getReviewedAt(), a.getCreatedAt(),
                    dog == null ? null : dogSnapshot.from(dog));
        }
    }

    /**
     * 승인 결과(PN-07). 승인은 원생을 만드는 행위이므로 만들어진 원생을 함께 돌려준다 —
     * 클라이언트가 곧바로 이용권 발급(PN-12)으로 넘어갈 수 있어야 한다(흐름 F-13 3단계).
     */
    public record approveResult(
            @Schema(description = "승인된 신청") item application,
            @Schema(description = "새로 생성된 원생 ID") UUID enrollmentId
    ) {}
}
