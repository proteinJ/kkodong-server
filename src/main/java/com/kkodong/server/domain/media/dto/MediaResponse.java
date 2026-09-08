package com.kkodong.server.domain.media.dto;

import com.kkodong.server.domain.media.domain.MediaType;
import com.kkodong.server.domain.media.domain.MerchantMedia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class MediaResponse {

    /** 미디어 한 건(PN-15, KG-12). */
    public record detailInfo(
            @Schema(description = "미디어 ID") UUID id,
            @Schema(description = "종류") MediaType mediaType,
            @Schema(description = "원본 URL") String url,
            @Schema(description = "썸네일 URL. 클라이언트가 함께 올리지 않으면 null") String thumbnailUrl,
            @Schema(description = "용량(bytes)") Long sizeBytes,
            @Schema(description = "가로") Integer width,
            @Schema(description = "세로") Integer height,
            @Schema(description = "촬영일") LocalDate takenOn,
            @Schema(description = "업로드 시각") OffsetDateTime createdAt,

            @Schema(description = "이 사진에 태깅된 원생") List<taggedItem> tagged,

            @Schema(description = """
                    ⚠️ 커뮤니티 공유가 제한되는지(PC-29). 태깅된 원생 중 촬영·공개 동의가
                    확인되지 않은 보호자가 있으면 true.

                    보호자 본인 앨범으로 배분하는 것과, 보호자가 이 사진을 커뮤니티로 내보내는 것
                    (F-16)은 다른 동의입니다. 이 값이 true면 내보내기 버튼을 막아야 합니다 —
                    단체 사진에는 다른 강아지가 함께 찍히는 것이 기본이기 때문입니다.""")
            Boolean sharingRestricted
    ) {
        /** 태깅된 원생 한 명. */
        public record taggedItem(
                @Schema(description = "원생 ID") UUID enrollmentId,
                @Schema(description = "강아지 이름") String dogName,
                @Schema(description = "이 보호자가 촬영·공개에 동의했는지. 확인 불가면 false")
                Boolean photoPublicConsented
        ) {}

        public static detailInfo of(MerchantMedia m, List<taggedItem> tagged) {
            // 한 명이라도 동의가 확인되지 않으면 제한한다 — 확인 불가를 동의로 해석하면
            // 동의 없이 공유되는 사고가 난다.
            boolean restricted = tagged.stream()
                    .anyMatch(t -> !Boolean.TRUE.equals(t.photoPublicConsented()));
            return new detailInfo(
                    m.getId(), m.getMediaType(), m.getUrl(), m.getThumbnailUrl(),
                    m.getSizeBytes(), m.getWidth(), m.getHeight(),
                    m.getTakenOn(), m.getCreatedAt(), tagged, restricted);
        }
    }

    /**
     * 업로드 결과.
     *
     * <p>부분 성공을 허용한다 — 20장을 올리는데 한 장이 용량을 넘었다고 19장을 되돌리면
     * 점주는 어느 것이 문제인지 모른 채 전부 다시 올려야 한다.
     */
    public record uploadResult(
            @Schema(description = "업로드된 미디어") List<detailInfo> uploaded,
            @Schema(description = "실패한 파일과 사유") List<failure> failed,
            @Schema(description = "이번 업로드로 저장된 총 용량(bytes). ⚠️ PC-23 — 스토리지 증가를 눈으로 보라고 준다")
            long totalBytes
    ) {
        public record failure(
                @Schema(description = "원본 파일명") String fileName,
                @Schema(description = "에러 코드", example = "MD002") String code,
                @Schema(description = "사유") String message
        ) {}
    }
}
