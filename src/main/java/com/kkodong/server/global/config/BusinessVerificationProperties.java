package com.kkodong.server.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * 국세청 사업자등록번호 진위확인(FR-PN02-01).
 *
 * <p>공공데이터포털(data.go.kr)의 "국세청_사업자등록정보 진위확인 및 상태조회" API를 쓴다.
 * 무료이며 인증키만 발급받으면 된다.
 *
 * <p><b>왜 켜고 끌 수 있게 하는가</b>: 로컬 개발과 테스트에서 외부 API를 매번 부를 수 없다.
 * 다만 ⚠️ <b>꺼져 있으면 매장이 검증되지 않은 상태(PENDING)로 남는다</b> — 통과시킨 척하지
 * 않는다. 검증 없이 ACTIVE가 되면 아무 번호나 넣은 매장이 견주 앱에 노출된다.
 *
 * @param enabled       진위확인 수행 여부. 운영에서는 반드시 true
 * @param serviceKey    공공데이터포털 인증키(Decoding 키)
 * @param baseUrl       API 기본 주소
 * @param timeoutMillis 응답 대기 상한. 국세청 API가 느릴 때 가입 요청이 통째로 묶이지 않게 한다
 */
@ConfigurationProperties(prefix = "kkodong.business-verification")
@Validated
public record BusinessVerificationProperties(
        boolean enabled,
        String serviceKey,
        @NotBlank String baseUrl,
        @Positive int timeoutMillis
) {

    /**
     * 실제로 호출할 수 있는 상태인지.
     *
     * <p>{@code enabled=true}인데 키가 없으면 호출이 401로 실패한다. 그 경우를 "검증 실패"로
     * 다루면 원인이 사업자번호인지 설정인지 구분되지 않으므로, 기동 시점에 걸러낸다
     * ({@code NtsBusinessVerifier} 생성자 참조).
     */
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(serviceKey);
    }
}
