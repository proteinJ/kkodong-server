-- V8: 예약 · 출석 (PN-19 예약 관리, PN-08 대시보드, PN-09 출석 체크)
--
-- 이번 작업의 핵심이다. 예약과 출석·이용권이 어떻게 맞물리는지가 여기서 확정된다.
--
-- ★ 예약 → 등원 전환 모델 (2026-09-07 결정)
--
--     reservations(requested) ──승인──> reservations(confirmed)
--                                            │
--                                            └─> attendances(scheduled)  ← 예정 레코드 생성
--                                                     │
--                                              등원 처리(PN-09)
--                                                     │
--                                                     ├─> attendances(attended)
--                                                     └─> pass_ledger(deduct)  ← 이 시점에 차감
--
--   ⚠️ 이용권은 '예약 승인'이 아니라 '등원 확정' 시점에 차감한다.
--      예약 시점에 차감하면 취소·노쇼마다 복원 로직이 붙고, pass_ledger 가
--      실제로 오지도 않은 등원의 차감·복원 쌍으로 뒤덮인다. 회차는 서비스를
--      받은 사실에 대응해야 한다.
--
--      대신 이 선택에는 대가가 있다: 예약은 승인됐는데 등원일에 잔여가 0이거나
--      이용권이 만료된 상황이 실재한다. 이건 버그가 아니라 운영상 정상 상황이며
--      (그 사이에 다른 날 등원으로 회차를 썼을 수 있다), 두 지점에서 받는다:
--        - 예약 승인 시: 서버가 "잔여 회차 부족" 경고를 함께 내려준다(거부하지 않는다)
--        - 출석 체크 시: FR-PN09-02 대로 만료·잔여0 원생을 선택 대상에서 제외한다
--
-- ★ 왜 reservations 는 업종 공통이고 attendances 는 유치원 전용인가
--   미용실·병원에는 '등원/하원'도 '이용권 회차'도 없다. 예약을 잡고 서비스를 받으면
--   끝이다(reservations.status = completed). 반면 유치원은 예약 이후에 등원-하원이라는
--   하루짜리 상태가 따로 흐르고 거기에 회차가 붙는다.
--   → 공통 상태머신은 reservations 가 갖고, 유치원 고유의 하루 운영은 attendances 로 내린다.


-- ============================================================
-- 1. reservations — 예약 (PN-19)
-- ============================================================
CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    -- ⚠️ enrollments 와 같은 이유로 SET NULL + 스냅샷이다(V7 4번 주석 참조).
    --    견주가 탈퇴해도 매장의 예약 이력은 남아야 한다.
    dog_id            UUID REFERENCES dogs (id)  ON DELETE SET NULL,
    requested_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    dog_name_snapshot TEXT NOT NULL,

    -- 유치원 예약은 원생만 할 수 있다(F-11: 신청→승인→원생→이용권→예약).
    -- 미용실·병원은 원생 개념이 없어 NULL 이다.
    -- ⚠️ merchant_type = 'kindergarten' 이면 NOT NULL 임을 서버가 검증할 것 —
    --    업종별 조건부 필수는 CHECK 로 표현하려면 merchants 조인이 필요해 불가능하다.
    enrollment_id UUID REFERENCES enrollments (id) ON DELETE SET NULL,

    -- ★ service_date 를 starts_at 과 별도로 두는 이유
    --   "오늘 등원 예정"(PN-08)과 일일 정원 집계가 전부 날짜 단위인데, TIMESTAMPTZ 는
    --   UTC로 저장된다. starts_at 에서 매번 날짜를 뽑으면 자정 근처 예약이 KST 기준
    --   하루 밀린 날짜로 집계된다. 매장 로컬 날짜를 확정해 못박아 둔다.
    service_date DATE NOT NULL,

    -- 유치원은 종일(is_all_day=true)이라 운영시간으로 채운다.
    -- 미용실·병원은 이 두 값이 예약의 본질이다 — 그래서 지금부터 시각으로 저장한다.
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    is_all_day BOOLEAN NOT NULL DEFAULT true,

    -- requested: 견주 신청, 승인 대기 / confirmed: 승인(→ attendances 생성)
    -- rejected: 점주 거절 / cancelled: 취소 / completed: 이용 완료 / no_show: 노쇼
    status TEXT NOT NULL DEFAULT 'requested'
        CHECK (status IN ('requested', 'confirmed', 'rejected', 'cancelled', 'completed', 'no_show')),

    -- 견주가 신청했는지(owner), 점주가 전화 등으로 받아 대신 넣었는지(partner).
    -- 예약 채널별 비중은 점주 앱이 실제로 쓰이는지 판단하는 지표다.
    source TEXT NOT NULL DEFAULT 'owner' CHECK (source IN ('owner', 'partner')),

    note TEXT, -- 견주 요청사항

    responded_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    responded_at TIMESTAMPTZ,
    reject_reason TEXT,

    cancelled_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reservations_period CHECK (ends_at > starts_at)
);

-- [조회 성능] PN-19 캘린더 · PN-08 오늘 현황 — 매장의 날짜별 예약이 주 동선이다.
--   일일 정원 집계도 이 인덱스를 탄다.
CREATE INDEX idx_reservations_merchant_date
    ON reservations (merchant_id, service_date, status);
-- [조회 성능] PN-07/홈 배지 — 승인 대기 건수.
CREATE INDEX idx_reservations_pending
    ON reservations (merchant_id, created_at DESC)
    WHERE status = 'requested';
-- [조회 성능] 견주 앱 KG-09 "내 예약". 탈퇴로 NULL이 된 행은 색인에서 뺀다.
CREATE INDEX idx_reservations_requester
    ON reservations (requested_by_user_id, service_date DESC)
    WHERE requested_by_user_id IS NOT NULL;

-- [비즈니스 규칙] 같은 원생이 같은 날 예약을 중복으로 잡지 못하게 막는다.
--   거절·취소된 건은 제외해야 재예약이 되므로 부분 유니크다.
--   ⚠️ 유치원(종일) 전제의 제약이다. 미용실처럼 하루 두 번 예약이 정상인 업종을
--      붙일 때는 이 인덱스를 업종 조건이 붙은 형태로 교체해야 한다.
CREATE UNIQUE INDEX uq_reservations_active_per_day
    ON reservations (enrollment_id, service_date)
    WHERE enrollment_id IS NOT NULL
      AND status IN ('requested', 'confirmed', 'completed');

-- ⚠️ [동시성] 일일 정원(kindergarten_profiles.daily_capacity) 초과는 DB 제약으로
--    표현할 수 없다 — "이 날짜의 confirmed 건수 <= N"은 행 단위 CHECK 로 쓸 수 없고,
--    UNIQUE 로도 카운트 상한은 표현되지 않는다.
--
--    두 명의 원장이 동시에 승인하면 각자 정원 미달을 보고 둘 다 통과시킨다.
--    승인 트랜잭션 맨 앞에서 (매장, 날짜) 단위 advisory lock 을 잡을 것:
--
--      SELECT pg_advisory_xact_lock(hashtextextended(:merchantId::text || :serviceDate::text, 0));
--      -- 그 다음에 count → 정원 비교 → UPDATE status = 'confirmed'
--
--    매장 행 전체를 FOR UPDATE 로 잠그지 않는 이유는 날짜가 다른 승인끼리는
--    서로 막을 이유가 없기 때문이다.


-- ============================================================
-- 2. attendances — 등원/하원 (PN-08 대시보드, PN-09 출석 체크)
-- ============================================================
-- 유치원 전용. 예약이 승인되면 이 테이블에 scheduled 행이 하나 생기고,
-- 그 행이 하루 동안 attended → (하원) 로 흐른다.
CREATE TABLE attendances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID NOT NULL REFERENCES merchants (id)   ON DELETE CASCADE,
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,

    -- ★ 예약에서 전환됐음을 잇는 고리.
    --   NULL 이면 예약 없이 그날 바로 온 경우다(전화로 오늘 보내겠다고 한 상황).
    --   워크인을 막지 않는 이유: 막으면 점주가 출석을 못 찍고, 그러면 이용권도
    --   차감되지 않아 매출이 새는 쪽이 훨씬 나쁘다.
    reservation_id UUID REFERENCES reservations (id) ON DELETE SET NULL,

    attendance_date DATE NOT NULL,

    -- scheduled: 등원 예정 / attended: 등원함 / absent: 결석 / cancelled: 당일 취소
    -- ⚠️ 하원은 별도 상태가 아니라 checked_out_at 이 채워진 것으로 본다.
    --    상태로 만들면 "하원했지만 등원 안 함" 같은 불가능한 조합이 생긴다.
    status TEXT NOT NULL DEFAULT 'scheduled'
        CHECK (status IN ('scheduled', 'attended', 'absent', 'cancelled')),

    checked_in_at  TIMESTAMPTZ,
    checked_out_at TIMESTAMPTZ,
    checked_in_by_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    checked_out_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,

    -- ★ 이 등원으로 차감된 이용권과 그 원장 행.
    --   ⚠️ FR-PN09-03 — 등원 확정과 이용권 차감은 하나의 트랜잭션이다.
    --      status='attended' UPDATE / pass_ledger INSERT / passes.remaining_count UPDATE
    --      셋이 같은 트랜잭션 안에 있어야 한다. 하나라도 밖에 있으면 "등원은 됐는데
    --      회차가 안 깎인" 행이 남고, 그건 조회로 찾아낼 방법이 없다.
    --   기간권 등원이면 pass_id 는 있고 pass_ledger_id 는 NULL 이다(차감할 회차가 없다).
    pass_id UUID REFERENCES passes (id) ON DELETE SET NULL,
    pass_ledger_id UUID REFERENCES pass_ledger (id) ON DELETE SET NULL,

    -- FR-PN09-04 되돌리기. 오처리를 취소하면 차감도 복원되고, 그 사실이 남는다.
    -- ⚠️ 행을 지우지 않는다 — 되돌린 이력 자체가 분쟁 대응의 근거다.
    --    복원은 pass_ledger 에 restore 행을 새로 넣는 것으로 한다(원장은 append-only).
    reverted_at TIMESTAMPTZ,
    reverted_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_attendances_checkout_order CHECK (
        checked_out_at IS NULL OR checked_in_at IS NULL OR checked_out_at >= checked_in_at
    ),
    -- 등원하지 않았는데 등원 시각이 있을 수 없다.
    CONSTRAINT chk_attendances_checkin_status CHECK (
        checked_in_at IS NULL OR status = 'attended'
    )
);

-- [조회 성능] PN-08 출석 대시보드 — "오늘 이 매장의 전부". 점주 앱 로그인 직후
--   첫 화면의 질의이며 가장 자주 실행된다.
CREATE INDEX idx_attendances_merchant_date
    ON attendances (merchant_id, attendance_date, status);
-- [조회 성능] PN-11 원생 상세의 등원 이력 · KG-14 통합 타임라인.
CREATE INDEX idx_attendances_enrollment
    ON attendances (enrollment_id, attendance_date DESC);

-- [비즈니스 규칙] 한 원생은 하루에 한 번만 등원한다.
--   취소된 행은 제외해야 당일 취소 후 재등록이 가능하다.
CREATE UNIQUE INDEX uq_attendances_per_day
    ON attendances (enrollment_id, attendance_date)
    WHERE status <> 'cancelled';

-- [비즈니스 규칙] 하나의 예약은 하나의 출석으로만 전환된다.
--   전환을 두 번 실행하면(재시도·중복 요청) 회차가 두 번 깎인다.
CREATE UNIQUE INDEX uq_attendances_reservation
    ON attendances (reservation_id)
    WHERE reservation_id IS NOT NULL;


-- ============================================================
-- 3. pass_ledger — 출처 연결 (V7에서 미뤄둔 FK)
-- ============================================================
-- V7 시점에는 attendances 가 아직 없어 FK를 걸 수 없었다.
-- 차감 행이 어느 등원에서 비롯됐는지 이어야 KG-13 "등원일별 차감 내역"이 만들어진다.
ALTER TABLE pass_ledger
    ADD COLUMN source_attendance_id UUID REFERENCES attendances (id) ON DELETE SET NULL;

-- [조회 성능] KG-13 차감 이력에서 등원일을 함께 보여줄 때.
CREATE INDEX idx_pass_ledger_attendance
    ON pass_ledger (source_attendance_id)
    WHERE source_attendance_id IS NOT NULL;
