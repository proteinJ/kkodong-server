package com.kkodong.server.domain.place.service;

import com.kkodong.server.domain.place.config.PlaceProperties;
import com.kkodong.server.domain.place.domain.OpeningHours;
import com.kkodong.server.domain.place.domain.PlaceCursor;
import com.kkodong.server.domain.place.dto.PlaceResponse;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository.NearbyQuery;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository.PlaceRow;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 견주 지도 — 주변 매장 목록 (MAP-01, API_SPEC 14.2).
 *
 * <p>조회는 {@link PlaceQueryRepository} 가 하고, 이 클래스는 요청 해석과 응답 가공을 맡는다.
 * 요청 쪽은 검증·반경 자르기·업종/검색어 정리·커서 해석, 응답 쪽은 거리 반올림·영업 중 판정·
 * 다음 페이지 커서다.
 */
@Service
@Transactional(readOnly = true)
public class PlaceService {

    /**
     * 매장 시간대. {@code merchants} 에 시간대 칸이 없어 국내 단일 시간대로 고정한다
     * (KG-09 {@code MyKindergartenService}, 점주 {@code ReservationService} 와 같은 판단).
     */
    static final ZoneId MERCHANT_ZONE = ZoneId.of("Asia/Seoul");

    private final PlaceQueryRepository queryRepository;
    private final PlaceProperties properties;

    /**
     * "지금"의 출처. 영업 중 판정이 여기 걸려 있어, 시각을 고정하지 않으면 테스트가
     * 실행 시각에 따라 통과하거나 실패한다.
     */
    private final Clock clock;

    /**
     * 운영용. <b>{@code @Autowired} 를 빼면 기동이 깨진다</b> — 생성자가 둘이면 Spring 이 어느 것을
     * 쓸지 모른다(KG-09 에서 실제로 CI 가 이것으로 실패했다).
     */
    @Autowired
    public PlaceService(PlaceQueryRepository queryRepository, PlaceProperties properties) {
        this(queryRepository, properties, Clock.system(MERCHANT_ZONE));
    }

    /** 테스트용 — "지금"을 고정한다. */
    PlaceService(PlaceQueryRepository queryRepository, PlaceProperties properties, Clock clock) {
        this.queryRepository = queryRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param lat        필수. −90~90
     * @param lng        필수. −180~180
     * @param radiusKm   없으면 기본값, 상한을 넘으면 상한으로 자른다. 0 이하는 400
     * @param categories 쉼표 구분. 비어 있으면 전 업종
     * @param keyword    비어 있으면 검색하지 않는다
     * @param cursor     비어 있으면 첫 페이지. 형식이 틀리면 400
     */
    public PlaceResponse.page getNearby(UUID userId, Double lat, Double lng, Double radiusKm,
                                        String categories, String keyword, String cursor) {
        requireCoordinates(lat, lng);
        double radius = resolveRadiusKm(radiusKm);
        int pageSize = properties.pageSize();

        // 한 건 더 가져와서 다음 페이지가 있는지 본다. 전체 개수를 세는 쿼리를 따로 날리지 않는다.
        List<PlaceRow> rows = queryRepository.findNearby(new NearbyQuery(
                userId, lat, lng, radius * 1_000,
                parseCategories(categories), normalizeKeyword(keyword), parseCursor(cursor),
                pageSize + 1));

        boolean hasNext = rows.size() > pageSize;
        List<PlaceRow> pageRows = hasNext ? rows.subList(0, pageSize) : rows;

        LocalDateTime now = LocalDateTime.now(clock);
        List<PlaceResponse.summary> items = pageRows.stream()
                .map(row -> toSummary(row, now))
                .toList();

        String nextCursor = hasNext ? cursorAfter(pageRows.get(pageRows.size() - 1)) : null;
        return new PlaceResponse.page(items, nextCursor);
    }

    // ── 요청 해석 ────────────────────────────────────────────────────────

    private void requireCoordinates(Double lat, Double lng) {
        if (lat == null || lng == null
                || !Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90
                || lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private double resolveRadiusKm(Double radiusKm) {
        if (radiusKm == null) {
            return properties.defaultRadiusKm();
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return Math.min(radiusKm, properties.maxRadiusKm());
    }

    /**
     * {@code " Grooming, ,clinic"} → {@code [grooming, clinic]}. 빈 조각은 버린다.
     *
     * <p>모르는 업종 값은 에러로 막지 않고 그대로 넘긴다 — 아무 매장에도 걸리지 않으므로
     * 결과가 비는 것이 맞는 답이다. iOS 에는 {@code cafe} 자리가 있어서, 막으면 그 칩이
     * 켜지는 날 지도가 400 으로 깨진다.
     */
    private static Set<String> parseCategories(String categories) {
        if (categories == null || categories.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = Arrays.stream(categories.split(","))
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(parsed);
    }

    /** 빈 문자열을 "아무것도 아닌 것 검색"으로 읽으면 결과가 0 이 되어 지도가 통째로 빈다(14.2). */
    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }

    private static PlaceCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return PlaceCursor.decode(cursor);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // ── 응답 가공 ────────────────────────────────────────────────────────

    private PlaceResponse.summary toSummary(PlaceRow row, LocalDateTime now) {
        return new PlaceResponse.summary(
                row.id(),
                row.name(),
                row.category(),
                row.lat(),
                row.lng(),
                row.address(),
                row.phone(),
                row.thumbnailUrl(),
                toDistanceKm(row.distanceMeters()),
                OpeningHours.of(row.businessHours(), row.closedDates()).isOpenAt(now),
                row.friendDogCount()
        );
    }

    /** 정수 km 로 반올림한다(14.2 — 강아지 추천 3.5절과 같은 규칙). 500m 미만은 0 이다. */
    private static int toDistanceKm(double distanceMeters) {
        return (int) Math.round(distanceMeters / 1_000);
    }

    private static String cursorAfter(PlaceRow last) {
        return new PlaceCursor(last.distanceMeters(), last.id()).encode();
    }
}
