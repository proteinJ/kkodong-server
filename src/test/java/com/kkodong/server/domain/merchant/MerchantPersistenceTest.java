package com.kkodong.server.domain.merchant;

import com.kkodong.server.domain.merchant.domain.*;
import com.kkodong.server.domain.merchant.repository.MerchantRepository;
import com.kkodong.server.domain.merchant.repository.MerchantStaffRepository;
import com.kkodong.server.global.util.Locations;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * merchant 도메인의 <b>실제 DB 왕복</b>을 검증한다.
 *
 * <p><b>왜 SchemaDriftTest로 부족한가</b>: 그 테스트는 Hibernate 메타모델과
 * {@code information_schema}를 대조할 뿐 값을 쓰고 읽지 않는다. 그래서 아래 세 가지가
 * 전부 통과로 잡힌다 — 컬럼 이름은 맞는데 값이 깨지는 경우들이다.
 * <ul>
 *   <li>AttributeConverter 미적용 — {@code @Enumerated}를 실수로 붙이면 ordinal(정수)로
 *       조용히 저장되고, DB의 CHECK 제약에 걸려서야 드러난다</li>
 *   <li>JSONB에 담은 record({@link BusinessHour}) 직렬화 — 타입만으로는 알 수 없다</li>
 *   <li>PostGIS Point의 좌표 순서 — (lng, lat)이 뒤집혀도 저장은 성공한다</li>
 * </ul>
 *
 * <p><b>전제</b>: SchemaDriftTest와 같다 — {@code local} 프로파일, 로컬 Postgres(5433)가
 * 떠 있어야 한다({@code docker compose up -d postgres}).
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class MerchantPersistenceTest {

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantStaffRepository merchantStaffRepository;

    @Autowired
    private EntityManager em;

    /** 사업자등록번호가 UNIQUE라 테스트마다 다른 값이 필요하다. */
    private static String uniqueBrn() {
        return String.valueOf(System.nanoTime()).substring(0, 10);
    }

    /**
     * FK 충족용 유저 한 명. User 엔티티를 거치지 않고 네이티브로 넣는다 —
     * 이 테스트의 관심사는 merchant 도메인이지 회원가입 경로가 아니다.
     */
    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
                        "INSERT INTO users (id, email, password_hash) VALUES (?1, ?2, 'x')")
                .setParameter(1, id)
                .setParameter(2, id + "@test.local")
                .executeUpdate();
        return id;
    }

    @Test
    @DisplayName("매장을 저장하고 다시 읽으면 enum·JSONB·PostGIS 값이 그대로 살아있다")
    void merchantRoundTrip() {
        Merchant saved = merchantRepository.save(Merchant.builder()
                .name("햇살유치원")
                .businessRegistrationNumber(uniqueBrn())
                .representativeName("김원장")
                .location(Locations.of(35.1796, 129.0756)) // 부산시청 (lat, lng)
                .imageUrls(List.of("https://cdn/1.jpg", "https://cdn/2.jpg"))
                .businessHours(List.of(
                        new BusinessHour("mon", "09:00", "19:00"),
                        new BusinessHour("sat", "10:00", "15:00")))
                .closedDates(List.of("2026-09-30"))
                .build());

        // 영속성 컨텍스트를 비워야 캐시가 아니라 실제 DB에서 다시 읽는다.
        em.flush();
        em.clear();

        Merchant found = merchantRepository.findById(saved.getId()).orElseThrow();

        // enum — 컨버터가 소문자 문자열로 오갔는지
        assertThat(found.getMerchantType()).isEqualTo(MerchantType.KINDERGARTEN);
        assertThat(found.getStatus()).isEqualTo(MerchantStatus.PENDING);

        // JSONB에 담은 record가 필드까지 온전한지
        assertThat(found.getBusinessHours())
                .containsExactly(
                        new BusinessHour("mon", "09:00", "19:00"),
                        new BusinessHour("sat", "10:00", "15:00"));
        assertThat(found.getImageUrls()).hasSize(2);
        assertThat(found.getClosedDates()).containsExactly("2026-09-30");

        // ⚠️ PostGIS 좌표 순서. x=lng, y=lat 이 뒤집히면 부산이 남극 근처로 간다.
        assertThat(Locations.latOf(found.getLocation())).isCloseTo(35.1796, within(1e-6));
        assertThat(Locations.lngOf(found.getLocation())).isCloseTo(129.0756, within(1e-6));

        // DB DEFAULT now()가 채우는 값 — insertable=false라 엔티티가 건드리지 않는다
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("진위확인을 통과하면 ACTIVE로 바뀌고 검증 시각이 남는다")
    void verifyActivatesMerchant() {
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .name("검증대기유치원")
                .businessRegistrationNumber(uniqueBrn())
                .representativeName("박원장")
                .build());

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getVerifiedAt()).isNull();

        merchant.verify();

        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        assertThat(merchant.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("원장은 permissions가 비어 있어도 전권을 갖고, 선생님은 부여된 것만 갖는다")
    void directorHasAllPermissions() {
        UUID merchantId = UUID.randomUUID();

        MerchantStaff director = MerchantStaff.builder()
                .merchantId(merchantId).userId(UUID.randomUUID())
                .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE)
                .build();
        MerchantStaff teacher = MerchantStaff.builder()
                .merchantId(merchantId).userId(UUID.randomUUID())
                .role(StaffRole.STAFF).status(StaffStatus.ACTIVE)
                .permissions(List.of("attendance", "daily_note"))
                .build();

        // 원장은 목록이 비어도 전부 통과 — 매장이 잠기지 않게 하는 규칙이다
        assertThat(director.getPermissions()).isEmpty();
        assertThat(director.can("pass")).isTrue();

        assertThat(teacher.can("attendance")).isTrue();
        // FR-PN18-01 — 이용권 변경은 기본 원장 전용이다
        assertThat(teacher.can("pass")).isFalse();
    }

    @Test
    @DisplayName("퇴사하면 권한을 즉시 잃지만 행은 남는다")
    void resignedStaffLosesAccess() {
        MerchantStaff teacher = MerchantStaff.builder()
                .merchantId(UUID.randomUUID()).userId(UUID.randomUUID())
                .role(StaffRole.STAFF).status(StaffStatus.ACTIVE)
                .permissions(List.of("attendance"))
                .build();
        assertThat(teacher.can("attendance")).isTrue();

        teacher.resign();

        assertThat(teacher.can("attendance")).isFalse();
        assertThat(teacher.getResignedAt()).isNotNull();
        // FR-PN18-02 — 과거 작성 이력이 이 행을 참조하므로 지우지 않는다
        assertThat(teacher.getStatus()).isEqualTo(StaffStatus.RESIGNED);
    }

    @Test
    @DisplayName("스태프 권한 조회는 (매장, 유저) 쌍으로 찾는다")
    void findStaffByMerchantAndUser() {
        // merchant_staff는 매장·유저 양쪽에 FK가 걸려 있어 실재하는 행이 필요하다.
        // 임의 UUID를 넣으면 merchant_staff_merchant_id_fkey 위반으로 막힌다.
        UUID merchantId = merchantRepository.save(Merchant.builder()
                .name("소속테스트유치원")
                .businessRegistrationNumber(uniqueBrn())
                .representativeName("이원장")
                .build()).getId();
        UUID userId = insertUser();

        merchantStaffRepository.save(MerchantStaff.builder()
                .merchantId(merchantId).userId(userId)
                .role(StaffRole.DIRECTOR).status(StaffStatus.ACTIVE)
                .build());
        em.flush();
        em.clear();

        assertThat(merchantStaffRepository.findByMerchantIdAndUserId(merchantId, userId))
                .isPresent()
                .get()
                .extracting(MerchantStaff::getRole)
                .isEqualTo(StaffRole.DIRECTOR);

        assertThat(merchantStaffRepository
                .countByMerchantIdAndRoleAndStatus(merchantId, StaffRole.DIRECTOR, StaffStatus.ACTIVE))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("폐기되거나 만료된 초대 코드는 사용할 수 없다")
    void inviteUsability() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();

        MerchantInvite open = MerchantInvite.builder()
                .merchantId(UUID.randomUUID()).code("OPEN").build();
        MerchantInvite expired = MerchantInvite.builder()
                .merchantId(UUID.randomUUID()).code("EXPIRED")
                .expiresAt(now.minusDays(1)).build();
        MerchantInvite revoked = MerchantInvite.builder()
                .merchantId(UUID.randomUUID()).code("REVOKED").build();
        revoked.revoke();

        assertThat(open.isUsableAt(now)).isTrue();      // 만료 없음 = 무기한
        assertThat(expired.isUsableAt(now)).isFalse();
        assertThat(revoked.isUsableAt(now)).isFalse();
    }
}
