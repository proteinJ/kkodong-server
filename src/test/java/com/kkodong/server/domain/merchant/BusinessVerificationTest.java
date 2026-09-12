package com.kkodong.server.domain.merchant;

import com.kkodong.server.domain.merchant.domain.MerchantStatus;
import com.kkodong.server.domain.merchant.dto.MerchantRequest;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.service.MerchantService;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import com.kkodong.server.global.external.NtsBusinessVerifier;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 사업자 진위확인이 실제로 가입을 막는지 검증한다(PN-02, FR-PN02-01).
 *
 * <p><b>핵심은 "확인할 수 없을 때 통과시키지 않는가"다.</b> 국세청 API가 잠깐 죽었다고
 * 통과시키면 그 틈에 아무 번호나 등록되고, 사업자등록번호가 UNIQUE라 나중에 정상 가입도 막힌다.
 * 이런 종류는 장애가 나야 드러나므로 테스트로 고정해 둔다.
 *
 * <p>⚠️ 이 클래스는 진위확인을 <b>켠</b> 상태로 돌린다({@code @TestPropertySource}).
 * 다른 테스트들은 기본값(꺼짐)이라 매장이 PENDING으로 만들어진다.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "kkodong.business-verification.enabled=true",
        "kkodong.business-verification.service-key=test-key"
})
class BusinessVerificationTest {

    /**
     * ⚠️ 이 테스트 전용 표식. 정리할 때 이 이름으로만 지운다 —
     * 흔한 이름("김원장")으로 지우면 다른 테스트가 만든 매장까지 삭제하려다
     * 그 매장의 이용권 FK에 걸려 실패한다(실제로 한 번 겪었다).
     */
    private static final String MARKER = "진위확인전용대표";

    @Autowired private MerchantService merchantService;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager em;

    /** 국세청 API를 실제로 부를 수 없으니 응답만 대체한다. 검증 대상은 그 결과의 해석이다. */
    @MockitoBean private NtsBusinessVerifier verifier;

    private UUID userId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(tx -> {
            userId = UUID.randomUUID();
            em.createNativeQuery("INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                    .setParameter(1, userId).setParameter(2, userId + "@nts.test").executeUpdate();
        });
    }

    @AfterEach
    void tearDown() {
        txTemplate.executeWithoutResult(tx -> {
            em.createNativeQuery("""
                    DELETE FROM merchant_staff WHERE merchant_id IN
                      (SELECT id FROM merchants WHERE representative_name = ?1)""")
                    .setParameter(1, MARKER).executeUpdate();
            em.createNativeQuery("""
                    DELETE FROM kindergarten_profiles WHERE merchant_id IN
                      (SELECT id FROM merchants WHERE representative_name = ?1)""")
                    .setParameter(1, MARKER).executeUpdate();
            em.createNativeQuery("DELETE FROM merchants WHERE representative_name = ?1")
                    .setParameter(1, MARKER).executeUpdate();
            em.createNativeQuery("DELETE FROM users WHERE id = ?1").setParameter(1, userId).executeUpdate();
        });
    }

    @Test
    @DisplayName("진위확인을 통과하면 매장이 ACTIVE가 된다")
    void validBusinessBecomesActive() {
        when(verifier.verify(anyString(), anyString(), any()))
                .thenReturn(NtsBusinessVerifier.Result.VALID_BUSINESS);

        var created = merchantService.create(userId, request());

        assertThat(created.status()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(created.verifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("국세청 기록과 다르면 가입이 거부된다 — 매장이 만들어지지 않는다")
    void mismatchRejectsSignup() {
        when(verifier.verify(anyString(), anyString(), any()))
                .thenReturn(NtsBusinessVerifier.Result.MISMATCH);

        MerchantRequest.create request = request();
        assertThatThrownBy(() -> merchantService.create(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_VERIFICATION_FAILED);

        // ⚠️ 반쯤 만들어두면 안 된다 — 사업자등록번호가 UNIQUE라 재시도조차 막힌다.
        assertThat(merchantRepository
                .existsByBusinessRegistrationNumber(request.businessRegistrationNumber())).isFalse();
    }

    @Test
    @DisplayName("★ 확인할 수 없으면 통과시키지 않는다 — API 장애 중에 아무 번호나 등록되면 안 된다")
    void unavailableDoesNotPass() {
        when(verifier.verify(anyString(), anyString(), any()))
                .thenReturn(NtsBusinessVerifier.Result.UNAVAILABLE);

        MerchantRequest.create request = request();
        assertThatThrownBy(() -> merchantService.create(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                // 불일치(P003)와 구분되는 코드여야 점주에게 "잠시 후 다시"를 안내할 수 있다.
                .isEqualTo(ErrorCode.BUSINESS_VERIFICATION_UNAVAILABLE);

        assertThat(merchantRepository
                .existsByBusinessRegistrationNumber(request.businessRegistrationNumber())).isFalse();
    }

    @Test
    @DisplayName("개업일 없이는 진위확인을 할 수 없으므로 미리 막는다")
    void openedOnIsRequiredWhenVerifying() {
        MerchantRequest.create noDate = new MerchantRequest.create(
                "햇살유치원", uniqueBrn(), MARKER, null, null);

        assertThatThrownBy(() -> merchantService.create(userId, noDate))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_OPENED_ON_REQUIRED);
    }

    @Test
    @DisplayName("진위확인은 번호·대표자명·개업일 세 값을 함께 넘긴다")
    void allThreeValuesArePassed() {
        when(verifier.verify(anyString(), anyString(), any()))
                .thenReturn(NtsBusinessVerifier.Result.VALID_BUSINESS);

        MerchantRequest.create request = request();
        merchantService.create(userId, request);

        // 번호만 대조하면 남의 사업자번호로 매장을 열 수 있다.
        org.mockito.Mockito.verify(verifier).verify(
                request.businessRegistrationNumber(), MARKER, LocalDate.of(2024, 3, 2));
    }

    private MerchantRequest.create request() {
        return new MerchantRequest.create(
                "햇살유치원", uniqueBrn(), MARKER, LocalDate.of(2024, 3, 2), null);
    }

    private static String uniqueBrn() {
        return String.valueOf(System.nanoTime()).substring(0, 10);
    }
}
