package com.kkodong.server.domain.enrollment;

import com.kkodong.server.domain.dog.domain.Dog;
import com.kkodong.server.domain.dog.repository.DogRepository;
import com.kkodong.server.domain.enrollment.domain.*;
import com.kkodong.server.domain.enrollment.dto.ApplicationRequest;
import com.kkodong.server.domain.enrollment.dto.ApplicationResponse;
import com.kkodong.server.domain.enrollment.repository.EnrollmentApplicationRepository;
import com.kkodong.server.domain.enrollment.repository.EnrollmentRepository;
import com.kkodong.server.domain.enrollment.service.ApplicationFormService;
import com.kkodong.server.domain.enrollment.service.ApplicationInboxService;
import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 신청 승인이 <b>원생을 만드는</b> 것을 검증한다(PN-07, FR-PN07-01, 흐름 F-13).
 *
 * <p>점주 앱에서 원생이 생기는 유일한 경로다. 승인만 되고 원생이 안 생기면 견주에게는
 * "승인됨"으로 보이는데 매장에는 원생이 없어 등원도 이용권 발급도 되지 않고,
 * 신청은 이미 처리됨이라 다시 승인할 수도 없다.
 *
 * <p>⚠️ 신청서 제출은 견주 앱(KG-06/07) 몫이라 서버에 엔드포인트가 없다.
 * 그래서 이 테스트는 신청 행을 직접 만들어 접수함에 넣는다.
 */
@SpringBootTest
@ActiveProfiles("local")
class ApplicationApprovalTest {

    @Autowired private ApplicationInboxService inboxService;
    @Autowired private ApplicationFormService formService;
    @Autowired private EnrollmentApplicationRepository applicationRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private DogRepository dogRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private MerchantStaffRepository merchantStaffRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    private UUID merchantId;
    private UUID directorUserId;
    private UUID ownerUserId;
    private UUID dogId;
    private UUID formId;
    private UUID consentId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            directorUserId = insertUser("director");
            ownerUserId = insertUser("owner");

            merchantId = merchantRepository.save(Merchant.builder()
                    .name("접수함테스트유치원")
                    .businessRegistrationNumber(String.valueOf(System.nanoTime()).substring(0, 10))
                    .representativeName("김원장").status(MerchantStatus.ACTIVE)
                    .build()).getId();

            merchantStaffRepository.save(MerchantStaff.builder()
                    .merchantId(merchantId).userId(directorUserId)
                    .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE)
                    .build());

            dogId = dogRepository.save(Dog.builder()
                    .ownerId(ownerUserId).name("두부").breed("포메라니안")
                    .size(com.kkodong.server.domain.dog.domain.DogSize.SMALL)
                    .neutered(true)
                    .build()).getId();
        });

        // PN-06 — 점주가 양식과 서약서를 먼저 발행한다. 견주 앱은 이걸 받아 그린다.
        formId = UUID.fromString(formService.publishForm(merchantId, directorUserId,
                new ApplicationRequest.publishForm(List.of(
                        new ApplicationForm.ExtraField("pickup", "픽업 필요", "boolean", false, null))))
                .id().toString());
        consentId = formService.publishConsent(merchantId, directorUserId,
                new ApplicationRequest.publishConsent("서약서 전문입니다.", List.of(
                        new ConsentDocument.ConsentItem("accident_liability", "사고 책임", true),
                        new ConsentDocument.ConsentItem("photo_public", "촬영·공개", false))))
                .id();
    }

    @AfterEach
    void tearDown() {
        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("DELETE FROM enrollments WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM enrollment_applications WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM application_forms WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM consent_documents WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchant_staff WHERE merchant_id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE id = ?1")
                    .setParameter(1, merchantId).executeUpdate();
            em.createNativeQuery("DELETE FROM dogs WHERE id = ?1")
                    .setParameter(1, dogId).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id IN (?1, ?2)")
                    .setParameter(1, directorUserId).setParameter(2, ownerUserId).executeUpdate();
        });
    }

    @Test
    @DisplayName("승인하면 원생이 생기고, 반려견 정보가 스냅샷으로 복사된다")
    void approveCreatesEnrollmentWithSnapshot() {
        UUID applicationId = submitApplication();

        ApplicationResponse.approveResult result =
                inboxService.approve(merchantId, directorUserId, applicationId);

        assertThat(result.application().status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(result.enrollmentId()).isNotNull();

        Enrollment enrollment = enrollmentRepository.findById(result.enrollmentId()).orElseThrow();
        assertThat(enrollment.getMerchantId()).isEqualTo(merchantId);
        assertThat(enrollment.getDogId()).isEqualTo(dogId);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(enrollment.isReservable()).isTrue(); // 곧바로 예약 대상이 된다

        // ⚠️ 스냅샷은 편의용이 아니다 — 견주가 탈퇴하면 dogs가 지워지고 dog_id는 null이 되는데,
        //    그때도 점주 화면이 "이름 없는 원생"을 보이면 안 된다.
        assertThat(enrollment.getDogNameSnapshot()).isEqualTo("두부");
        assertThat(enrollment.getDogBreedSnapshot()).isEqualTo("포메라니안");
        assertThat(enrollment.getApplicationId()).isEqualTo(applicationId);
    }

    @Test
    @DisplayName("접수함은 꼬동 프로필의 반려견 정보를 함께 실어 내린다")
    void inboxCarriesDogProfile() {
        submitApplication();

        List<ApplicationResponse.item> inbox = inboxService.getInbox(merchantId, directorUserId, null);

        assertThat(inbox).hasSize(1);
        // FR-PN11-01 — 점주가 수용 조건(체급·중성화)을 바로 판단할 수 있어야 한다.
        // 이 정보를 유치원마다 새로 받지 않아도 되는 것이 꼬동을 쓰는 이유다.
        assertThat(inbox.get(0).dog()).isNotNull().satisfies(d -> {
            assertThat(d.name()).isEqualTo("두부");
            assertThat(d.breed()).isEqualTo("포메라니안");
            assertThat(d.size()).isEqualTo("small");
            assertThat(d.neutered()).isTrue();
        });
        assertThat(inboxService.countPending(merchantId, directorUserId)).isEqualTo(1);
    }

    @Test
    @DisplayName("항목별 동의 결과가 그대로 보존된다 — 사진 공개 판단의 근거다")
    void consentItemsArePreservedPerItem() {
        UUID applicationId = submitApplication();

        ApplicationResponse.item item = inboxService.get(merchantId, directorUserId, applicationId);

        // PC-29 — 사고 책임과 촬영·공개를 한 덩어리로 받으면 나중에 이 사진을
        // 커뮤니티로 내보내도 되는지 판단할 근거가 없다.
        assertThat(item.consentedItems())
                .containsEntry("accident_liability", true)
                .containsEntry("photo_public", false);

        EnrollmentApplication entity = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(entity.hasConsented("accident_liability")).isTrue();
        assertThat(entity.hasConsented("photo_public")).isFalse();
    }

    @Test
    @DisplayName("이미 재원 중인 강아지를 또 승인하면 막힌다 — 원생이 둘로 갈리면 안 된다")
    void duplicateApprovalIsRejected() {
        inboxService.approve(merchantId, directorUserId, submitApplication());

        UUID second = submitApplication();
        assertThatThrownBy(() -> inboxService.approve(merchantId, directorUserId, second))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_ENROLLED);

        // 원생이 하나만 남아야 한다. 둘이면 이용권과 출석이 각각 다른 원생에 붙는다.
        assertThat(enrollmentRepository.findAllByMerchantIdAndStatus(merchantId, EnrollmentStatus.ACTIVE))
                .hasSize(1);
    }

    @Test
    @DisplayName("이미 처리된 신청은 다시 승인·거절할 수 없다")
    void reviewedApplicationCannotBeReviewedAgain() {
        UUID applicationId = submitApplication();
        inboxService.reject(merchantId, directorUserId, applicationId,
                new ApplicationRequest.reject("정원 초과"));

        assertThatThrownBy(() -> inboxService.approve(merchantId, directorUserId, applicationId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_REVIEWED);

        // 거절은 원생을 만들지 않는다.
        assertThat(enrollmentRepository.findAllByMerchantIdAndStatus(merchantId, EnrollmentStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    @DisplayName("양식을 새로 발행하면 이전 버전이 내려가고 활성은 항상 하나다")
    void publishingFormSupersedesPreviousVersion() {
        var v2 = formService.publishForm(merchantId, directorUserId,
                new ApplicationRequest.publishForm(List.of(
                        new ApplicationForm.ExtraField("allergy", "알레르기", "text", true, null))));

        assertThat(v2.version()).isEqualTo(2);
        assertThat(formService.getActiveForm(merchantId, directorUserId).id()).isEqualTo(v2.id());

        // 이전 버전은 지워지지 않는다 — 그 버전으로 제출된 신청서의 답을 해석하려면 필요하다.
        var history = formService.getFormHistory(merchantId, directorUserId);
        assertThat(history).hasSize(2);
        assertThat(history).filteredOn(ApplicationResponse.formInfo::isActive).hasSize(1);
    }

    // ---- 견주 앱이 할 일을 테스트에서 대신한다 (KG-06/07) ----

    /**
     * 견주가 신청서를 제출한 상태를 만든다.
     *
     * <p>⚠️ 서버에 이 경로의 엔드포인트는 없다 — 신청서 작성과 서약서 동의는 견주 앱
     * (KG-06/KG-07)의 몫이고, 점주 앱은 들어온 신청을 처리하기만 한다.
     */
    private UUID submitApplication() {
        return txTemplate.execute(tx -> applicationRepository.save(EnrollmentApplication.builder()
                .merchantId(merchantId)
                .dogId(dogId)
                .applicantUserId(ownerUserId)
                .formId(formId)
                .consentDocumentId(consentId)
                .submittedValues(Map.of("pickup", true))
                .consentedItems(Map.of("accident_liability", true, "photo_public", false))
                .consentedAt(java.time.OffsetDateTime.now())
                .status(ApplicationStatus.PENDING)
                .build()).getId());
    }

    private UUID insertUser(String prefix) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                        "INSERT INTO users (id, email, password_hash, display_name) VALUES (?1, ?2, 'x', ?3)")
                .setParameter(1, id).setParameter(2, prefix + "-" + id + "@test.local")
                .setParameter(3, prefix)
                .executeUpdate();
        return id;
    }
}
