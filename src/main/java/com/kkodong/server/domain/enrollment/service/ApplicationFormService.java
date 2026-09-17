package com.kkodong.server.domain.enrollment.service;

import com.kkodong.server.domain.enrollment.domain.ApplicationForm;
import com.kkodong.server.domain.enrollment.domain.ConsentDocument;
import com.kkodong.server.domain.enrollment.dto.ApplicationRequest;
import com.kkodong.server.domain.enrollment.dto.ApplicationResponse;
import com.kkodong.server.domain.enrollment.repository.ApplicationFormRepository;
import com.kkodong.server.domain.enrollment.repository.ConsentDocumentRepository;
import com.kkodong.server.domain.merchant.service.MerchantAccessGuard;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 신청서 양식·서약서 관리(PN-06, FR-PN06-01).
 *
 * <p>점주가 구성하고 <b>견주 앱(KG-06/KG-07)이 렌더링</b>한다. 신청서 제출 자체는
 * 견주 앱 몫이라 여기 없다 — 이 서비스는 "무엇을 물어볼지"를 정의하는 쪽이다.
 *
 * <p>⚠️ 수정이 아니라 <b>새 버전 발행</b>만 제공한다. 양식을 고치면 과거 제출값이 어떤
 * 질문에 대한 답인지 알 수 없게 되고, 서약서를 고치면 무엇에 동의한 것인지 알 수 없게 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationFormService {

    private final ApplicationFormRepository formRepository;
    private final ConsentDocumentRepository consentRepository;
    private final MerchantAccessGuard accessGuard;

    /**
     * 신청서 양식 새 버전 발행(PN-06). 원장 전용.
     *
     * <p>기존 활성 양식을 내리고 다음 버전을 만든다. 두 동작이 한 트랜잭션이어야
     * "활성 양식이 둘"이거나 "활성 양식이 없는" 순간이 생기지 않는다 —
     * DB의 부분 유니크 인덱스가 전자를 막지만, 후자는 견주 앱이 신청서를 못 그리는 상태다.
     */
    @Transactional
    public ApplicationResponse.formInfo publishForm(
            UUID merchantId, UUID userId, ApplicationRequest.publishForm request) {
        accessGuard.requireDirector(merchantId, userId);

        int nextVersion = formRepository.findAllByMerchantIdOrderByVersionDesc(merchantId).stream()
                .findFirst().map(f -> f.getVersion() + 1).orElse(1);

        formRepository.findByMerchantIdAndIsActiveTrue(merchantId)
                .ifPresent(ApplicationForm::deactivate);
        // 이전 버전을 먼저 내려야 부분 유니크(활성은 매장당 하나)에 걸리지 않는다.
        formRepository.flush();

        ApplicationForm form = formRepository.save(ApplicationForm.builder()
                .merchantId(merchantId)
                .version(nextVersion)
                .extraFields(request.extraFields() == null ? List.of() : request.extraFields())
                .isActive(true)
                .build());

        return ApplicationResponse.formInfo.from(form);
    }

    /** 서약서 새 버전 발행(PN-06). 원장 전용. */
    @Transactional
    public ApplicationResponse.consentInfo publishConsent(
            UUID merchantId, UUID userId, ApplicationRequest.publishConsent request) {
        accessGuard.requireDirector(merchantId, userId);

        int nextVersion = consentRepository.findAllByMerchantIdOrderByVersionDesc(merchantId).stream()
                .findFirst().map(c -> c.getVersion() + 1).orElse(1);

        consentRepository.findByMerchantIdAndIsActiveTrue(merchantId)
                .ifPresent(ConsentDocument::deactivate);
        consentRepository.flush();

        ConsentDocument consent = consentRepository.save(ConsentDocument.builder()
                .merchantId(merchantId)
                .version(nextVersion)
                .body(request.body())
                .items(request.items())
                .isActive(true)
                .build());

        return ApplicationResponse.consentInfo.from(consent);
    }

    /** 현재 활성 양식(PN-06 미리보기). 견주 앱이 받을 것과 같은 내용이다. */
    public ApplicationResponse.formInfo getActiveForm(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return formRepository.findByMerchantIdAndIsActiveTrue(merchantId)
                .map(ApplicationResponse.formInfo::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_FORM_NOT_FOUND));
    }

    public ApplicationResponse.consentInfo getActiveConsent(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return consentRepository.findByMerchantIdAndIsActiveTrue(merchantId)
                .map(ApplicationResponse.consentInfo::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSENT_DOCUMENT_NOT_FOUND));
    }

    /** 양식 버전 이력(PN-06). 과거 제출값을 해석하려면 당시 양식이 필요하다. */
    public List<ApplicationResponse.formInfo> getFormHistory(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return formRepository.findAllByMerchantIdOrderByVersionDesc(merchantId).stream()
                .map(ApplicationResponse.formInfo::from)
                .toList();
    }

    public List<ApplicationResponse.consentInfo> getConsentHistory(UUID merchantId, UUID userId) {
        accessGuard.requireStaff(merchantId, userId);
        return consentRepository.findAllByMerchantIdOrderByVersionDesc(merchantId).stream()
                .map(ApplicationResponse.consentInfo::from)
                .toList();
    }
}
