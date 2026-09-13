package com.kkodong.server.domain.place.repository;

import com.kkodong.server.domain.merchant.domain.BusinessHour;
import com.kkodong.server.domain.place.domain.PlaceCursor;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository.NearbyQuery;
import com.kkodong.server.domain.place.repository.PlaceQueryRepository.PlaceRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 견주 지도 매장 조회의 <b>실제 SQL</b> 을 검증한다 (MAP-01, API_SPEC 14절).
 *
 * <p><b>왜 실제 DB 인가</b>: 반경(PostGIS)·JSONB·친구 관계 조인·커서 비교가 전부 SQL 안에
 * 있다. 목(mock)으로는 이 중 어느 것도 검증되지 않고, 컬럼 이름 하나만 틀려도 컴파일은 통과한다.
 *
 * <p><b>데이터 격리</b>: 테스트 트랜잭션으로 감싸 끝나면 되돌린다. 매장 좌표는 서해 한가운데로
 * 잡아 로컬 DB 의 시드 매장이나 다른 테스트 데이터와 반경이 겹치지 않게 한다.
 *
 * <p><b>전제</b>: SchemaDriftTest 와 같다 — {@code local} 프로파일, 로컬 Postgres(5433).
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PlaceQueryRepositoryTest {

    private static final double BASE_LAT = 34.0;
    private static final double BASE_LNG = 124.0;
    /** 위도 1도의 대략적인 거리. 테스트 좌표를 "북쪽으로 N 미터"로 놓는 데만 쓴다. */
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    private static final double RADIUS_3KM = 3_000;

    @Autowired
    private PlaceQueryRepository repository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private UUID me;

    @BeforeEach
    void setUp() {
        me = insertUser();
    }

    // ── 조회 헬퍼 ────────────────────────────────────────────────────────

    private List<PlaceRow> nearby(double radiusMeters, Set<String> categories, String keyword,
                                  PlaceCursor after, int limit) {
        return repository.findNearby(new NearbyQuery(
                me, BASE_LAT, BASE_LNG, radiusMeters, categories, keyword, after, limit));
    }

    private List<PlaceRow> nearby(double radiusMeters) {
        return nearby(radiusMeters, Set.of(), null, null, 50);
    }

    private static List<UUID> ids(List<PlaceRow> rows) {
        return rows.stream().map(PlaceRow::id).toList();
    }

    private static Map<UUID, PlaceRow> byId(List<PlaceRow> rows) {
        return rows.stream().collect(Collectors.toMap(PlaceRow::id, Function.identity()));
    }

    private static double north(double meters) {
        return BASE_LAT + meters / METERS_PER_DEGREE_LAT;
    }

    // ── 테스트 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("반경 안의 active 매장만, 가까운 순으로 나온다 — 대기·정지·폐업·좌표 없음·반경 밖은 빠진다")
    void onlyActiveStoresWithinRadiusNearestFirst() {
        UUID near = activeStore("가까운곳", "kindergarten", 100);
        UUID middle = activeStore("중간", "grooming", 500);
        UUID far = activeStore("먼곳", "clinic", 2_000);
        insertStore("대기매장", "kindergarten", "pending", 200.0, "[]", "[]", "[]");
        insertStore("정지매장", "kindergarten", "suspended", 300.0, "[]", "[]", "[]");
        insertStore("폐업매장", "kindergarten", "closed", 400.0, "[]", "[]", "[]");
        insertStore("좌표없음", "kindergarten", "active", null, "[]", "[]", "[]");
        activeStore("반경밖", "kindergarten", 20_000);

        assertThat(ids(nearby(RADIUS_3KM))).containsExactly(near, middle, far);
    }

    @Test
    @DisplayName("업종을 주면 그 업종만, 비어 있으면 전 업종이 나온다. 없는 업종 값은 아무것도 걸리지 않는다")
    void filtersByCategories() {
        UUID kindergarten = activeStore("유치원", "kindergarten", 100);
        UUID grooming = activeStore("미용실", "grooming", 200);
        UUID clinic = activeStore("병원", "clinic", 300);

        assertThat(ids(nearby(RADIUS_3KM, Set.of("grooming", "clinic"), null, null, 50)))
                .containsExactly(grooming, clinic);
        assertThat(ids(nearby(RADIUS_3KM, Set.of(), null, null, 50)))
                .containsExactly(kindergarten, grooming, clinic);
        assertThat(nearby(RADIUS_3KM, Set.of("cafe"), null, null, 50)).isEmpty();
    }

    @Test
    @DisplayName("검색어는 상호 부분 일치이고, % 와 _ 는 와일드카드가 아니라 글자 그대로 찾는다")
    void keywordIsPartialMatchWithEscapedWildcards() {
        UUID kindergarten = activeStore("댕댕유치원 성수점", "kindergarten", 100);
        UUID percent = activeStore("100%미용", "grooming", 200);
        UUID underscore = activeStore("멍_멍병원", "clinic", 300);
        activeStore("멍X멍병원", "clinic", 400);
        UUID english = activeStore("Happy Dog", "grooming", 500);

        assertThat(ids(nearby(RADIUS_3KM, Set.of(), "유치", null, 50))).containsExactly(kindergarten);
        assertThat(ids(nearby(RADIUS_3KM, Set.of(), "%", null, 50))).containsExactly(percent);
        assertThat(ids(nearby(RADIUS_3KM, Set.of(), "_", null, 50))).containsExactly(underscore);
        assertThat(ids(nearby(RADIUS_3KM, Set.of(), "dog", null, 50))).containsExactly(english);
    }

    @Test
    @DisplayName("커서로 끝까지 넘기면 한 번에 조회한 순서와 똑같다 — 거리가 같은 매장도 빠지거나 겹치지 않는다")
    void cursorPagesWithoutGapsOrDuplicates() {
        activeStore("가", "kindergarten", 100);
        activeStore("나", "grooming", 200);
        activeStore("다1", "clinic", 300);   // 다1·다2·다3 은 같은 자리 — 거리 동률
        activeStore("다2", "clinic", 300);
        activeStore("다3", "clinic", 300);
        activeStore("라", "kindergarten", 400);

        List<UUID> all = ids(nearby(RADIUS_3KM));
        assertThat(all).hasSize(6);

        List<UUID> paged = new ArrayList<>();
        PlaceCursor after = null;
        for (int page = 0; page < 10; page++) {
            List<PlaceRow> rows = nearby(RADIUS_3KM, Set.of(), null, after, 2);
            if (rows.isEmpty()) {
                break;
            }
            paged.addAll(ids(rows));
            PlaceRow last = rows.get(rows.size() - 1);
            // 앱이 실제로 하듯 문자열로 바꿨다가 되돌린 커서로 다음 페이지를 부른다.
            after = PlaceCursor.decode(new PlaceCursor(last.distanceMeters(), last.id()).encode());
        }

        assertThat(paged).containsExactlyElementsOf(all);
    }

    @Test
    @DisplayName("좌표·대표 사진·영업시간·휴무일·업종·주소가 그대로 읽힌다. 사진이 없으면 대표 사진은 null")
    void mapsColumns() {
        UUID withPhotos = insertStore("사진있는곳", "grooming", "active", 100.0,
                "[\"https://cdn/1.jpg\", \"https://cdn/2.jpg\"]",
                "[{\"day\":\"mon\",\"open\":\"09:00\",\"close\":\"18:00\"}]",
                "[\"2026-09-15\"]");
        UUID noPhotos = activeStore("사진없는곳", "clinic", 200);

        Map<UUID, PlaceRow> rows = byId(nearby(RADIUS_3KM));
        PlaceRow row = rows.get(withPhotos);

        assertThat(row.name()).isEqualTo("사진있는곳");
        assertThat(row.category()).isEqualTo("grooming");
        assertThat(row.address()).isEqualTo("서울 테스트구 1");
        assertThat(row.lat()).isCloseTo(north(100), within(1e-9));
        assertThat(row.lng()).isCloseTo(BASE_LNG, within(1e-9));
        assertThat(row.distanceMeters()).isCloseTo(100, within(2.0));
        assertThat(row.thumbnailUrl()).isEqualTo("https://cdn/1.jpg");
        assertThat(row.businessHours()).containsExactly(new BusinessHour("mon", "09:00", "18:00"));
        assertThat(row.closedDates()).containsExactly("2026-09-15");

        assertThat(rows.get(noPhotos).thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("영업시간 JSON 모양이 틀린 매장도 목록에서 빠지지 않고, 영업시간만 빈 목록으로 읽힌다")
    void malformedBusinessHoursDoNotBreakTheList() {
        UUID broken = insertStore("이상한값", "clinic", "active", 100.0,
                "[]", "\"not an array\"", "[]");

        PlaceRow row = byId(nearby(RADIUS_3KM)).get(broken);

        assertThat(row).isNotNull();
        assertThat(row.businessHours()).isEmpty();
    }

    @Test
    @DisplayName("친구 강아지 수는 친구 견주의 강아지 중 이 매장에 재원 중인 수다 — 퇴원·남의 강아지·내 강아지는 세지 않는다")
    void countsActiveDogsOfFriendsEnrolledHere() {
        UUID store = activeStore("친구많은유치원", "kindergarten", 100);
        UUID otherStore = activeStore("다른유치원", "kindergarten", 200);

        UUID myDog = insertDog(me);

        // 친구 A — 친구 관계 행에서 내가 user_a 쪽
        UUID friendA = insertUser();
        UUID a1 = insertDog(friendA);
        UUID a2 = insertDog(friendA);
        UUID a3 = insertDog(friendA);
        befriend(me, myDog, friendA, a1);
        enroll(store, a1, "active");
        enroll(store, a2, "active");
        enroll(store, a3, "withdrawn");      // 퇴원 — 세지 않는다

        // 친구 B — 친구 관계 행에서 내가 user_b 쪽
        UUID friendB = insertUser();
        UUID b1 = insertDog(friendB);
        UUID b2 = insertDog(friendB);
        befriend(friendB, b1, me, myDog);
        enroll(store, b1, "active");
        enroll(otherStore, b2, "active");    // 다른 매장

        // 친구 아님
        UUID stranger = insertUser();
        enroll(store, insertDog(stranger), "active");

        // 내 강아지 — 친구의 강아지가 아니다
        enroll(store, myDog, "active");

        Map<UUID, PlaceRow> rows = byId(nearby(RADIUS_3KM));
        assertThat(rows.get(store).friendDogCount()).isEqualTo(3);        // a1, a2, b1
        assertThat(rows.get(otherStore).friendDogCount()).isEqualTo(1);   // b2
    }

    // ── 데이터 준비 헬퍼 (네이티브 INSERT — 이 테스트의 관심사는 조회 SQL 이다) ──────────

    private UUID activeStore(String name, String type, double metersNorth) {
        return insertStore(name, type, "active", metersNorth, "[]", "[]", "[]");
    }

    private UUID insertStore(String name, String type, String status, Double metersNorth,
                             String imageUrlsJson, String businessHoursJson, String closedDatesJson) {
        UUID id = UUID.randomUUID();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("type", type)
                .addValue("name", name)
                .addValue("brn", uniqueBusinessRegistrationNumber())
                .addValue("status", status)
                .addValue("images", imageUrlsJson)
                .addValue("hours", businessHoursJson)
                .addValue("closed", closedDatesJson);

        // 좌표가 없는 매장은 location 자리에 NULL 을 그대로 쓴다.
        // 파라미터로 null 을 넘기면 Postgres 가 타입을 추론하지 못한다.
        String location = "NULL";
        if (metersNorth != null) {
            location = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography";
            params.addValue("lat", north(metersNorth)).addValue("lng", BASE_LNG);
        }

        jdbc.update("""
                INSERT INTO merchants (id, merchant_type, name, business_registration_number, representative_name,
                                       status, location, image_urls, business_hours, closed_dates, address)
                VALUES (:id, :type, :name, :brn, '테스트대표', :status, %s,
                        CAST(:images AS jsonb), CAST(:hours AS jsonb), CAST(:closed AS jsonb), '서울 테스트구 1')
                """.formatted(location), params);
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash) VALUES (:id, :email, 'x')",
                new MapSqlParameterSource().addValue("id", id).addValue("email", id + "@test.local"));
        return id;
    }

    private UUID insertDog(UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dogs (id, owner_id, name) VALUES (:id, :owner, '테스트견')",
                new MapSqlParameterSource().addValue("id", id).addValue("owner", ownerId));
        return id;
    }

    private void enroll(UUID merchantId, UUID dogId, String status) {
        jdbc.update("""
                INSERT INTO enrollments (merchant_id, dog_id, dog_name_snapshot, status)
                VALUES (:merchant, :dog, '테스트견', :status)
                """, new MapSqlParameterSource()
                .addValue("merchant", merchantId)
                .addValue("dog", dogId)
                .addValue("status", status));
    }

    private void befriend(UUID userA, UUID dogA, UUID userB, UUID dogB) {
        jdbc.update("""
                INSERT INTO friendships (user_a_id, user_b_id, dog_a_id, dog_b_id)
                VALUES (:userA, :userB, :dogA, :dogB)
                """, new MapSqlParameterSource()
                .addValue("userA", userA).addValue("userB", userB)
                .addValue("dogA", dogA).addValue("dogB", dogB));
    }

    /** 사업자등록번호가 UNIQUE 라 매장마다 다른 값이 필요하다. */
    private static String uniqueBusinessRegistrationNumber() {
        return String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }
}
