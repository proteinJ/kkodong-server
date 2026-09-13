package com.kkodong.server.domain.place.controller;

import com.kkodong.server.domain.place.dto.PlaceResponse;
import com.kkodong.server.domain.place.service.PlaceService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 견주 지도 — 강아지 동반 매장 탐색 (MAP-01, API_SPEC 14절).
 *
 * <p>점주가 등록한 {@code merchants} 행을 견주에게 읽기 전용으로 연다. 점주 경로
 * ({@code /api/v1/partner/...})와 섞지 않는다.
 *
 * <p><b>인증이 필요하다</b> — {@code friendDogCount} 가 요청자를 알아야 계산된다.
 */
@Tag(name = "Place (견주 지도)", description = "강아지 동반 매장 지도 API — MAP-01 주변 매장 목록")
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    /**
     * <b>{@code lat}·{@code lng} 를 {@code required = false} 로 받는 이유</b>: 필수로 두면 값이 없을 때
     * Spring 이 {@code MissingServletRequestParameterException} 을 던지는데, 지금 전역 예외 처리기에
     * 그 처리가 없어 400 이 아니라 500 으로 나간다. 계약(14.2)은 400({@code CM001})이므로
     * 서비스에서 직접 검증한다. 문서에는 필수로 표시한다.
     */
    @Operation(
            summary = "주변 매장 목록 (MAP-01)",
            description = """
                    내 위치 기준 반경 안의 강아지 동반 매장(유치원·미용실·동물병원)을 가까운 순으로 반환합니다.

                    - **운영 중(`active`)이고 좌표가 있는 매장만** 나옵니다.
                    - 반경은 기본 **3km**, 최대 **10km**입니다. 10을 넘기면 에러가 아니라 10km로 잘립니다.
                    - 한 번에 최대 **50개**입니다. 더 있으면 `nextCursor`가 채워집니다.
                      **다음 페이지는 첫 요청과 같은 `lat`·`lng`·`radiusKm`·`categories`·`keyword`로** 부르고
                      `cursor`만 추가하세요. 조건이 바뀌면 처음부터 다시 부릅니다.
                    - `isOpenNow`는 서버가 한국 시간 기준으로 판정합니다. **영업시간이 입력되지 않은 매장은 `null`** 입니다.
                    - `distanceKm`는 정수 반올림이라 500m 미만은 `0`입니다.
                    - `friendDogCount`는 친구 기능(FRIEND-2) 전까지 항상 0입니다.

                    **에러** — `lat`·`lng`가 없거나 범위 밖, `radiusKm`가 0 이하, `cursor` 형식이 틀리면 `400 CM001`입니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PlaceResponse.page>> nearby(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "내 위치 위도 (필수, −90~90)", required = true, example = "37.5605")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "내 위치 경도 (필수, −180~180)", required = true, example = "126.9237")
            @RequestParam(required = false) Double lng,
            @Parameter(description = "반경(km). 기본 3, 최대 10 — 넘으면 10으로 잘린다", example = "3")
            @RequestParam(required = false) Double radiusKm,
            @Parameter(description = "업종, 쉼표 구분(kindergarten·grooming·clinic). 비우면 전 업종",
                    example = "kindergarten,grooming")
            @RequestParam(required = false) String categories,
            @Parameter(description = "상호 부분 일치 검색어. 비우면 검색하지 않는다", example = "미용")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "다음 페이지 커서. 첫 요청은 비우고, 응답의 nextCursor 를 그대로 넣는다")
            @RequestParam(required = false) String cursor
    ) {
        PlaceResponse.page response = placeService.getNearby(
                principal.getUserId(), lat, lng, radiusKm, categories, keyword, cursor);
        return ResponseEntity.ok(ApiResponse.success("주변 매장 목록 조회 완료", response));
    }
}
