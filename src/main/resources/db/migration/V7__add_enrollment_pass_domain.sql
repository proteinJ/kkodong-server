-- V7: 신청서 · 서약서 · 원생 · 이용권 (PN-06, PN-07, PN-10~12 / KG-06~08)
--
-- 왜 지금인가:
--   예약(V8)은 "이 강아지가 이 매장의 원생인가"와 "차감할 이용권이 있는가"를
--   알아야 성립한다. 원생과 이용권이 예약보다 먼저 있어야 한다.
--
-- 흐름 F-11 → F-13 을 그대로 테이블로 옮긴 것이다:
--   신청서 양식(PN-06) → 견주 제출(KG-06/07) → 접수함 승인(PN-07)
--     → 원생 생성 → 이용권 발급(PN-12)


-- ============================================================
-- 1. application_forms — 신청서 양식 (PN-06)
-- ============================================================
-- FR-PN06-01. 점주가 매장별로 추가 질문을 구성한다.
--
-- ★ 왜 버전을 남기는가
--   양식을 고친 뒤에 과거 제출값을 보면, 어떤 질문에 대한 답인지 알 수 없게 된다.
--   ("기타 특이사항"이 3번 항목이었는데 지금은 5번이면 값이 엉뚱한 질문에 붙는다.)
--   그래서 양식은 수정하지 않고 새 버전을 만들고, 제출본은 자신이 쓴 버전을 가리킨다.
CREATE TABLE application_forms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    version INTEGER NOT NULL CHECK (version > 0),

    -- 추가 질문 정의. [{"key":"pickup","label":"픽업 필요","type":"boolean","required":false}, ...]
    -- 항목 구조가 점주 자유 구성이라 컬럼으로 펼 수 없다.
    extra_fields JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 현재 견주에게 노출되는 양식. 매장당 하나만 true 여야 한다(아래 부분 유니크).
    is_active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_application_forms_version UNIQUE (merchant_id, version)
);

-- [비즈니스 규칙] 활성 양식은 매장당 하나뿐이다. 전체 UNIQUE로는 표현할 수 없어
--   부분 유니크 인덱스로 만든다(uq_friend_requests_pending 과 같은 구조).
CREATE UNIQUE INDEX uq_application_forms_active
    ON application_forms (merchant_id)
    WHERE is_active;


-- ============================================================
-- 2. consent_documents — 서약서 (PN-06 등록, KG-07 동의)
-- ============================================================
-- ★ 항목을 분리해 저장하는 이유 (PC-29)
--   유치원 단체 사진에는 다른 강아지가 함께 찍히는 것이 기본이다. "사고 책임"에
--   동의한 것과 "촬영·공개"에 동의한 것을 한 덩어리로 받으면, 나중에 이 사진을
--   커뮤니티로 내보내도 되는지(F-16) 판단할 근거가 없다.
--   동의는 항목별로 받고 항목별로 기록해야 그 판단이 가능하다.
CREATE TABLE consent_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    version INTEGER NOT NULL CHECK (version > 0),
    body TEXT NOT NULL, -- 서약서 전문

    -- 개별 동의 항목. [{"key":"accident_liability","label":"...","required":true}, ...]
    -- 항목 key 세트(사고책임·촬영공개·마케팅)의 유효성은 서버 enum이 맡는다.
    items JSONB NOT NULL DEFAULT '[]'::jsonb,

    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_consent_documents_version UNIQUE (merchant_id, version)
);

CREATE UNIQUE INDEX uq_consent_documents_active
    ON consent_documents (merchant_id)
    WHERE is_active;


-- ============================================================
-- 3. enrollment_applications — 등원 신청 (KG-06/07/08, PN-07)
-- ============================================================
CREATE TABLE enrollment_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    -- 신청 시점의 강아지. 승인되면 enrollments 로 이어진다.
    dog_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    applicant_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- 어느 QR/링크로 들어왔는지(F-12). 직접 검색해 신청했으면 NULL.
    -- 점주 입장에서 모집 채널별 성과를 보는 유일한 근거다.
    invite_id UUID REFERENCES merchant_invites (id) ON DELETE SET NULL,

    -- ⚠️ 어느 버전의 양식/서약서로 낸 신청인지 반드시 고정한다. 위 1·2번 주석 참조.
    form_id UUID NOT NULL REFERENCES application_forms (id),
    consent_document_id UUID NOT NULL REFERENCES consent_documents (id),

    -- 추가 질문 답변. form_id 가 가리키는 버전의 extra_fields 키에 대응한다.
    submitted_values JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 항목별 동의 결과. {"accident_liability":true,"photo_public":false,...}
    -- ⚠️ FR-KG07-02 — 서명과 동의 시각을 남겨야 양쪽이 나중에 재열람할 수 있다.
    consented_items JSONB NOT NULL DEFAULT '{}'::jsonb,
    signature_image_url TEXT,
    consented_at TIMESTAMPTZ,

    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    reject_reason TEXT, -- FR-PN07-01: 거절 시 사유 입력

    reviewed_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] PN-07 접수함 — 매장의 대기 중 신청. 홈 배지 카운트도 여기서 나온다.
CREATE INDEX idx_enrollment_applications_merchant
    ON enrollment_applications (merchant_id, status, created_at DESC);
-- [조회 성능] KG-08 견주 쪽 "내 신청 현황".
CREATE INDEX idx_enrollment_applications_applicant
    ON enrollment_applications (applicant_user_id, status);

-- [비즈니스 규칙] 같은 강아지가 같은 매장에 대기 중 신청을 중복으로 쌓지 못하게 막는다.
--   거절·취소 후 재신청은 허용해야 하므로 부분 유니크다.
CREATE UNIQUE INDEX uq_enrollment_applications_pending
    ON enrollment_applications (merchant_id, dog_id)
    WHERE status = 'pending';


-- ============================================================
-- 4. enrollments — 원생 (PN-10 목록, PN-11 상세)
-- ============================================================
-- ★ 왜 dog_id 가 ON DELETE SET NULL 인가 (중요)
--   견주는 언제든 탈퇴할 수 있고(FR-AU07-01, Apple 심사 5.1.1(v) 필수), 탈퇴하면
--   users → dogs 가 CASCADE 로 지워진다. 그런데 이 행에는 매장의 이용권 발급·차감
--   이력과 출석 기록이 매달려 있다 — 매출 데이터다.
--
--   CASCADE 로 두면 견주 한 명의 탈퇴가 매장의 회계 기록을 지운다.
--   RESTRICT 로 두면 반대로 탈퇴 자체가 실패한다(심사 항목 위반).
--   → SET NULL + 스냅샷이 유일하게 둘 다 만족하는 선택이다.
--
--   ⚠️ 그래서 아래 스냅샷 컬럼은 편의용이 아니라 필수다. dog_id 가 NULL 이 된 뒤
--      점주 화면이 "이름 없는 원생"을 보여주면 안 된다.
--   ⚠️ 스냅샷은 점주가 정산·분쟁에 필요한 최소한만 담는다. 탈퇴한 사람의 정보를
--      필요 이상으로 남기지 않는다.
CREATE TABLE enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    dog_id         UUID REFERENCES dogs (id)  ON DELETE SET NULL,
    owner_user_id  UUID REFERENCES users (id) ON DELETE SET NULL,

    -- 탈퇴 후에도 남는 최소 식별 정보.
    dog_name_snapshot   TEXT NOT NULL,
    dog_breed_snapshot  TEXT,
    owner_name_snapshot TEXT,

    -- 승인된 신청서. 제출 당시 값·동의 이력의 원본이 여기 있다.
    application_id UUID REFERENCES enrollment_applications (id) ON DELETE SET NULL,

    -- active: 재원 / paused: 휴원 / withdrawn: 퇴원
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'paused', 'withdrawn')),

    enrolled_on DATE NOT NULL DEFAULT CURRENT_DATE,
    withdrawn_at TIMESTAMPTZ,

    -- PN-11 특이사항 메모. ⚠️ 보호자에게 노출하지 않는다(4.데이터항목 비고).
    --   견주 앱 응답에 이 컬럼이 섞여 나가지 않게 DTO를 분리할 것.
    staff_memo TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] PN-10 원생 목록 — 상태 필터가 기본으로 걸린다.
CREATE INDEX idx_enrollments_merchant ON enrollments (merchant_id, status);
-- [조회 성능] KG-09 견주 쪽 "내 유치원". 탈퇴로 NULL이 된 행은 색인에서 뺀다.
CREATE INDEX idx_enrollments_owner ON enrollments (owner_user_id)
    WHERE owner_user_id IS NOT NULL;

-- [비즈니스 규칙] 같은 강아지가 같은 매장에 재원 상태로 두 번 등록되지 않는다.
--   퇴원 후 재등록은 새 행으로 허용한다 — 재원 이력이 끊겨야 이용권도 분리된다.
--   ⚠️ UD-E(한 강아지의 다중 유치원 동시 등록)는 이 제약과 무관하다. 매장이 다르면
--      merchant_id 가 달라 그대로 허용된다 — 즉 현재 스키마는 다중 등록을 지원한다.
CREATE UNIQUE INDEX uq_enrollments_active_dog
    ON enrollments (merchant_id, dog_id)
    WHERE status <> 'withdrawn' AND dog_id IS NOT NULL;


-- ============================================================
-- 5. passes — 발급된 이용권 (PN-12, KG-13)
-- ============================================================
-- ⚠️ 매출 데이터다. 이 테이블의 값은 절대 조용히 바뀌어서는 안 된다 —
--    모든 변경은 pass_ledger 에 한 줄을 남기고, 잔여 회차는 원장(ledger)과
--    항상 일치해야 한다(FR-PN12-01).
CREATE TABLE passes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,

    -- 발급 근거 상품. 상품이 판매 중단돼도 이 행은 남아야 하므로 삭제를 막는다.
    product_id UUID REFERENCES merchant_products (id) ON DELETE RESTRICT,

    -- 발급 시점 상품 조건 스냅샷. 이후 상품 가격·회차가 바뀌어도 이미 팔린 이용권의
    -- 조건은 그대로여야 한다.
    product_name_snapshot TEXT NOT NULL,
    product_type TEXT NOT NULL CHECK (product_type IN ('count', 'period')),
    price_snapshot INTEGER CHECK (price_snapshot IS NULL OR price_snapshot >= 0),

    -- 횟수권의 잔여 회차. 기간권이면 NULL.
    -- ⚠️ 음수가 되면 안 된다. 차감은 반드시 이 CHECK가 지켜지는 트랜잭션 안에서.
    remaining_count INTEGER CHECK (remaining_count IS NULL OR remaining_count >= 0),
    total_count     INTEGER CHECK (total_count IS NULL OR total_count > 0),

    -- NULL이면 무기한. FR-PN09-02의 "만료" 판정 기준.
    expires_on DATE,

    -- active: 사용 가능 / exhausted: 회차 소진 / expired: 기간 만료
    -- refunded: 환불 / suspended: 일시 정지
    -- ⚠️ exhausted·expired 는 파생 상태다. 배치로 갱신하지 말고 조회 시 함께 판정할 것 —
    --    "잔여 0인데 아직 active" 같은 시차가 출석 화면에서 곧바로 사고가 된다.
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'exhausted', 'expired', 'refunded', 'suspended')),

    issued_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_passes_shape CHECK (
        (product_type = 'count'  AND remaining_count IS NOT NULL AND total_count IS NOT NULL) OR
        (product_type = 'period' AND expires_on IS NOT NULL)
    )
);

-- [조회 성능] PN-09 출석 체크의 "유효한 이용권 보유 원생" 판정 + KG-13 견주 조회.
CREATE INDEX idx_passes_enrollment ON passes (enrollment_id, status);
-- [조회 성능] PN-17 결제 임박 집계 — 만료 7일 이내 / 잔여 2회 미만.
CREATE INDEX idx_passes_expiring ON passes (expires_on) WHERE status = 'active';


-- ============================================================
-- 6. pass_ledger — 이용권 변동 이력 (PN-12)
-- ============================================================
-- FR-PN12-01. 누가·언제·무엇을 바꿨는지 전부 남긴다. 되돌리기(FR-PN09-04)와
-- 분쟁 대응의 유일한 근거이며, passes.remaining_count 의 진실 원본이다.
--
-- ⚠️ append-only 다. UPDATE·DELETE 하지 않는다. 잘못 넣었으면 반대 방향 행을 추가한다.
CREATE TABLE pass_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pass_id UUID NOT NULL REFERENCES passes (id) ON DELETE CASCADE,

    -- grant: 발급 / deduct: 차감(등원) / restore: 복원(되돌리기·취소)
    -- extend: 기간 연장 / refund: 환불 / adjust: 수동 조정
    entry_type TEXT NOT NULL
        CHECK (entry_type IN ('grant', 'deduct', 'restore', 'extend', 'refund', 'adjust')),

    -- 회차 변동량. 차감이면 음수, 복원이면 양수. 기간 연장 등 회차와 무관하면 0.
    delta INTEGER NOT NULL,
    -- 이 행을 적용한 직후의 잔여 회차. 기간권이면 NULL.
    -- 스냅샷을 남겨야 중간에 계산이 어긋났을 때 어느 지점에서 깨졌는지 찾을 수 있다.
    balance_after INTEGER,

    reason TEXT,
    -- 처리자. 시스템 자동 처리(만료 등)면 NULL.
    actor_user_id UUID REFERENCES users (id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] KG-13 차감 이력 · PN-12 감사 로그 — 항상 시간 역순으로 읽는다.
CREATE INDEX idx_pass_ledger_pass ON pass_ledger (pass_id, created_at DESC);
