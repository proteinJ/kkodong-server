package com.kkodong.server.global.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * 유치원 미디어 업로드 정책(PN-15, FR-PN15-02).
 *
 * <p><b>왜 설정값인가</b>: 수치가 아직 정해지지 않았다(UD-C — 보관 기간·해상도·건당 용량
 * 상한). PC-23이 지적하듯 유치원 1곳이 하루 100장을 올리면 스토리지가 빠르게 부풀고,
 * 이는 CONCEPT.md 4절의 "트래픽 연동형 인프라로 고정비 0" 원칙과 정면으로 충돌하는
 * 유일한 기능이다. 실사용 데이터를 보고 조정해야 하므로 재빌드 없이 바꿀 수 있어야 한다.
 *
 * <p>여기 값은 <b>잠정치</b>다. 실제 증가 속도는 merchant_media에 기록되는
 * size_bytes/width/height로 측정한다.
 *
 * @param maxImageBytes  이미지 건당 용량 상한
 * @param maxVideoBytes  영상 건당 용량 상한. ⚠️ spring.servlet.multipart.max-file-size 를
 *                       넘으면 그쪽에서 먼저 잘린다 — 영상을 실제로 받으려면 함께 올릴 것
 * @param maxImagePixels 이미지 해상도 상한(가로 × 세로). 초과하면 거부한다
 * @param maxPerUpload   한 번에 올릴 수 있는 파일 수
 * @param imageTypes     허용 이미지 MIME 타입 → 확장자
 * @param videoTypes     허용 영상 MIME 타입 → 확장자
 */
@ConfigurationProperties(prefix = "kkodong.media")
@Validated
public record MediaProperties(
        @Positive long maxImageBytes,
        @Positive long maxVideoBytes,
        @Positive long maxImagePixels,
        @Positive int maxPerUpload,
        @NotEmpty java.util.Map<String, String> imageTypes,
        @NotEmpty java.util.Map<String, String> videoTypes
) {

    public boolean isImage(String contentType) {
        return contentType != null && imageTypes.containsKey(contentType);
    }

    public boolean isVideo(String contentType) {
        return contentType != null && videoTypes.containsKey(contentType);
    }

    /** 저장 시 붙일 확장자. 허용되지 않는 타입이면 null. */
    public String extensionOf(String contentType) {
        if (contentType == null) return null;
        String ext = imageTypes.get(contentType);
        return ext != null ? ext : videoTypes.get(contentType);
    }

    public Set<String> allowedContentTypes() {
        var all = new java.util.HashSet<>(imageTypes.keySet());
        all.addAll(videoTypes.keySet());
        return all;
    }
}
