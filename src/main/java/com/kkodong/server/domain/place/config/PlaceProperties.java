package com.kkodong.server.domain.place.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 견주 지도 조회 정책 (MAP-01, API_SPEC 14.2).
 *
 * <p><b>왜 설정값인가</b>: 반경 상한과 페이지 크기는 계약에 없던 값을 서버가 정한 것이고
 * (#50), 실제 매장 밀도를 보고 조정할 가능성이 크다. 재빌드 없이 바꿀 수 있어야 한다.
 *
 * <p><b>왜 {@code global/config} 가 아니라 여기인가</b>: 이 값은 지도 도메인만 쓴다.
 * {@code global/} 은 세 사람이 공유하는 자리라, 한 도메인 전용 설정을 거기 두면 공유 파일이
 * 불필요하게 늘어난다. {@code @ConfigurationPropertiesScan} 이 전체 패키지를 훑으므로 등록은 같다.
 *
 * @param defaultRadiusKm 반경을 안 보냈을 때 쓰는 값
 * @param maxRadiusKm     반경 상한. 넘으면 에러가 아니라 이 값으로 자른다 — 상한이 없으면
 *                        반경 1000km 한 번으로 전국 매장을 긁어갈 수 있다
 * @param pageSize        한 번에 내려주는 최대 매장 수
 */
@ConfigurationProperties(prefix = "kkodong.place")
@Validated
public record PlaceProperties(
        @Positive double defaultRadiusKm,
        @Positive double maxRadiusKm,
        @Positive int pageSize
) {

    public PlaceProperties {
        // 기본값이 상한보다 크면 "안 보냈을 때"가 항상 잘려서 설정의 뜻이 사라진다. 기동 시 막는다.
        if (defaultRadiusKm > maxRadiusKm) {
            throw new IllegalArgumentException(
                    "kkodong.place.default-radius-km 는 max-radius-km 보다 클 수 없다");
        }
    }
}
