package com.kkodong.server.domain.place.service;

import com.kkodong.server.domain.merchant.domain.BusinessHour;
import com.kkodong.server.domain.place.config.PlaceProperties;
import com.kkodong.server.domain.place.domain.PlaceCursor;
import com.kkodong.server.domain.place.dto.PlaceResponse;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository.NearbyQuery;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository.PlaceRow;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

/**
 * 주변 매장 목록의 요청 해석과 응답 가공 (API_SPEC 14.2).
 *
 * <p>SQL 은 {@code PlaceQueryRepositoryTest} 가 실제 DB 로 본다. 여기서는 그 앞뒤 —
 * 앱이 보내는 값을 어떻게 읽고, DB 값을 앱에 어떻게 보이는지 — 를 고정한다.
 * 페이지 크기는 2 로 줄여 "다음 페이지" 경계를 적은 데이터로 확인한다.
 */
class PlaceServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 2026-09-14(월) 12:00 한국 시간 = 같은 날 03:00 UTC. 시간대를 잘못 쓰면 영업 판정이 뒤집힌다. */
    private static final Clock MONDAY_NOON_KST = Clock.fixed(
            LocalDateTime.of(2026, 9, 14, 12, 0).atZone(SEOUL).toInstant(), SEOUL);

    private static final int PAGE_SIZE = 2;

    private final PlaceQueryRepository repository = Mockito.mock(PlaceQueryRepository.class);
    private final PlaceService service =
            new PlaceService(repository, new PlaceProperties(3, 10, PAGE_SIZE), MONDAY_NOON_KST);

    private final UUID me = UUID.randomUUID();

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private PlaceResponse.page call(Double lat, Double lng, Double radiusKm,
                                    String categories, String keyword, String cursor) {
        return service.getNearby(me, lat, lng, radiusKm, categories, keyword, cursor);
    }

    private PlaceResponse.page callWithDefaults() {
        return call(37.5605, 126.9237, null, null, null, null);
    }

    private NearbyQuery capturedQuery() {
        ArgumentCaptor<NearbyQuery> captor = ArgumentCaptor.forClass(NearbyQuery.class);
        Mockito.verify(repository).findNearby(captor.capture());
        return captor.getValue();
    }

    private void givenRows(PlaceRow... rows) {
        Mockito.when(repository.findNearby(any())).thenReturn(List.of(rows));
    }

    private static PlaceRow row(double distanceMeters) {
        return row(distanceMeters, List.of());
    }

    private static PlaceRow row(double distanceMeters, List<BusinessHour> hours) {
        return new PlaceRow(UUID.randomUUID(), "매장", "grooming", "서울 마포구 망원로 1", null,
                37.56, 126.92, null, hours, List.of(), distanceMeters, 0);
    }

    private void assertBadRequest(Executable call) {
        BusinessException e = assertThrows(BusinessException.class, call);
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ── 요청 해석 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("반경을 안 보내면 기본 3km, 한 건 더(페이지 크기 + 1) 요청한다")
    void defaultRadiusAndOneExtraRow() {
        givenRows();

        callWithDefaults();

        NearbyQuery query = capturedQuery();
        assertThat(query.userId()).isEqualTo(me);
        assertThat(query.lat()).isEqualTo(37.5605);
        assertThat(query.lng()).isEqualTo(126.9237);
        assertThat(query.radiusMeters()).isEqualTo(3_000);
        assertThat(query.limit()).isEqualTo(PAGE_SIZE + 1);
        assertThat(query.categories()).isEmpty();
        assertThat(query.keyword()).isNull();
        assertThat(query.after()).isNull();
    }

    @Test
    @DisplayName("반경이 상한을 넘으면 에러가 아니라 10km 로 잘린다")
    void radiusIsClampedToMax() {
        givenRows();

        call(37.5605, 126.9237, 25.0, null, null, null);

        assertThat(capturedQuery().radiusMeters()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("위경도가 없거나 범위 밖이거나, 반경이 0 이하·숫자가 아니면 400(CM001)이고 조회하지 않는다")
    void invalidInputsAreBadRequest() {
        assertBadRequest(() -> call(null, 126.9, null, null, null, null));
        assertBadRequest(() -> call(37.5, null, null, null, null, null));
        assertBadRequest(() -> call(90.1, 126.9, null, null, null, null));
        assertBadRequest(() -> call(-90.1, 126.9, null, null, null, null));
        assertBadRequest(() -> call(37.5, 180.1, null, null, null, null));
        assertBadRequest(() -> call(37.5, -180.1, null, null, null, null));
        assertBadRequest(() -> call(Double.NaN, 126.9, null, null, null, null));
        assertBadRequest(() -> call(37.5, 126.9, 0.0, null, null, null));
        assertBadRequest(() -> call(37.5, 126.9, -1.0, null, null, null));
        assertBadRequest(() -> call(37.5, 126.9, Double.NaN, null, null, null));

        Mockito.verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("업종은 쉼표로 나눠 공백·대소문자를 정리하고 빈 조각과 중복을 버린다")
    void categoriesAreNormalized() {
        givenRows();

        call(37.5605, 126.9237, null, " Grooming, ,clinic ,GROOMING", null, null);

        assertThat(capturedQuery().categories()).containsExactly("grooming", "clinic");
    }

    @Test
    @DisplayName("업종이 공백·쉼표뿐이면 전 업종으로 본다")
    void blankCategoriesMeanAll() {
        givenRows();

        call(37.5605, 126.9237, null, " , ", null, null);

        assertThat(capturedQuery().categories()).isEmpty();
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 자르고, 공백뿐이면 검색하지 않는다 — 빈 검색으로 지도가 비면 안 된다")
    void keywordIsTrimmedAndBlankIsIgnored() {
        givenRows();
        call(37.5605, 126.9237, null, null, "  미용 ", null);
        assertThat(capturedQuery().keyword()).isEqualTo("미용");

        Mockito.reset(repository);
        givenRows();
        call(37.5605, 126.9237, null, null, "   ", null);
        assertThat(capturedQuery().keyword()).isNull();
    }

    @Test
    @DisplayName("커서는 해석해서 조회에 넘기고, 형식이 틀리면 400 이다")
    void cursorIsDecodedOrRejected() {
        UUID id = UUID.randomUUID();
        givenRows();

        call(37.5605, 126.9237, null, null, null, new PlaceCursor(812.5, id).encode());

        assertThat(capturedQuery().after()).isEqualTo(new PlaceCursor(812.5, id));
        assertBadRequest(() -> call(37.5605, 126.9237, null, null, null, "not-a-cursor"));
    }

    // ── 응답 가공 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("페이지 크기보다 한 건 더 오면 그 한 건은 빼고, 마지막으로 보여준 매장 다음부터 이어지는 커서를 준다")
    void nextCursorWhenMoreRowsExist() {
        PlaceRow first = row(100);
        PlaceRow second = row(200);
        givenRows(first, second, row(300));

        PlaceResponse.page page = callWithDefaults();

        assertThat(page.items()).extracting(PlaceResponse.summary::id).containsExactly(first.id(), second.id());
        assertThat(PlaceCursor.decode(page.nextCursor())).isEqualTo(new PlaceCursor(200, second.id()));
    }

    @Test
    @DisplayName("페이지 크기 이하로 오면 마지막 페이지라 커서는 null 이다")
    void noCursorOnLastPage() {
        givenRows(row(100), row(200));
        assertThat(callWithDefaults().nextCursor()).isNull();

        Mockito.reset(repository);
        givenRows();
        PlaceResponse.page empty = callWithDefaults();
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nextCursor()).isNull();
    }

    @Test
    @DisplayName("거리는 정수 km 로 반올림한다 — 499m 는 0, 500m 는 1, 1499m 는 1, 1500m 는 2")
    void distanceIsRoundedToKm() {
        givenRows(row(499));
        assertThat(callWithDefaults().items().get(0).distanceKm()).isZero();
        Mockito.reset(repository);
        givenRows(row(500));
        assertThat(callWithDefaults().items().get(0).distanceKm()).isEqualTo(1);
        Mockito.reset(repository);
        givenRows(row(1_499));
        assertThat(callWithDefaults().items().get(0).distanceKm()).isEqualTo(1);
        Mockito.reset(repository);
        givenRows(row(1_500));
        assertThat(callWithDefaults().items().get(0).distanceKm()).isEqualTo(2);
    }

    @Test
    @DisplayName("영업 중 판정은 한국 시간 기준이다 — 월 12:00 KST 는 UTC 로 03:00 이라 시간대를 틀리면 답이 뒤집힌다")
    void openNowUsesKoreanTime() {
        givenRows(
                row(100, List.of(new BusinessHour("mon", "09:00", "18:00"))),  // KST 12:00 → 영업 중
                row(200, List.of(new BusinessHour("mon", "00:00", "09:00"))),  // UTC 03:00 이었다면 영업 중
                row(300, List.of()));                                           // 영업시간 없음

        // 페이지 크기 2 라 세 번째 매장은 다음 페이지로 넘어간다. 첫 두 매장만 본다.
        List<PlaceResponse.summary> items = callWithDefaults().items();

        assertThat(items.get(0).isOpenNow()).isTrue();
        assertThat(items.get(1).isOpenNow()).isFalse();
    }

    @Test
    @DisplayName("영업시간이 입력되지 않은 매장은 isOpenNow 가 null 이다")
    void openNowIsNullWithoutHours() {
        givenRows(row(100, List.of()));

        assertThat(callWithDefaults().items().get(0).isOpenNow()).isNull();
    }

    @Test
    @DisplayName("DB 값은 계약 필드에 그대로 옮긴다")
    void mapsRowToSummary() {
        UUID id = UUID.randomUUID();
        givenRows(new PlaceRow(id, "망원 댕댕유치원", "kindergarten", "서울 마포구 망원로 12, 1층", "02-336-1234",
                37.5632, 126.9237, "https://cdn/1.jpg", List.of(), List.of(), 312.0, 2));

        PlaceResponse.summary item = callWithDefaults().items().get(0);

        assertThat(item.id()).isEqualTo(id);
        assertThat(item.name()).isEqualTo("망원 댕댕유치원");
        assertThat(item.category()).isEqualTo("kindergarten");
        assertThat(item.address()).isEqualTo("서울 마포구 망원로 12, 1층");
        assertThat(item.phone()).isEqualTo("02-336-1234");
        assertThat(item.lat()).isEqualTo(37.5632);
        assertThat(item.lng()).isEqualTo(126.9237);
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn/1.jpg");
        assertThat(item.distanceKm()).isZero();
        assertThat(item.friendDogCount()).isEqualTo(2);
    }
}
