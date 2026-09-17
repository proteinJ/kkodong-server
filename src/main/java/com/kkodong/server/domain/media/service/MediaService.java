package com.kkodong.server.domain.media.service;

import com.kkodong.server.domain.enrollment.domain.ConsentDocument;
import com.kkodong.server.domain.enrollment.domain.Enrollment;
import com.kkodong.server.domain.enrollment.domain.EnrollmentApplication;
import com.kkodong.server.domain.enrollment.repository.EnrollmentApplicationRepository;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.media.domain.MediaTag;
import com.kkodong.server.domain.media.domain.MediaType;
import com.kkodong.server.domain.media.domain.MerchantMedia;
import com.kkodong.server.domain.media.dto.MediaResponse;
import com.kkodong.server.domain.media.repository.MediaTagRepository;
import com.kkodong.server.domain.media.repository.MerchantMediaRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.global.config.MediaProperties;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 유치원 사진·영상 업로드와 원생 태깅(PN-15).
 *
 * <p>⚠️ <b>PC-23</b>: 미디어 보관 비용이 "고정비 0" 원칙과 충돌하는 유일한 기능이다.
 * 업로드 응답에 저장된 총 용량을 함께 돌려주는 것도 그래서다 — 증가가 눈에 보여야 한다.
 *
 * <p>⚠️ <b>PC-29</b>: 태깅된 원생 중 촬영·공개 동의가 확인되지 않은 보호자가 있으면
 * {@code sharingRestricted}로 표시한다. 보호자 앨범 배분과 커뮤니티 내보내기는 다른 동의다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaService {

    private final MerchantMediaRepository mediaRepository;
    private final MediaTagRepository tagRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentApplicationRepository applicationRepository;
    private final MediaStorageService storageService;
    private final MediaProperties mediaProperties;
    private final MerchantAccessGuard accessGuard;

    /**
     * 다중 업로드 + 태깅(PN-15, FR-PN15-01).
     *
     * <p>업로드한 모든 파일에 같은 원생 목록이 태깅된다 — 활동 사진은 보통 같은 아이들을
     * 연속으로 찍으므로 이쪽이 실제 사용에 맞는다. 개별 조정은 태깅 수정으로 한다.
     *
     * <p>부분 성공을 허용한다. 한 장이 용량을 넘었다고 나머지를 되돌리면 점주는 어느 것이
     * 문제인지 모른 채 전부 다시 올려야 한다.
     */
    @Transactional
    public MediaResponse.uploadResult upload(
            UUID merchantId, UUID userId, List<MultipartFile> files,
            LocalDate takenOn, List<UUID> enrollmentIds) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_MEDIA_FILE);
        }
        if (files.size() > mediaProperties.maxPerUpload()) {
            throw new BusinessException(ErrorCode.TOO_MANY_MEDIA_FILES);
        }

        List<UUID> targets = validTargets(merchantId, enrollmentIds);
        LocalDate date = takenOn == null ? LocalDate.now() : takenOn;

        List<MerchantMedia> saved = new ArrayList<>();
        List<MediaResponse.uploadResult.failure> failed = new ArrayList<>();
        long totalBytes = 0;

        for (MultipartFile file : files) {
            try {
                var stored = storageService.upload(file, "merchants/" + merchantId + "/media");

                MerchantMedia media = mediaRepository.save(MerchantMedia.builder()
                        .merchantId(merchantId)
                        .mediaType(stored.image() ? MediaType.IMAGE : MediaType.VIDEO)
                        .url(stored.url())
                        .sizeBytes(stored.sizeBytes())
                        .width(stored.width())
                        .height(stored.height())
                        .takenOn(date)
                        .uploadedByUserId(userId)
                        .build());

                targets.forEach(enrollmentId -> tagRepository.save(MediaTag.builder()
                        .mediaId(media.getId()).enrollmentId(enrollmentId).build()));

                saved.add(media);
                totalBytes += stored.sizeBytes();
            } catch (BusinessException e) {
                failed.add(new MediaResponse.uploadResult.failure(
                        file.getOriginalFilename(), e.getErrorCode().getCode(), e.getMessage()));
            }
        }

        Map<UUID, MediaResponse.detailInfo.taggedItem> tagInfo = taggedItems(targets);
        List<MediaResponse.detailInfo> uploaded = saved.stream()
                .map(m -> MediaResponse.detailInfo.of(m,
                        targets.stream().map(tagInfo::get).filter(Objects::nonNull).toList()))
                .toList();

        return new MediaResponse.uploadResult(uploaded, failed, totalBytes);
    }

    /**
     * 태깅 수정(FR-PN15-01). 전달된 목록으로 통째로 교체한다.
     *
     * <p>태깅 화면은 체크박스라 클라이언트가 항상 최종 상태를 보낸다. 델타로 받으면
     * "태그 해제"를 표현할 방법이 따로 필요해진다.
     */
    @Transactional
    public MediaResponse.detailInfo retag(
            UUID merchantId, UUID userId, UUID mediaId, List<UUID> enrollmentIds) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        MerchantMedia media = findInMerchant(merchantId, mediaId);
        List<UUID> targets = validTargets(merchantId, enrollmentIds);

        tagRepository.deleteAllByMediaId(mediaId);
        tagRepository.flush(); // 지우기를 먼저 반영해야 복합 PK 중복으로 걸리지 않는다
        targets.forEach(enrollmentId -> tagRepository.save(MediaTag.builder()
                .mediaId(mediaId).enrollmentId(enrollmentId).build()));

        Map<UUID, MediaResponse.detailInfo.taggedItem> tagInfo = taggedItems(targets);
        return MediaResponse.detailInfo.of(media,
                targets.stream().map(tagInfo::get).filter(Objects::nonNull).toList());
    }

    /** 매장 앨범(PN-15, KG-12). 날짜를 주면 그날 것만. */
    public List<MediaResponse.detailInfo> getAlbum(UUID merchantId, UUID userId, LocalDate date) {
        accessGuard.requireStaff(merchantId, userId);

        List<MerchantMedia> media = date == null
                ? mediaRepository.findAllByMerchantIdOrderByTakenOnDescCreatedAtDesc(merchantId)
                : mediaRepository.findAllByMerchantIdAndTakenOnOrderByCreatedAtDesc(merchantId, date);
        return withTags(media);
    }

    /** 원생별 사진(PN-11 상세, KG-11/KG-12 보호자 앨범의 원본). */
    public List<MediaResponse.detailInfo> getByEnrollment(
            UUID merchantId, UUID userId, UUID enrollmentId) {
        accessGuard.requireStaff(merchantId, userId);

        List<UUID> mediaIds = tagRepository.findAllByEnrollmentId(enrollmentId).stream()
                .map(MediaTag::getMediaId).toList();
        if (mediaIds.isEmpty()) return List.of();

        // ⚠️ 남의 매장 사진이 섞이지 않게 소속을 대조한다.
        List<MerchantMedia> media = mediaRepository.findAllById(mediaIds).stream()
                .filter(m -> m.getMerchantId().equals(merchantId))
                .sorted(Comparator.comparing(MerchantMedia::getTakenOn,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return withTags(media);
    }

    /**
     * 삭제(PN-15).
     *
     * <p>⚠️ R2의 실제 객체는 지우지 않는다. 보호자 앨범에 이미 내려간 URL이 깨지는 것과,
     * 잘못 지웠을 때 되살릴 수 없는 것 중 후자가 더 나쁘다. 저장소 정리는 보관 정책(UD-C)이
     * 정해진 뒤 일괄 작업으로 한다 — 그때 이 행의 url을 근거로 쓴다.
     */
    @Transactional
    public void delete(UUID merchantId, UUID userId, UUID mediaId) {
        accessGuard.requirePermission(merchantId, userId, MerchantAccessGuard.PERM_DAILY_NOTE);

        MerchantMedia media = findInMerchant(merchantId, mediaId);
        tagRepository.deleteAllByMediaId(mediaId);
        mediaRepository.delete(media);
    }

    // ---------- 내부 ----------

    /** 태깅을 한 번에 붙인다 — 건별로 읽으면 사진 수만큼 쿼리가 늘어난다. */
    private List<MediaResponse.detailInfo> withTags(List<MerchantMedia> media) {
        if (media.isEmpty()) return List.of();

        List<UUID> mediaIds = media.stream().map(MerchantMedia::getId).toList();
        Map<UUID, List<UUID>> tagsByMedia = tagRepository.findAllByMediaIdIn(mediaIds).stream()
                .collect(Collectors.groupingBy(MediaTag::getMediaId,
                        Collectors.mapping(MediaTag::getEnrollmentId, Collectors.toList())));

        Map<UUID, MediaResponse.detailInfo.taggedItem> tagInfo = taggedItems(
                tagsByMedia.values().stream().flatMap(List::stream).distinct().toList());

        return media.stream()
                .map(m -> MediaResponse.detailInfo.of(m,
                        tagsByMedia.getOrDefault(m.getId(), List.of()).stream()
                                .map(tagInfo::get).filter(Objects::nonNull).toList()))
                .toList();
    }

    /**
     * 태깅 대상의 이름과 촬영·공개 동의 여부를 모은다(PC-29).
     *
     * <p>동의는 승인된 신청서(enrollment_applications.consented_items)에 남아 있다.
     * ⚠️ 확인할 수 없으면 <b>동의하지 않은 것으로</b> 본다 — 확인 불가를 동의로 해석하면
     * 동의 없이 공유되는 사고가 난다.
     */
    private Map<UUID, MediaResponse.detailInfo.taggedItem> taggedItems(List<UUID> enrollmentIds) {
        if (enrollmentIds.isEmpty()) return Map.of();

        Map<UUID, Enrollment> enrollments = enrollmentRepository.findAllById(enrollmentIds).stream()
                .collect(Collectors.toMap(Enrollment::getId, Function.identity()));

        List<UUID> applicationIds = enrollments.values().stream()
                .map(Enrollment::getApplicationId).filter(Objects::nonNull).toList();
        Map<UUID, EnrollmentApplication> applications = applicationIds.isEmpty() ? Map.of()
                : applicationRepository.findAllById(applicationIds).stream()
                        .collect(Collectors.toMap(EnrollmentApplication::getId, Function.identity()));

        Map<UUID, MediaResponse.detailInfo.taggedItem> result = new LinkedHashMap<>();
        for (UUID id : enrollmentIds) {
            Enrollment enrollment = enrollments.get(id);
            if (enrollment == null) continue;

            EnrollmentApplication application = enrollment.getApplicationId() == null ? null
                    : applications.get(enrollment.getApplicationId());
            boolean consented = application != null
                    && application.hasConsented(ConsentDocument.ITEM_PHOTO_PUBLIC);

            result.put(id, new MediaResponse.detailInfo.taggedItem(
                    id, enrollment.getDogNameSnapshot(), consented));
        }
        return result;
    }

    /** 요청된 원생 중 이 매장 소속만 남긴다. 남의 매장 원생을 태깅할 수 없다. */
    private List<UUID> validTargets(UUID merchantId, List<UUID> enrollmentIds) {
        if (enrollmentIds == null || enrollmentIds.isEmpty()) return List.of();
        return enrollmentRepository.findAllById(enrollmentIds).stream()
                .filter(e -> e.getMerchantId().equals(merchantId))
                .map(Enrollment::getId)
                .toList();
    }

    private MerchantMedia findInMerchant(UUID merchantId, UUID mediaId) {
        MerchantMedia media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));
        if (!media.getMerchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return media;
    }
}
