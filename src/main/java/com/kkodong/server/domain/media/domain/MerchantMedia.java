package com.kkodong.server.domain.media.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 유치원 사진·영상(PN-15 업로드, KG-11/KG-12 보호자 열람).
 *
 * <p>⚠️ <b>PC-23 미디어 보관 비용</b>: 유치원 1곳이 하루 100장을 올리면 스토리지가 빠르게
 * 부푼다. CONCEPT.md 4절의 "트래픽 연동형 인프라로 고정비 0" 원칙과 정면으로 충돌하는
 * 유일한 기능이다. 그래서 용량·해상도를 행마다 기록해 둔다 — 정책(UD-C)이 정해지기 전이라도
 * 실제 증가 속도를 측정할 수 있어야 나중에 수치를 정할 근거가 생긴다.
 *
 * <p>사진 한 장에 여러 강아지가 찍히므로 배분은 {@link MediaTag}로 한다(다대다).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "merchant_media")
public class MerchantMedia {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "media_type", nullable = false)
    private MediaType mediaType; // MediaTypeConverter 자동 적용

    @Column(nullable = false)
    private String url;

    /**
     * 목록(KG-12 앨범 그리드)에서 쓸 축소본 URL.
     *
     * <p>⚠️ 서버가 만들지 않는다. 원본만 있으면 앨범 한 화면에 수십 장의 원본이 내려가
     * 보호자의 데이터를 태우고 R2 egress도 늘어난다. 클라이언트가 업로드 시 함께 올리거나,
     * 없으면 원본을 쓰되 표시 크기를 제한할 것 — 서버 리사이즈를 두지 않은 이유는
     * MediaStorageService 주석 참조.
     */
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    /** ⚠️ PC-23 측정값. 스토리지 증가 속도를 재는 유일한 근거라 반드시 채운다. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    private Integer width;
    private Integer height;

    /** 촬영일. KG-12 앨범이 날짜별 그리드라 정렬 축이 된다. 미지정이면 업로드일. */
    @Column(name = "taken_on")
    private LocalDate takenOn;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public void updateThumbnail(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
