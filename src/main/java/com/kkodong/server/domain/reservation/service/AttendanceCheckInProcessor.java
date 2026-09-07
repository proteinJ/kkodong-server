package com.kkodong.server.domain.reservation.service;

import com.kkodong.server.domain.enrollment.domain.Pass;
import com.kkodong.server.domain.enrollment.domain.PassLedger;
import com.kkodong.server.domain.enrollment.domain.PassStatus;
import com.kkodong.server.domain.enrollment.repository.PassLedgerRepository;
import com.kkodong.server.domain.enrollment.repository.PassRepository;
import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.repository.AttendanceRepository;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 등원 한 건의 <b>원자적</b> 처리. 등원 확정과 이용권 차감이 절대 갈라지지 않게 한다(FR-PN09-03).
 *
 * <p><b>왜 별도 컴포넌트인가</b>: 다중 처리(FR-PN09-01)는 10마리 중 1마리가 실패해도
 * 나머지 9마리는 확정돼야 한다 — 전부 되돌리면 점주는 원인을 모른 채 다시 눌러야 한다.
 * 그러려면 건별로 트랜잭션이 갈려야 하는데, 같은 클래스 안에서 메서드를 부르면 스프링
 * 프록시를 타지 않아 {@code REQUIRES_NEW}가 무시된다. 그래서 호출을 클래스 밖으로 뺀다.
 *
 * <p>⚠️ 부분 성공은 <b>건과 건 사이</b>에만 허용된다. 한 건 안에서 등원만 되고 차감이 안 되면
 * "등원은 됐는데 회차가 안 깎인" 행이 남고, 그건 조회로 찾아낼 방법이 없다.
 */
@Component
@RequiredArgsConstructor
public class AttendanceCheckInProcessor {

    private final AttendanceRepository attendanceRepository;
    private final PassRepository passRepository;
    private final PassLedgerRepository passLedgerRepository;

    /**
     * 등원 확정 + 이용권 1회 차감 + 원장 기록을 한 트랜잭션으로 처리한다.
     *
     * @throws BusinessException 사용 가능한 이용권이 없으면 {@code NO_USABLE_PASS}(PA001).
     *         ⚠️ 차감 없이 등원시키지 않는다 — 그렇게 하면 매출이 조용히 샌다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkIn(UUID attendanceId, UUID actorUserId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTENDANCE_NOT_FOUND));

        Pass pass = pickUsablePass(attendance.getEnrollmentId(), attendance.getAttendanceDate());

        Integer balanceAfter = pass.deduct();

        // 기간권은 차감할 회차가 없어 원장에 남길 것도 없다.
        UUID ledgerId = null;
        if (balanceAfter != null) {
            PassLedger ledger = passLedgerRepository.save(
                    PassLedger.deduction(pass.getId(), balanceAfter, actorUserId, attendance.getId()));
            ledgerId = ledger.getId();
        }

        attendance.checkIn(actorUserId, pass.getId(), ledgerId);
    }

    /**
     * 차감할 이용권을 고른다. 만료일이 빠른 것부터 쓴다 — 반대로 하면 유효기간이 남은
     * 이용권을 놔둔 채 다른 것을 먼저 태워 결국 하나가 통째로 만료된다.
     *
     * <p>⚠️ 후보를 <b>ID로만</b> 받아온다. 엔티티로 먼저 읽으면 영속성 컨텍스트에 올라가고,
     * 이어지는 락 조회가 DB 락은 잡되 <b>캐시에 있는 낡은 인스턴스를 돌려준다</b> —
     * Hibernate는 락을 잡아도 필드를 다시 읽어오지 않기 때문이다. 그러면 뒤에 들어온
     * 트랜잭션이 락을 기다렸다가 얻고도 옛날 잔여를 보고 통과해, 잔여 1인 이용권으로
     * 두 건이 차감된다(실제로 테스트에서 재현됐다).
     *
     * <p>ID만 받으면 락을 잡는 시점이 그 이용권의 최초 적재라 항상 최신 값을 본다.
     * 그 위에서 다시 {@code isUsableOn}으로 검사한다 — 락을 기다리는 동안 다른
     * 트랜잭션이 마지막 회차를 써버렸을 수 있다.
     */
    private Pass pickUsablePass(UUID enrollmentId, LocalDate date) {
        List<UUID> candidateIds = passRepository.findUsableCandidateIds(enrollmentId, PassStatus.ACTIVE);

        for (UUID candidateId : candidateIds) {
            Pass locked = passRepository.findByIdForUpdate(candidateId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PASS_NOT_FOUND));
            if (locked.isUsableOn(date)) {
                return locked;
            }
            // 락을 잡는 사이 다른 트랜잭션이 마지막 회차를 써버린 경우 — 다음 후보로 넘어간다.
        }
        throw new BusinessException(ErrorCode.NO_USABLE_PASS);
    }
}
