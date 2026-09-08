package com.kkodong.server.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReviewRequest {

    /**
     * 답글 작성·수정(PN-20).
     *
     * <p>점주가 고칠 수 있는 것은 답글뿐이다. 리뷰 본문은 견주가 쓴 것이라 손댈 수 없다.
     */
    public record reply(
            @NotBlank(message = "답글 내용은 필수입니다.")
            @Size(max = 2000, message = "답글은 2000자를 넘을 수 없습니다.")
            @Schema(description = "답글 내용. 견주에게 공개된다",
                    example = "소중한 후기 감사합니다. 앞으로도 최선을 다하겠습니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String body
    ) {}
}
