package com.kkodong.server.domain.place.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkodong.server.domain.merchant.domain.BusinessHour;
import com.kkodong.server.domain.place.domain.PlaceCursor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 견주 지도의 매장 조회 전용 레이어 (MAP-01, API_SPEC 14절).
 *
 * <p><b>왜 {@code MerchantRepository} 에 메서드를 더하지 않나</b>: 매장 엔티티와 리포지터리는
 * 점주 도메인 소유다. 견주 지도는 그 행을 읽기만 하고, 거리·친구 수처럼 점주 쪽에 없는
 * 계산이 붙는다. 조회 모델을 따로 두면 점주 쪽 파일을 건드리지 않는다
 * (같은 판단: {@code MyKindergartenQueryRepository}).
 *
 * <p><b>노출 조건은 SQL 에 박는다</b> — {@code status = 'active'} 와 좌표 있음. 서비스에서
 * 거르면 "조건을 빠뜨린 조회"가 언젠가 생긴다. 대기·정지 매장이 견주 지도에 뜨는 것은
 * 사고다(14.1).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlaceQueryRepository {

    private static final TypeReference<List<BusinessHour>> BUSINESS_HOURS = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /**
     * @param categories 비어 있으면 전 업종. 값은 이미 소문자로 정리돼 있어야 한다
     * @param keyword    null 이면 검색하지 않는다. 앞뒤 공백은 이미 잘려 있어야 한다
     * @param after      null 이면 첫 페이지
     * @param limit      이번에 가져올 최대 행 수
     */
    public record NearbyQuery(UUID userId, double lat, double lng, double radiusMeters,
                              Set<String> categories, String keyword, PlaceCursor after, int limit) {
    }

    /** DB 값 그대로의 한 행. 표시용 가공(거리 반올림·영업 중 판정)은 서비스가 한다. */
    public record PlaceRow(UUID id, String name, String category, String address, String phone,
                           double lat, double lng, String thumbnailUrl,
                           List<BusinessHour> businessHours, List<String> closedDates,
                           double distanceMeters, int friendDogCount) {
    }

    /**
     * 반경 안의 노출 가능한 매장을 가까운 순(동률은 id 순)으로 가져온다.
     *
     * <p>{@code friend_dog_count}: 내 친구 견주의 강아지 중 이 매장에 재원(active) 중인 수.
     * 친구 관계는 견주 쌍 한 행에 한쪽이 {@code user_a}, 다른 쪽이 {@code user_b} 로 저장되므로
     * 양방향을 다 본다. 같은 강아지가 한 매장에 두 번 셀 일은 없지만(재원 유니크 인덱스)
     * 의미를 분명히 하려고 {@code DISTINCT} 로 센다.
     */
    public List<PlaceRow> findNearby(NearbyQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", query.userId())
                .addValue("lat", query.lat())
                .addValue("lng", query.lng())
                .addValue("radiusMeters", query.radiusMeters())
                .addValue("limit", query.limit());

        // 선택 조건은 고정된 SQL 조각만 이어 붙이고, 값은 전부 바인딩한다.
        StringBuilder filters = new StringBuilder();
        if (!query.categories().isEmpty()) {
            filters.append("           AND m.merchant_type IN (:categories)\n");
            params.addValue("categories", query.categories());
        }
        if (query.keyword() != null) {
            filters.append("           AND m.name ILIKE :keywordPattern ESCAPE '\\'\n");
            params.addValue("keywordPattern", "%" + escapeLike(query.keyword()) + "%");
        }

        String afterCursor = "";
        if (query.after() != null) {
            afterCursor = " WHERE (p.distance_meters, p.id) > (:cursorDistance, :cursorId)\n";
            params.addValue("cursorDistance", query.after().distanceMeters());
            params.addValue("cursorId", query.after().id());
        }

        String sql = """
                SELECT p.*,
                       (SELECT count(DISTINCT e.dog_id)
                          FROM enrollments e
                          JOIN dogs d ON d.id = e.dog_id
                          JOIN friendships f
                            ON (f.user_a_id = :userId AND f.user_b_id = d.owner_id)
                            OR (f.user_b_id = :userId AND f.user_a_id = d.owner_id)
                         WHERE e.merchant_id = p.id
                           AND e.status = 'active') AS friend_dog_count
                  FROM (
                        SELECT m.id,
                               m.name,
                               m.merchant_type,
                               m.address,
                               m.phone,
                               ST_Y(m.location::geometry) AS lat,
                               ST_X(m.location::geometry) AS lng,
                               m.image_urls ->> 0         AS thumbnail_url,
                               m.business_hours::text     AS business_hours_json,
                               m.closed_dates::text       AS closed_dates_json,
                               ST_Distance(m.location,
                                           ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_meters
                          FROM merchants m
                         WHERE m.status = 'active'
                           AND m.location IS NOT NULL
                           AND ST_DWithin(m.location,
                                          ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                                          :radiusMeters)
                """ + filters + """
                       ) p
                """ + afterCursor + """
                 ORDER BY p.distance_meters, p.id
                 LIMIT :limit
                """;

        return jdbc.query(sql, params, this::mapRow);
    }

    private PlaceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new PlaceRow(
                id,
                rs.getString("name"),
                rs.getString("merchant_type"),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getDouble("lat"),
                rs.getDouble("lng"),
                rs.getString("thumbnail_url"),
                readList(rs.getString("business_hours_json"), BUSINESS_HOURS, id, "business_hours"),
                readList(rs.getString("closed_dates_json"), STRINGS, id, "closed_dates"),
                rs.getDouble("distance_meters"),
                rs.getInt("friend_dog_count")
        );
    }

    /**
     * JSONB 목록을 읽는다. <b>모양이 틀리면 빈 목록</b>으로 본다 — 점주 입력 경로에 검증이 없어
     * 이상한 값이 들어올 수 있고, 한 매장 때문에 지도 목록 전체가 500 이 되면 안 된다.
     * 빈 영업시간은 "영업 중 모름(null)"으로 이어진다.
     */
    private <T> List<T> readList(String json, TypeReference<List<T>> type, UUID merchantId, String column) {
        if (json == null) {
            return List.of();
        }
        try {
            List<T> values = objectMapper.readValue(json, type);
            return values == null ? List.of() : values;
        } catch (JsonProcessingException e) {
            log.warn("merchants.{} 를 읽을 수 없어 빈 목록으로 본다. merchantId={}", column, merchantId);
            return List.of();
        }
    }

    /** LIKE 패턴의 특수문자를 글자 그대로 찾게 한다. 백슬래시를 가장 먼저 바꿔야 이중 처리가 안 된다. */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
