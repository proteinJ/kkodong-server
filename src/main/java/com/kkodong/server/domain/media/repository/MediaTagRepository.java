package com.kkodong.server.domain.media.repository;

import com.kkodong.server.domain.media.domain.MediaTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaTagRepository extends JpaRepository<MediaTag, MediaTag.Pk> {

    List<MediaTag> findAllByMediaId(UUID mediaId);

    /** 여러 사진의 태깅을 한 번에 — 목록 조회에서 건별로 읽으면 N+1이 된다. */
    List<MediaTag> findAllByMediaIdIn(List<UUID> mediaIds);

    /** KG-11/KG-12 "내 강아지가 찍힌 사진만" — 보호자 앨범의 주 동선. */
    List<MediaTag> findAllByEnrollmentId(UUID enrollmentId);

    void deleteAllByMediaId(UUID mediaId);
}
