package com.kkodong.server.domain.media.repository;

import com.kkodong.server.domain.media.domain.MerchantMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MerchantMediaRepository extends JpaRepository<MerchantMedia, UUID> {

    /** PN-15 / KG-12 앨범 — 날짜별 그리드라 촬영일 역순이 기본이다. */
    List<MerchantMedia> findAllByMerchantIdOrderByTakenOnDescCreatedAtDesc(UUID merchantId);

    List<MerchantMedia> findAllByMerchantIdAndTakenOnOrderByCreatedAtDesc(
            UUID merchantId, LocalDate takenOn);
}
