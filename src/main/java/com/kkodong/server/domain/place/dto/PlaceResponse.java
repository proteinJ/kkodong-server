package com.kkodong.server.domain.place.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 견주 지도 응답 (API_SPEC 14.2).
 *
 * <p>필드 이름과 모양은 14절 계약 그대로다. iOS({@code Features/Places/})가 이미 이 모양으로
 * 디코딩하고 있어서, 이름이 하나라도 어긋나면 지도 화면이 통째로 빈다.
 */
public class PlaceResponse {

    @Schema(description = "주변 매장 한 페이지")
    public record page(
            @Schema(description = "가까운 순(거리가 같으면 id 순) 매장 목록") List<summary> items,
            @Schema(description = "다음 페이지 커서. 마지막 페이지면 null. 다음 요청의 cursor 에 그대로 넣는다")
            String nextCursor
    ) {
    }

    /**
     * 지도 핀과 카드 한 장.
     *
     * <p>{@code isOpenNow} 에 {@link JsonProperty} 를 붙인 이유: 계약의 필드 이름이 "is" 로
     * 시작한다. JSON 라이브러리 설정이나 버전에 따라 {@code openNow} 로 바뀌어 나갈 수 있어,
     * 이름을 명시해 고정한다.
     */
    @Schema(description = "지도 핀·카드 한 장")
    public record summary(
            @Schema(description = "매장 ID") UUID id,
            @Schema(description = "상호", example = "망원 댕댕유치원") String name,
            @Schema(description = "업종", allowableValues = {"kindergarten", "grooming", "clinic"},
                    example = "kindergarten") String category,
            @Schema(description = "위도", example = "37.5632") double lat,
            @Schema(description = "경도", example = "126.9237") double lng,
            @Schema(description = "주소 (상세와 같은 전체 주소)", example = "서울 마포구 망원로 12, 1층") String address,
            @Schema(description = "전화번호. 없으면 null", example = "02-336-1234") String phone,
            @Schema(description = "대표 사진(사진 목록의 첫 장). 없으면 null") String thumbnailUrl,
            @Schema(description = "거리(km). 정수로 반올림 — 500m 미만은 0", example = "1") int distanceKm,
            @JsonProperty("isOpenNow")
            @Schema(description = "지금 영업 중인가(한국 시간). 영업시간이 입력되지 않은 매장은 null", example = "true")
            Boolean isOpenNow,
            @Schema(description = "이 매장에 재원 중인 내 친구 강아지 수", example = "2") int friendDogCount
    ) {
    }
}
