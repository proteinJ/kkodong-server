package com.kkodong.server.domain.reservation.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * (매장, 날짜) 단위 직렬화 락.
 *
 * <p><b>왜 필요한가</b>: 일일 정원 상한("이 날짜의 확정 예약 ≤ N")은 DB 제약으로 표현할 수
 * 없다. 행 단위 CHECK로는 다른 행을 셀 수 없고, UNIQUE로는 카운트 상한이 표현되지 않는다.
 * 그래서 승인 로직이 "센다 → 비교한다 → 승인한다"를 하는데, 이 사이에 다른 트랜잭션이
 * 끼어들면 둘 다 정원 미달을 보고 둘 다 통과시킨다:
 *
 * <pre>
 *   원장A: count=19 (정원 20)          원장B: count=19
 *   원장A: 19 &lt; 20 이므로 승인          원장B: 19 &lt; 20 이므로 승인
 *   → 21명. 정원 초과.
 * </pre>
 *
 * <p><b>왜 advisory lock인가</b>: 세는 대상이 "아직 없는 행"이라 잠글 행이 없다.
 * merchants 행을 {@code FOR UPDATE}로 잠글 수도 있지만, 그러면 날짜가 다른 승인끼리도
 * 서로 막는다 — 9월 10일 예약 승인이 9월 20일 예약 승인을 기다릴 이유가 없다.
 * 락 키를 (매장, 날짜)로 좁히면 실제로 경쟁하는 것들만 직렬화된다.
 *
 * <p><b>트랜잭션 스코프</b>: {@code pg_advisory_xact_lock}은 트랜잭션이 끝나면 자동으로
 * 풀린다. 명시적 해제가 없으므로 예외로 빠져나가도 락이 남지 않는다 —
 * 세션 단위 {@code pg_advisory_lock}을 쓰면 커넥션 풀에 락이 붙은 채로 반납돼
 * 다음 요청이 영문 모르고 멈춘다.
 *
 * <p>⚠️ 반드시 진행 중인 트랜잭션 안에서 불러야 한다. 트랜잭션이 없으면 락을 잡자마자
 * 풀려서 아무것도 막지 못한다 — {@code MANDATORY}로 그 실수를 기동이 아니라 호출 시점에
 * 예외로 드러낸다.
 */
@Component
@RequiredArgsConstructor
public class MerchantDayLock {

    @PersistenceContext
    private EntityManager em;

    /**
     * 해당 (매장, 날짜)에 대한 배타 락을 잡는다. 같은 키를 잡으려는 다른 트랜잭션은
     * 이 트랜잭션이 끝날 때까지 대기한다.
     *
     * <p>{@code hashtextextended}로 문자열을 bigint로 줄여 락 키로 쓴다. 해시 충돌이
     * 나면 무관한 (매장, 날짜) 쌍이 서로 기다리게 되는데, 이는 정확성 문제가 아니라
     * 아주 드문 성능 손실이라 감수한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID merchantId, LocalDate date) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, merchantId + ":" + date)
                .getSingleResult();
    }
}
