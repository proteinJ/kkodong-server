package com.kkodong.server.domain.media;

import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.repository.EnrollmentApplicationRepository;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.media.dto.MediaResponse;
import com.kkodong.server.domain.media.repository.MediaTagRepository;
import com.kkodong.server.domain.media.service.MediaService;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.storage.MediaStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 사진 태깅과 <b>촬영·공개 동의 판정</b>을 검증한다(PN-15, FR-PN15-01, PC-29).
 *
 * <p><b>PC-29가 이 기능의 핵심 제약이다</b>: 유치원 단체 사진에는 다른 강아지가 함께 찍히는
 * 것이 기본이다. 보호자 본인 앨범으로 배분하는 것과, 보호자가 그 사진을 커뮤니티로
 * 내보내는 것(F-16)은 다른 동의다. 서버가 "동의하지 않은 원생이 함께 찍혔는지"를 알려주지
 * 않으면 클라이언트가 판단할 근거가 없다.
 *
 * <p>R2는 로컬에서 더미라 저장 계층을 목으로 대체한다 — 검증 대상은 업로드 자체가 아니라
 * 태깅·동의·소속 판정이다.
 */
@SpringBootTest
@ActiveProfiles("local")
class MediaTaggingTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private MediaService mediaService;
    @Autowired private MediaTagRepository tagRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private EnrollmentApplicationRepository applicationRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository merchantStaffRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    /** R2가 로컬에서 더미라 저장만 대체한다. 검증 대상은 그 뒤의 태깅·동의 판정이다. */
    @MockitoBean private MediaStorageService storageService;

    private UUID merchantId;
    private UUID staffUserId;
    private UUID dogId;
    private UUID formId;
    private UUID consentId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            staffUserId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                    .setParameter(1, staffUserId)
                    .setParameter(2, staffUserId + "@test.local").executeUpdate();

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("사진테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장").status(MerchantStatus.ACTIVE).build()).getId();

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(staffUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE).build());

            dogId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO dogs (id, owner_id, name) VALUES (?1, ?2, '더미')")
                    .setParameter(1, dogId).setParameter(2, staffUserId).executeUpdate();

            formId = em_uuid("INSERT INTO application_forms (merchant_id, version, is_active) "
                    + "VALUES (?1, 1, true) RETURNING id");
            consentId = em_uuid("INSERT INTO consent_documents (merchant_id, version, body, is_active) "
                    + "VALUES (?1, 1, '전문', true) RETURNING id");
        });

        when(storageService.upload(any(), anyString())).thenReturn(
                new MediaStorageService.StoredMedia(
                        "https://cdn.test/photo.jpg", true, 1_234_567L, 3000, 2000));
    }

    private UUID em_uuid(String sql) {
        return (UUID) em.createNativeQuery(sql).setParameter(1, merchantId).getSingleResult();
    }

    @AfterEach
    void tearDown() {
        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("""
                    DELETE FROM media_tags WHERE media_id IN
                      (SELECT id FROM merchant_media WHERE merchant_id = ?1)""")
                    .setParameter(1, merchantId).executeUpdate();
            for (String t : List.of("merchant_media", "enrollments", "enrollment_applications",
                    "application_forms", "consent_documents", "merchant_staff")) {
                em.createNativeQuery("DELETE FROM " + t + " WHERE merchant_id = ?1")
                        .setParameter(1, merchantId).executeUpdate();
            }
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM dogs WHERE id = ?1")
                    .setParameter(1, dogId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1")
                    .setParameter(1, staffUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("촬영·공개에 동의한 원생만 태깅되면 공유가 제한되지 않는다")
    void allConsentedMeansSharable() {
        UUID a = enrollmentWithConsent("두부", true);
        UUID b = enrollmentWithConsent("초코", true);

        var result = mediaService.upload(merchantId, staffUserId,
                List.of(file("a.jpg")), TODAY, List.of(a, b));

        assertThat(result.uploaded()).singleElement().satisfies(m -> {
            assertThat(m.tagged()).hasSize(2);
            assertThat(m.tagged()).allMatch(MediaResponse.detailInfo.taggedItem::photoPublicConsented);
            assertThat(m.sharingRestricted()).isFalse();
        });
        assertThat(result.totalBytes()).isEqualTo(1_234_567L);
    }

    @Test
    @DisplayName("한 명이라도 미동의면 공유가 제한된다 — 단체 사진의 기본 상황이다")
    void oneNonConsentRestrictsSharing() {
        UUID consented = enrollmentWithConsent("두부", true);
        UUID declined = enrollmentWithConsent("초코", false);

        var result = mediaService.upload(merchantId, staffUserId,
                List.of(file("a.jpg")), TODAY, List.of(consented, declined));

        var media = result.uploaded().get(0);
        // ★ PC-29 — 내보내기(F-16) 버튼을 막는 근거다.
        assertThat(media.sharingRestricted()).isTrue();
        assertThat(media.tagged()).filteredOn(t -> "초코".equals(t.dogName()))
                .singleElement()
                .satisfies(t -> assertThat(t.photoPublicConsented()).isFalse());
        // 미동의라고 배분 자체를 막지는 않는다 — 자기 개가 나온 사진은 보호자가 받아야 한다.
        assertThat(media.tagged()).hasSize(2);
    }

    @Test
    @DisplayName("동의를 확인할 수 없으면 동의하지 않은 것으로 본다")
    void unknownConsentIsTreatedAsDeclined() {
        // 신청서 없이 만들어진 원생 — 동의 이력을 찾을 수 없다.
        UUID orphan = txTemplate.execute(tx -> enrollmentRepository.save(Enrollment.builder()
                .merchantId(merchantId).dogNameSnapshot("이력없음").build()).getId());

        var result = mediaService.upload(merchantId, staffUserId,
                List.of(file("a.jpg")), TODAY, List.of(orphan));

        // ⚠️ 확인 불가를 동의로 해석하면 동의 없이 공유되는 사고가 난다.
        assertThat(result.uploaded().get(0).sharingRestricted()).isTrue();
        assertThat(result.uploaded().get(0).tagged()).singleElement()
                .satisfies(t -> assertThat(t.photoPublicConsented()).isFalse());
    }

    @Test
    @DisplayName("태깅 수정은 통째로 교체한다 — 빈 목록이면 모든 배분이 해제된다")
    void retagReplacesAll() {
        UUID a = enrollmentWithConsent("두부", true);
        UUID b = enrollmentWithConsent("초코", true);

        UUID mediaId = mediaService.upload(merchantId, staffUserId,
                List.of(file("a.jpg")), TODAY, List.of(a, b)).uploaded().get(0).id();
        assertThat(tagRepository.findAllByMediaId(mediaId)).hasSize(2);

        mediaService.retag(merchantId, staffUserId, mediaId, List.of(a));
        assertThat(tagRepository.findAllByMediaId(mediaId)).singleElement()
                .satisfies(t -> assertThat(t.getEnrollmentId()).isEqualTo(a));

        mediaService.retag(merchantId, staffUserId, mediaId, List.of());
        assertThat(tagRepository.findAllByMediaId(mediaId)).isEmpty();
    }

    @Test
    @DisplayName("남의 매장 원생은 태깅되지 않는다")
    void cannotTagOtherMerchantEnrollment() {
        UUID mine = enrollmentWithConsent("두부", true);

        UUID otherMerchant = txTemplate.execute(tx -> merchantRepository.save(Merchant.builder()
                .name("남의유치원")
                .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                .representativeName("박원장").status(MerchantStatus.ACTIVE).build()).getId());
        UUID theirs = txTemplate.execute(tx -> enrollmentRepository.save(Enrollment.builder()
                .merchantId(otherMerchant).dogNameSnapshot("남의개").build()).getId());

        var result = mediaService.upload(merchantId, staffUserId,
                List.of(file("a.jpg")), TODAY, List.of(mine, theirs));

        assertThat(result.uploaded().get(0).tagged()).singleElement()
                .satisfies(t -> assertThat(t.enrollmentId()).isEqualTo(mine));

        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("DELETE FROM enrollments WHERE merchant_id = ?1")
                    .setParameter(1, otherMerchant).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, otherMerchant).executeUpdate();
        });
    }

    @Test
    @DisplayName("업로드는 부분 성공한다 — 한 장이 실패해도 나머지는 저장된다")
    void uploadAllowsPartialSuccess() {
        UUID a = enrollmentWithConsent("두부", true);

        when(storageService.upload(any(), anyString()))
                .thenReturn(new MediaStorageService.StoredMedia("https://cdn.test/1.jpg", true, 100L, 800, 600))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_TOO_LARGE))
                .thenReturn(new MediaStorageService.StoredMedia("https://cdn.test/3.jpg", true, 200L, 800, 600));

        var result = mediaService.upload(merchantId, staffUserId,
                List.of(file("1.jpg"), file("2.jpg"), file("3.jpg")), TODAY, List.of(a));

        assertThat(result.uploaded()).hasSize(2);
        assertThat(result.failed()).singleElement()
                .satisfies(f -> {
                    assertThat(f.code()).isEqualTo("MD002");
                    assertThat(f.fileName()).isEqualTo("2.jpg");
                });
        // ⚠️ PC-23 — 스토리지 증가가 눈에 보이라고 준다. 실패분은 세지 않는다.
        assertThat(result.totalBytes()).isEqualTo(300L);
    }

    @Test
    @DisplayName("한 번에 올릴 수 있는 파일 수를 넘으면 거부한다")
    void tooManyFilesRejected() {
        List<org.springframework.web.multipart.MultipartFile> many =
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> file(i + ".jpg")).toList();

        assertThatThrownBy(() -> mediaService.upload(merchantId, staffUserId, many, TODAY, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_MEDIA_FILES);
    }

    @Test
    @DisplayName("원생별 조회는 그 아이가 태깅된 사진만 준다")
    void byEnrollmentReturnsOnlyTagged() {
        UUID a = enrollmentWithConsent("두부", true);
        UUID b = enrollmentWithConsent("초코", true);

        mediaService.upload(merchantId, staffUserId, List.of(file("a.jpg")), TODAY, List.of(a));
        mediaService.upload(merchantId, staffUserId, List.of(file("b.jpg")), TODAY, List.of(b));

        assertThat(mediaService.getByEnrollment(merchantId, staffUserId, a)).hasSize(1);
        assertThat(mediaService.getAlbum(merchantId, staffUserId, TODAY)).hasSize(2);
    }

    // ---- 픽스처 ----

    /** 승인된 신청서와 함께 원생을 만든다 — 동의 이력이 거기 남아 있어야 판정이 가능하다. */
    private UUID enrollmentWithConsent(String dogName, boolean photoPublic) {
        return txTemplate.execute(tx -> {
            EnrollmentApplication application = applicationRepository.save(EnrollmentApplication.builder()
                    .merchantId(merchantId).dogId(dogId).applicantUserId(staffUserId)
                    .formId(formId).consentDocumentId(consentId)
                    .consentedItems(Map.of(
                            "accident_liability", true,
                            ConsentDocument.ITEM_PHOTO_PUBLIC, photoPublic))
                    .status(ApplicationStatus.APPROVED)
                    .build());

            return enrollmentRepository.save(Enrollment.builder()
                    .merchantId(merchantId)
                    .dogNameSnapshot(dogName)
                    .applicationId(application.getId())
                    .build()).getId();
        });
    }

    private org.springframework.web.multipart.MultipartFile file(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1, 2, 3});
    }
}
