-- V6: 매장 · 스태프 · 상품 — 꼬동 파트너(점주 앱) 기반 (PN-01~05, PN-18)
--
-- 왜 지금인가:
--   PN 19개 화면 전부가 "어느 매장의, 어떤 권한을 가진 사람인가"를 먼저 알아야 한다.
--   예약(V8)·원생(V7)이 전부 이 두 테이블에 매달리므로 여기가 첫 삽이다.
--
-- ★ 왜 kindergartens 가 아니라 merchants 인가 (2026-09-07 결정)
--   예약은 유치원에서 시작하지만 미용실·병원으로 확장한다. 세 업종을 비교해 보면
--   같은 것과 다른 것이 뚜렷하게 갈린다:
--
--                유치원            미용실             병원
--     예약 단위   하루(종일 등원)   시술별 가변 슬롯   15~30분 진료
--     자원 제약   일일 정원(총량)   디자이너 1:1       수의사 1:1
--     결제       이용권 회차 차감   시술별 결제        진료 후 결제
--
--   → 매장·스태프·권한·예약 상태머신·리뷰는 셋이 똑같다. 반면 이용권 회차, 등원/하원,
--     알림장, 일일 정원은 유치원에만 있다.
--
--   그래서 공통분모만 merchants 로 뽑고, 업종 고유값은 *_profiles 형제 테이블로 내린다.
--   미용실을 붙일 때 merchant_type 값 하나와 grooming_profiles 를 추가하면 되고,
--   이미 쌓인 유치원 데이터는 건드리지 않는다.
--
--   ⚠️ 반대로 예약 슬롯 모델까지 지금 추상화하지 않는다. 세 업종의 슬롯 성질이
--      너무 달라서, 실제 미용실 요구사항 없이 만든 추상은 거의 확실히 틀린다.
--      유치원을 구체적으로 만들되 부수기 쉬운 형태로 두는 쪽을 택한다.
--
-- 명세 출처: REQUIREMENTS.xlsx 1.화면목록 PN-01~PN-20 / 2.기능요구사항 FR-PN* /
--            docs/product/MARKET.md 부록(똑독 분석)


-- ============================================================
-- 1. merchants — 매장 (PN-02 개설, PN-04 정보 관리)
-- ============================================================
-- 견주 앱(KG-01/KG-02)에서 지도로 탐색되는 대상이자, 점주 앱의 모든 데이터가
-- 소속되는 루트다.
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 업종. 값이 늘어나면 마이그레이션이 필요한데, 이것은 의도된 마찰이다 —
    -- 새 업종은 어차피 전용 *_profiles 테이블과 예약 규칙을 함께 들여야 하므로
    -- 마이그레이션 없이 값만 추가되는 상황 자체가 있어서는 안 된다.
    merchant_type TEXT NOT NULL DEFAULT 'kindergarten'
        CHECK (merchant_type IN ('kindergarten', 'grooming', 'clinic')),

    name TEXT NOT NULL, -- 상호

    -- ⚠️ 사업자등록번호는 국세청 진위확인 API로 검증한 값만 저장한다(FR-PN02-01).
    --    검증 실패 시 가입 자체를 진행하지 않으므로 NOT NULL 이다.
    --    하이픈 없는 10자리로 정규화해 저장할 것 — 표기 흔들림이 곧 중복 매장이 된다.
    business_registration_number TEXT NOT NULL UNIQUE,
    representative_name TEXT NOT NULL, -- 대표자명 (진위확인 입력값)
    business_opened_on DATE,           -- 개업일 (진위확인 입력값)
    verified_at TIMESTAMPTZ,           -- 진위확인 통과 시각. NULL이면 미검증 매장

    address TEXT,
    -- ⚠ PostGIS는 (경도 lng, 위도 lat) 순서다 — ST_MakePoint(lng, lat)
    location GEOGRAPHY(POINT, 4326),
    phone TEXT,
    description TEXT,
    image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- 요일별 운영시간. [{"day":"mon","open":"09:00","close":"19:00"}, ...]
    -- 구조가 업종·매장마다 흔들려서 컬럼으로 펴면 곧 NULL 밭이 된다.
    business_hours JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- 임시 휴무일. ["2026-09-30", ...]
    closed_dates JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- pending: 진위확인 전 / active: 견주 앱 노출 / suspended: 운영 중단(노출 제외)
    -- closed: 폐업. 폐업해도 행을 지우지 않는다 — 과거 예약·이용권 이력이 매달려 있다.
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'active', 'suspended', 'closed')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] KG-01 지도 탐색 — 현재 위치 반경 내 매장.
CREATE INDEX idx_merchants_location ON merchants USING GIST (location);
-- [조회 성능] 견주 앱은 active 매장만 본다. 업종 탭이 생기면 그대로 쓰인다.
CREATE INDEX idx_merchants_type_status ON merchants (merchant_type, status);


-- ============================================================
-- 2. kindergarten_profiles — 유치원 고유 수용 조건 (PN-04)
-- ============================================================
-- FR-PN04-02. 견주 앱 KG-02의 "적합도 문장"이 이 값들을 근거로 만들어진다.
-- merchants 에 합치지 않는 이유는 위 1번 주석 참조 — 미용실에는 정원도 접종요건도 없다.
CREATE TABLE kindergarten_profiles (
    -- 매장당 하나. PK를 FK와 겸하게 해서 1:1을 스키마로 강제한다.
    merchant_id UUID PRIMARY KEY REFERENCES merchants (id) ON DELETE CASCADE,

    -- 수용 가능 체급. dogs.size 와 같은 값 세트(small/medium/large).
    -- 값 유효성은 서버 enum이 맡는다 — dogs.personality_traits 와 같은 분담.
    accepted_sizes JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- ⚠️ 일일 정원. V8 예약 승인의 정원 검사 기준값이다.
    --    NULL이면 무제한으로 해석한다 — 0과 구분해야 하므로 DEFAULT를 두지 않는다.
    daily_capacity INTEGER CHECK (daily_capacity IS NULL OR daily_capacity > 0),

    requires_neutered BOOLEAN NOT NULL DEFAULT false,
    -- 요구 접종 항목. ["rabies", "dhppl", ...] 값 세트는 서버 enum.
    required_vaccinations JSONB NOT NULL DEFAULT '[]'::jsonb,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ============================================================
-- 3. merchant_staff — 매장 소속 스태프 (PN-01 역할, PN-03 가입, PN-18 관리)
-- ============================================================
-- ★ 점주용 계정 테이블을 따로 만들지 않는다 (2026-09-07 결정)
--   미용실 사장도 개를 키운다. 계정은 users 하나로 두고 "이 사람이 이 매장의
--   무엇인가"를 이 테이블로 얹는다. 한 사람이 견주이면서 원장일 수 있고, 체인점이면
--   여러 매장의 원장일 수도 있다 — 둘 다 이 구조에서 그냥 된다.
--
-- ⚠️ 역할 이름에 owner 를 쓰지 않는다.
--   이 코드베이스에서 owner 는 이미 dogs.owner_id = 견주를 뜻한다(PC-14, FR-PT03-02).
--   점주 쪽에 owner 를 또 쓰면 같은 단어가 정반대를 가리키게 된다.
--   요구사항 문서 용어 그대로 director(원장) / staff(선생님)를 쓴다.
CREATE TABLE merchant_staff (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    role TEXT NOT NULL CHECK (role IN ('director', 'staff')),

    -- 세부 권한. ["attendance", "daily_note", "pass", "enrollment"] 등.
    -- ⚠️ director 는 이 값을 보지 않고 전권으로 취급한다 — 원장에게서 권한을 뺏는
    --    상태를 만들면 매장이 잠긴다. 서버 Service 계층에서 role 을 먼저 본다.
    -- ⚠️ FR-PN18-01: 이용권 변경(pass)은 기본적으로 director 전용이다.
    --    staff 에게 부여하려면 원장이 명시적으로 켜야 한다.
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- pending: 선생님이 소속 신청 후 원장 승인 대기(PN-03) / active: 정상
    -- resigned: 퇴사. ⚠️ FR-PN18-02 — 행을 지우지 않는다. 이 사람이 쓴 알림장·
    --   출석 처리 이력이 작성자로 이 행을 참조하고 있고, 그 이력은 보존해야 한다.
    --   접근 권한 회수는 status 로만 한다.
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'active', 'resigned')),

    approved_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    approved_at TIMESTAMPTZ,
    resigned_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 한 사람이 같은 매장에 두 번 소속되지 않는다. 퇴사 후 재입사는 이 행을
    -- 되살리는 것으로 처리한다(이력이 이어져야 한다).
    CONSTRAINT uq_merchant_staff UNIQUE (merchant_id, user_id)
);

-- [조회 성능] PN-01 로그인 직후 "내가 속한 매장" — 점주 앱의 첫 질의다.
CREATE INDEX idx_merchant_staff_user ON merchant_staff (user_id, status);
-- [조회 성능] PN-18 스태프 목록 + 승인 대기 배지.
CREATE INDEX idx_merchant_staff_merchant ON merchant_staff (merchant_id, status);

-- [비즈니스 규칙] 매장에는 원장이 최소 한 명 있어야 하지만, 마지막 원장의 퇴사를
--   DB로 막을 방법은 없다(행 단위 CHECK로 표현 불가). 서버 Service 계층에서
--   "active director 가 1명뿐이면 퇴사 처리 거부"를 검증할 것.


-- ============================================================
-- 4. merchant_products — 이용권 상품 (PN-04)
-- ============================================================
-- FR-PN04-01. 상품 '정의'이고, 실제 발급분은 V7 passes 다. 둘을 분리하는 이유는
-- 상품 가격이 바뀌어도 이미 팔린 이용권의 조건은 그대로여야 하기 때문이다.
CREATE TABLE merchant_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    name TEXT NOT NULL, -- "주 3회 10회권" 등

    -- count: 횟수권(총 N회) / period: 기간권(유효기간 내 무제한)
    product_type TEXT NOT NULL CHECK (product_type IN ('count', 'period')),

    -- 횟수권의 총 회차. 기간권이면 NULL.
    total_count INTEGER CHECK (total_count IS NULL OR total_count > 0),
    -- 발급일로부터의 유효일수. NULL이면 무기한.
    valid_days INTEGER CHECK (valid_days IS NULL OR valid_days > 0),

    price INTEGER NOT NULL CHECK (price >= 0), -- 원 단위 정수. 통화 계산에 실수를 쓰지 않는다.

    -- 판매 중단해도 지우지 않는다 — 발급된 passes 가 이 행을 참조한다.
    is_active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 횟수권은 회차가 있어야 하고, 기간권은 유효기간이 있어야 상품이 성립한다.
    CONSTRAINT chk_merchant_products_shape CHECK (
        (product_type = 'count'  AND total_count IS NOT NULL) OR
        (product_type = 'period' AND valid_days  IS NOT NULL)
    )
);

CREATE INDEX idx_merchant_products_merchant ON merchant_products (merchant_id, is_active);


-- ============================================================
-- 5. merchant_invites — 원생 모집 QR / 링크 (PN-05)
-- ============================================================
-- FR-PN05-01. 매장에 게시한 QR로 견주가 즉시 연결된다(F-12 콜드스타트 경로).
CREATE TABLE merchant_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    -- QR·딥링크에 실리는 공개 코드. 추측 불가능한 난수여야 한다(URL-safe).
    -- ⚠️ merchant_id 를 그대로 노출하지 않는 이유: 코드를 폐기해도 매장 식별자는
    --    바뀌지 않으므로, id 를 실으면 한 번 유출된 링크를 영원히 막을 수 없다.
    code TEXT NOT NULL UNIQUE,

    -- NULL이면 무기한. FR-PN05-01의 "유효기간·폐기"가 이 둘이다.
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    created_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] PN-05 매장별 발급 이력.
CREATE INDEX idx_merchant_invites_merchant ON merchant_invites (merchant_id);


-- ============================================================
-- 6. device_tokens — 앱 구분 추가 (점주 앱 분리에 따른 필수 수정)
-- ============================================================
-- ★ 견주 앱과 점주 앱을 별도 앱으로 낸다 → APNs 번들 ID가 다르므로 토큰도 별개다.
--   지금 구조(UNIQUE(user_id, apns_token))로는 한 사람의 두 앱 토큰이 구분 없이
--   섞이고, 그 결과:
--     - 원장에게 보낼 "새 등원 신청" 푸시(PN-07)가 그 사람 견주 앱으로도 날아간다
--     - 보호자에게 보낼 등원 알림(F-14)이 점주 앱으로 간다
--     - 견주 앱 로그아웃 시 토큰을 지우면 점주 앱 푸시까지 끊긴다
--
--   ⚠️ 나중에 발견하면 이미 쌓인 토큰이 어느 앱 것인지 되살릴 방법이 없다.
--      점주 앱 첫 커밋보다 이 컬럼이 먼저 있어야 한다.
ALTER TABLE device_tokens
    ADD COLUMN app_kind TEXT NOT NULL DEFAULT 'owner'
        CHECK (app_kind IN ('owner', 'partner'));

-- 기존 행은 전부 견주 앱에서 온 것이다(점주 앱이 아직 없다). DEFAULT로 백필된다.
-- 이후 신규 등록은 클라이언트가 명시적으로 보내게 하고, DEFAULT에 기대지 않는다.

ALTER TABLE device_tokens
    DROP CONSTRAINT device_tokens_user_id_apns_token_key;

ALTER TABLE device_tokens
    ADD CONSTRAINT uq_device_tokens_user_app_token UNIQUE (user_id, app_kind, apns_token);

-- [조회 성능] "이 유저의 점주 앱 토큰만" — 푸시 발송의 주 동선.
CREATE INDEX idx_device_tokens_user_app ON device_tokens (user_id, app_kind);
