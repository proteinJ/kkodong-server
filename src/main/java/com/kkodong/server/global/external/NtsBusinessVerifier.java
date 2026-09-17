package com.kkodong.server.global.external;

import com.kkodong.server.global.config.BusinessVerificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 국세청 사업자등록번호 진위확인(FR-PN02-01).
 *
 * <p>공공데이터포털의 {@code nts-businessman/v1/validate}를 호출한다. 사업자등록번호·대표자명·
 * 개업일 <b>세 가지가 모두 일치</b>해야 유효로 판정된다 — 번호만 맞으면 남의 사업자번호로
 * 매장을 열 수 있다.
 *
 * <p><b>실패를 유효로 해석하지 않는다.</b> 네트워크 오류·타임아웃·응답 형식 변경은 전부
 * {@link Result#UNAVAILABLE}이며, 호출부는 이것을 "통과"로 다루면 안 된다. 검증할 수 없을 때
 * 통과시키면 국세청 API가 잠깐 죽은 사이에 아무 번호나 등록된다.
 */
@Slf4j
@Component
public class NtsBusinessVerifier {

    /** API가 요구하는 개업일 형식. */
    private static final DateTimeFormatter START_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 응답의 {@code valid} 값. "01"만 유효이고 나머지("02" 확인불가 등)는 전부 불일치다. */
    private static final String VALID = "01";

    private final BusinessVerificationProperties properties;
    private final RestClient restClient;

    public NtsBusinessVerifier(BusinessVerificationProperties properties,
                               RestTemplateBuilder builder) {
        this.properties = properties;
        this.restClient = RestClient.builder(builder
                        .connectTimeout(Duration.ofMillis(properties.timeoutMillis()))
                        .readTimeout(Duration.ofMillis(properties.timeoutMillis()))
                        .build())
                .baseUrl(properties.baseUrl())
                .build();

        // ⚠️ 켜 두고 키를 빠뜨리면 모든 가입이 401로 실패하는데, 그 원인이 사업자번호인지
        //    설정인지 응답만 봐서는 구분되지 않는다. 기동 시점에 소리 내어 알린다.
        if (properties.enabled() && !properties.isConfigured()) {
            log.error("사업자 진위확인이 켜져 있으나 인증키가 없습니다. "
                    + "NTS_SERVICE_KEY를 설정하지 않으면 매장 개설이 전부 실패합니다.");
        }
    }

    /** 진위확인 결과. */
    public enum Result {
        /** 세 값이 모두 일치. */
        VALID_BUSINESS,
        /** 국세청에 등록되지 않았거나 입력값이 일치하지 않음. */
        MISMATCH,
        /** 호출 자체가 불가 — 네트워크·타임아웃·응답 형식 변경. ⚠️ 통과로 다루지 말 것. */
        UNAVAILABLE,
        /** 진위확인 기능이 꺼져 있음. 매장은 검증되지 않은 상태로 남는다. */
        DISABLED
    }

    /**
     * 사업자등록번호·대표자명·개업일이 국세청 기록과 일치하는지 확인한다.
     *
     * @param businessNumber 하이픈 없는 10자리
     * @param representative 대표자명
     * @param openedOn       개업일
     */
    public Result verify(String businessNumber, String representative, LocalDate openedOn) {
        if (!properties.isConfigured()) {
            return Result.DISABLED;
        }

        Map<String, Object> body = Map.of("businesses", List.of(Map.of(
                "b_no", businessNumber,
                "start_dt", openedOn.format(START_DATE),
                "p_nm", representative)));

        try {
            Map<?, ?> response = restClient.post()
                    .uri(uri -> uri.path("/validate")
                            .queryParam("serviceKey", properties.serviceKey())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return interpret(response, businessNumber);

        } catch (Exception e) {
            // ⚠️ 여기서 VALID를 돌려주면 국세청 API가 잠깐 죽은 사이에 아무 번호나 등록된다.
            log.warn("사업자 진위확인 호출 실패 (b_no={}): {}", businessNumber, e.toString());
            return Result.UNAVAILABLE;
        }
    }

    /**
     * 응답에서 {@code data[0].valid}를 읽는다.
     *
     * <p>형식이 예상과 다르면 {@link Result#UNAVAILABLE}이다 — API 응답 스펙이 바뀌었는데
     * 그걸 "불일치"로 처리하면 멀쩡한 사업자가 가입을 못 하고, "일치"로 처리하면 검증이
     * 통째로 무력해진다. 둘 다 틀렸으므로 "확인 불가"로 남긴다.
     */
    private Result interpret(Map<?, ?> response, String businessNumber) {
        if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()) {
            log.warn("사업자 진위확인 응답 형식이 예상과 다릅니다 (b_no={}): {}", businessNumber, response);
            return Result.UNAVAILABLE;
        }
        if (!(data.get(0) instanceof Map<?, ?> first)) {
            return Result.UNAVAILABLE;
        }
        return VALID.equals(first.get("valid")) ? Result.VALID_BUSINESS : Result.MISMATCH;
    }
}
