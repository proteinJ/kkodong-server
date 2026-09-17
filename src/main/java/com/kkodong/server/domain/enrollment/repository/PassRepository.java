package com.kkodong.server.domain.enrollment.repository;

import com.kkodong.server.domain.enrollment.domain.Pass;
import com.kkodong.server.domain.enrollment.domain.PassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PassRepository extends JpaRepository<Pass, UUID> {

    List<Pass> findAllByEnrollmentId(UUID enrollmentId);

    /**
     * 차감에 쓸 이용권 후보. <b>만료일이 빠른 것부터</b> 준다.
     *
     * <p>먼저 만료되는 것을 먼저 쓰는 것이 견주에게 유리하다 — 반대로 하면 유효기간이
     * 남은 이용권을 놔둔 채 다른 것을 먼저 태워 결국 하나가 통째로 만료된다.
     * 만료가 없는(무기한) 이용권은 맨 뒤로 보낸다.
     *
     * <p>⚠️ 상태만으로 거르지 않는다 — 잔여·만료 판정은 {@code Pass.isUsableOn}이 한다.
     * EXHAUSTED로 옮겨지지 않은 채 잔여가 0인 행이 섞일 수 있기 때문이다.
     */
    @Query("""
            select p from Pass p
            where p.enrollmentId = :enrollmentId
              and p.status = :status
            order by case when p.expiresOn is null then 1 else 0 end, p.expiresOn asc
            """)
    List<Pass> findUsableCandidates(@Param("enrollmentId") UUID enrollmentId,
                                    @Param("status") PassStatus status);

    /**
     * 차감 후보의 <b>ID만</b> 만료일 순으로 가져온다.
     *
     * <p>⚠️ 엔티티가 아니라 ID를 주는 것이 핵심이다. 후보를 엔티티로 먼저 읽으면 그것이
     * 영속성 컨텍스트에 올라가고, 이어지는 {@link #findByIdForUpdate}는 DB 락은 잡지만
     * <b>캐시에 있는 낡은 인스턴스를 그대로 돌려준다</b>(Hibernate는 락을 잡아도 필드를
     * 다시 읽어오지 않는다). 그러면 뒤에 들어온 트랜잭션이 락을 기다렸다가 얻고도
     * 옛날 잔여를 보고 통과해, 잔여 1인 이용권으로 두 건이 차감된다.
     *
     * <p>ID만 읽으면 락을 잡는 시점이 그 이용권의 최초 적재가 되어 항상 최신 값을 본다.
     * 실제로 이 구분이 없을 때 동시 등원 2건이 모두 성공하는 것을 테스트로 확인했다
     * ({@code AttendancePassDeductionTest.concurrentCheckInsOnSamePassDeductOnlyOnce}).
     */
    @Query("""
            select p.id from Pass p
            where p.enrollmentId = :enrollmentId
              and p.status = :status
            order by case when p.expiresOn is null then 1 else 0 end, p.expiresOn asc
            """)
    List<UUID> findUsableCandidateIds(@Param("enrollmentId") UUID enrollmentId,
                                      @Param("status") PassStatus status);

    /**
     * 차감 대상을 잠근 채 읽는다.
     *
     * <p>⚠️ 같은 원생을 두 기기에서 동시에 등원 처리하면 둘 다 잔여를 읽고 둘 다 1을 빼서
     * 한 번만 깎일 수 있다(lost update). 비관적 락으로 그 구간을 직렬화한다 —
     * 예약 정원과 달리 여기는 잠글 행이 분명히 있으므로 advisory lock이 필요 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pass p where p.id = :id")
    Optional<Pass> findByIdForUpdate(@Param("id") UUID id);
}
