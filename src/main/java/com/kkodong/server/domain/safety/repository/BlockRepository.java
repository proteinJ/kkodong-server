package com.kkodong.server.domain.safety.repository;

import com.kkodong.server.domain.safety.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    Optional<Block> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /**
     * 이미 차단돼 있으면 아무것도 하지 않는다(멱등).
     *
     * <p>"조회해서 없으면 저장"으로 짜면 동시 요청(더블탭 등)에서 양쪽 SELECT가 모두
     * 빈 결과를 받아 둘 다 INSERT하고, {@code UNIQUE (blocker_id, blocked_id)} 위반으로
     * 500이 난다. Postgres 기본 격리 수준(READ COMMITTED)에서는 상대의 미커밋 INSERT가
     * 보이지 않으므로 애플리케이션 코드로는 이 틈을 없앨 수 없다.
     *
     * <p>{@code INSERT ... ON CONFLICT DO NOTHING}은 한 문장이라 조회-삽입 사이에 틈이
     * 없고, 충돌 시 예외 대신 조용히 스킵한다 — 트랜잭션이 abort되지 않는 것이 핵심.
     *
     * <p>⚠️ ON CONFLICT의 컬럼 목록은 실재하는 유니크 제약(V1__init.sql:186)과 정확히
     * 일치해야 한다. 어긋나면 기동이 아니라 호출 시점에 터진다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO blocks (blocker_id, blocked_id)
            VALUES (:blockerId, :blockedId)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("blockerId") UUID blockerId,
                        @Param("blockedId") UUID blockedId);

    /**
     * 차단 해제.
     *
     * <p>{@code blocker_id}를 조건에 넣으므로 <b>소유자 검증이 WHERE 절에 내장</b>돼 있다 —
     * {@code blockerId}는 JWT에서 나온 값이라 위조할 수 없고, 남의 차단 행은 애초에
     * 매칭되지 않는다. 그래서 별도 권한 확인이 필요 없다.
     * (PK만으로 행이 특정되는 {@code DogRepository.deleteById}와 대비되는 지점 —
     *  그쪽은 소유자 확인을 빼면 남의 강아지가 지워진다.)
     *
     * <p>차단돼 있지 않아도 0행이 지워지고 끝이므로 사전 존재 확인도 불필요하다(멱등).
     */
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** 내가 차단한 목록. 설정 화면의 차단 해제 리스트용이라 최신순으로 준다. */
    List<Block> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    // ── 아래 두 메서드는 friend/chat/community가 차단을 존중하기 위해 쓴다 ──
    //    SAFETY-1의 실제 요구사항은 차단 저장이 아니라 "다른 도메인이 차단을 지키는 것"이다.
    //    지금 만들어두는 이유: 도메인마다 각자 구현하면 검증 누락이 생기고,
    //    RLS가 없어 누락되면 그대로 뚫린다(KKODONG_CONCEPT.md 6절).

    /**
     * 두 유저 사이에 <b>어느 방향으로든</b> 차단이 있으면 true.
     * 친구 신청 403(API_SPEC 2.2), 채팅 SUBSCRIBE/SEND 거부(3절)의 판정 기준.
     */
    @Query("""
            select count(b) > 0 from Block b
             where (b.blockerId = :a and b.blockedId = :b)
                or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    /**
     * 나와 차단 관계로 얽힌 모든 상대의 id — <b>내가 차단한 사람 + 나를 차단한 사람</b>.
     * 추천 피드 양방향 제외(API_SPEC 2.1 규약 8)와 커뮤니티 목록 제외(7절)에서
     * {@code NOT IN} 집합으로 쓴다.
     */
    @Query("""
            select case when b.blockerId = :me then b.blockedId else b.blockerId end
              from Block b
             where b.blockerId = :me or b.blockedId = :me
            """)
    List<UUID> findRelatedUserIds(@Param("me") UUID me);
}
