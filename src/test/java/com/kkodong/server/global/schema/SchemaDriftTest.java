package com.kkodong.server.global.schema;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 스키마와 JPA 엔티티 매핑이 벌어졌는지 검사한다.
 *
 * <p><b>왜 필요한가</b>: Hibernate의 {@code ddl-auto: validate}는 <b>엔티티 → 테이블</b>
 * 단방향만 본다. 엔티티가 참조하는 컬럼이 DB에 없으면 기동이 실패하지만,
 * 반대로 <b>DB에만 있고 엔티티가 모르는 컬럼은 조용히 통과</b>한다.
 * 마이그레이션으로 컬럼을 추가하고 엔티티 반영을 잊어도 아무도 알려주지 않는다는 뜻이다.
 * 실제로 {@code users.home_location}(V2, 2026-07-19 추가)이 한 달 넘게 미매핑으로 방치됐고,
 * 그건 친구 추천 점수의 35점짜리 최대 신호였다.
 *
 * <p><b>설계 의도</b>: "미매핑 컬럼이 없어야 한다"가 아니라
 * <b>"미매핑 컬럼 집합이 허용목록과 정확히 같아야 한다"</b>를 검증한다. 그래서
 * <ul>
 *   <li>컬럼을 추가하고 엔티티에 안 넣으면 → 실패. 매핑하거나 <b>사유와 함께 허용목록에 적어야</b> 한다</li>
 *   <li>허용목록의 컬럼을 나중에 매핑하면 → 실패. 낡은 허용목록 항목을 지우게 강제한다</li>
 * </ul>
 * 즉 "까먹었다"를 "결정했다"로 바꾸는 것이 목적이다. 허용목록 자체가 문서 역할을 한다.
 *
 * <p><b>전제</b>: {@code local} 프로파일 기준으로 실행되며 로컬 Postgres(5433)가 떠 있어야 한다
 * ({@code docker compose up -d postgres}). CI에서 돌리려면 Testcontainers 도입이 필요하다.
 */
@SpringBootTest
@ActiveProfiles("local")
class SchemaDriftTest {

    /**
     * 의도적으로 매핑하지 않은 컬럼. <b>반드시 사유를 남긴다.</b>
     * 해당 컬럼을 엔티티에 매핑하는 순간 여기서도 지워야 테스트가 통과한다.
     */
    private static final Map<String, Set<String>> INTENTIONALLY_UNMAPPED = Map.of(
            "users", Set.of(
                    // 소셜/전화 인증 방식 미정 (QUESTIONS.md Q7). 계약이 정해지면 매핑한다.
                    "kakao_user_id", "google_user_id", "phone_number", "phone_verified_at",

                    // PASS-1 착수 시 매핑 예정. 반려견 등록 마릿수 제한 검증에 필요.
                    "subscription_tier",

                    // DB DEFAULT now()가 관리. Dog은 매핑돼 있어 스타일이 갈렸다 —
                    // 통일 여부는 별도 판단(둘 다 정당한 선택).
                    "created_at", "updated_at"
            ),
            "dogs", Set.of()
    );

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("DB에만 있고 엔티티가 모르는 컬럼은 허용목록과 정확히 일치해야 한다")
    void unmappedColumnsMatchAllowlist() {
        Map<String, Set<String>> mapped = mappedColumnsByTable();

        Map<String, Set<String>> actualUnmapped = new TreeMap<>();
        mapped.forEach((table, mappedCols) -> {
            Set<String> diff = new TreeSet<>(databaseColumns(table));
            diff.removeAll(mappedCols);
            actualUnmapped.put(table, diff);
        });

        actualUnmapped.forEach((table, unmapped) -> {
            Set<String> allowed = INTENTIONALLY_UNMAPPED.getOrDefault(table, Set.of());
            assertThat(unmapped)
                    .as("""
                        [%s] 미매핑 컬럼이 허용목록과 다릅니다.
                          - 목록에 없는 컬럼이 나왔다면: 엔티티에 매핑하거나, 사유를 적어 \
                        INTENTIONALLY_UNMAPPED에 추가하세요.
                          - 목록에 있는데 사라졌다면: 그 컬럼을 매핑했다는 뜻이니 \
                        INTENTIONALLY_UNMAPPED에서 지우세요.""", table)
                    .containsExactlyInAnyOrderElementsOf(allowed);
        });
    }

    @Test
    @DisplayName("엔티티가 참조하는 컬럼은 모두 DB에 존재해야 한다")
    void mappedColumnsExistInDatabase() {
        mappedColumnsByTable().forEach((table, mappedCols) -> {
            Set<String> missing = new TreeSet<>(mappedCols);
            missing.removeAll(databaseColumns(table));
            assertThat(missing)
                    .as("[%s] 엔티티가 매핑한 컬럼이 DB에 없습니다. "
                        + "@Column(name=...) 오타이거나 마이그레이션이 빠졌습니다.", table)
                    .isEmpty();
        });
    }

    /** Hibernate 매핑 메타모델에서 (테이블 → 매핑된 컬럼) 을 뽑는다. */
    private Map<String, Set<String>> mappedColumnsByTable() {
        SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        MappingMetamodel metamodel = sessionFactory.getMappingMetamodel();

        Map<String, Set<String>> result = new TreeMap<>();
        metamodel.forEachEntityDescriptor(persister -> {
            String table = tableNameOf(persister);
            Set<String> columns = result.computeIfAbsent(table, t -> new LinkedHashSet<>());
            persister.forEachSelectable((index, selectable) -> {
                if (!selectable.isFormula()) {
                    columns.add(selectable.getSelectionExpression().toLowerCase());
                }
            });
            persister.getIdentifierMapping().forEachSelectable((index, selectable) -> {
                if (!selectable.isFormula()) {
                    columns.add(selectable.getSelectionExpression().toLowerCase());
                }
            });
        });
        return result;
    }

    private String tableNameOf(EntityPersister persister) {
        return persister.getMappedTableDetails().getTableName().toLowerCase();
    }

    /** information_schema에서 실제 DB 컬럼을 읽는다. */
    private Set<String> databaseColumns(String table) {
        String sql = """
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                """;
        Set<String> columns = new TreeSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name").toLowerCase());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("컬럼 조회 실패: " + table, e);
        }
        assertThat(columns).as("[%s] 테이블이 DB에 없습니다.", table).isNotEmpty();
        return columns;
    }
}
