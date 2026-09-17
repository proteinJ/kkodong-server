package com.kkodong.server.domain.media.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 사진에 찍힌 원생 태깅(PN-15, FR-PN15-01).
 *
 * <p>사진 한 장에 여러 강아지가 찍히고, 그 사진이 태깅된 원생의 보호자 앨범으로 배분된다.
 * 다대다이므로 별도 테이블이며, PK는 (media_id, enrollment_id) 복합키다 —
 * 같은 사진에 같은 원생을 두 번 태깅할 수 없다.
 *
 * <p>⚠️ <b>PC-29 동반 촬영 동의</b>: 단체 사진에 다른 강아지가 함께 찍히는 것이 기본이다.
 * 태깅된 원생의 보호자에게 배분하는 것과, 보호자가 그 사진을 커뮤니티로 내보내는 것(F-16)은
 * <b>다른 동의</b>다. 내보내기 시점에 촬영·공개 동의를 확인해야 하며, 그 판단 근거로
 * 서버가 "동의하지 않은 원생이 함께 찍혔는지"를 응답에 실어 준다
 * ({@code MediaResponse.detailInfo.sharingRestricted}).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "media_tags")
@IdClass(MediaTag.Pk.class)
public class MediaTag {

    @Id
    @Column(name = "media_id")
    private UUID mediaId;

    @Id
    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 복합 PK. JPA가 equals/hashCode를 요구하므로 record가 아닌 클래스로 둔다. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements Serializable {
        private UUID mediaId;
        private UUID enrollmentId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(mediaId, pk.mediaId)
                    && Objects.equals(enrollmentId, pk.enrollmentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mediaId, enrollmentId);
        }
    }
}
