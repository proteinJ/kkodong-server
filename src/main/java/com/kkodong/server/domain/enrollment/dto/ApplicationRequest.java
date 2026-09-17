package com.kkodong.server.domain.enrollment.dto;

import com.kkodong.server.domain.enrollment.domain.ApplicationForm;
import com.kkodong.server.domain.enrollment.domain.ConsentDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ApplicationRequest {

    /**
     * 신청서 양식 등록(PN-06, FR-PN06-01).
     *
     * <p>수정이 아니라 <b>새 버전 발행</b>이다. 기존 활성 양식은 자동으로 내려간다 —
     * 양식을 고치면 과거 제출값이 어떤 질문에 대한 답인지 알 수 없게 되기 때문이다.
     */
    public record publishForm(
            @Valid
            @Size(max = 30, message = "추가 질문은 30개를 넘을 수 없습니다.")
            @Schema(description = "추가 질문 목록. 견주 앱(KG-06)이 이대로 렌더링한다")
            List<ApplicationForm.ExtraField> extraFields
    ) {}

    /** 서약서 등록(PN-06). 마찬가지로 새 버전 발행이다. */
    public record publishConsent(
            @NotBlank(message = "서약서 전문은 필수입니다.")
            @Schema(description = "서약서 전문", requiredMode = Schema.RequiredMode.REQUIRED)
            String body,

            @NotNull(message = "동의 항목은 필수입니다.")
            @Size(min = 1, max = 20, message = "동의 항목은 1~20개여야 합니다.")
            @Schema(description = """
                    항목별 동의 목록. ⚠️ 사고 책임·촬영 공개·마케팅을 한 덩어리로 묶지 말 것 —
                    항목이 분리되어야 사진을 커뮤니티로 내보낼 수 있는지 판단할 수 있다(PC-29).""",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            List<ConsentDocument.ConsentItem> items
    ) {}

    /** 신청 거절(PN-07, FR-PN07-01). 사유는 견주에게 그대로 전달된다. */
    public record reject(
            @NotBlank(message = "거절 사유는 필수입니다.")
            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "거절 사유", example = "현재 소형견만 수용 가능합니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String reason
    ) {}
}
