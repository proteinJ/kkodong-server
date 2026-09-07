-- V9: 일일 운영 · 소통 (PN-13 담당 배정, PN-14 알림장, PN-15 미디어, PN-16 공지, PN-20 리뷰)
--
-- 흐름 F-14(일일 운영 루프)의 나머지 절반이다. 등원(V8) 이후에 매일 반복되는 일들 —
-- 담당을 나누고, 사진을 올리고, 알림장을 써서 보호자에게 보낸다.
--
-- ⚠️ 이 파일의 테이블은 '매일 쓰는' 것들이다. 입력 부담이 곧 이탈이므로
--    템플릿·일괄 작성이 스키마 차원에서 받쳐져 있어야 한다(FR-PN14-01).


-- ============================================================
-- 1. staff_assignments — 담당 배정 (PN-13)
-- ============================================================
-- FR-PN13-01. 오늘 어느 선생님이 어느 강아지를 맡는지. 담당 마릿수 카운트가
-- 이 테이블의 group by 로 나온다.
CREATE TABLE staff_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID NOT NULL REFERENCES merchants (id)   ON DELETE CASCADE,
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,

    -- ⚠️ users 가 아니라 merchant_staff 를 참조한다.
    --    "이 매장의 스태프로서" 담당하는 것이므로, 퇴사(status='resigned')하면
    --    과거 배정 이력은 남되 그 사람이 여전히 매장 소속인지는 merchant_staff 가 답한다.
    staff_id UUID NOT NULL REFERENCES merchant_staff (id) ON DELETE CASCADE,

    assigned_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 한 강아지는 하루에 한 선생님이 맡는다.
    CONSTRAINT uq_staff_assignments UNIQUE (enrollment_id, assigned_date)
);

-- [조회 성능] PN-13 "오늘 선생님별 담당" + 마릿수 카운트.
CREATE INDEX idx_staff_assignments_staff_date
    ON staff_assignments (staff_id, assigned_date);
-- [조회 성능] PN-08 대시보드에서 담당 정보를 함께 보여줄 때.
CREATE INDEX idx_staff_assignments_merchant_date
    ON staff_assignments (merchant_id, assigned_date);


-- ============================================================
-- 2. note_templates — 알림장 문구 템플릿 (FR-PN14-01)
-- ============================================================
-- 매일 쓰는 기능이라 입력 부담을 줄이는 것이 기능의 성패다.
CREATE TABLE note_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    title TEXT NOT NULL,
    -- 항목별 기본값. daily_notes 의 활동/식사/배변/컨디션 키와 같은 모양이다.
    content JSONB NOT NULL DEFAULT '{}'::jsonb,

    created_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_note_templates_merchant ON note_templates (merchant_id);


-- ============================================================
-- 3. daily_notes — 알림장 (PN-14 작성, KG-10/11 열람)
-- ============================================================
-- ★ 일괄 작성이어도 원생 단위로 개별 행을 만든다 (4.데이터항목 비고)
--   "오늘 다 같이 산책했어요"를 20명에게 보내더라도 행은 20개다. 보호자마다
--   읽음 시각이 다르고, 한 명만 내용을 고치는 일이 반드시 생기며, KG-14 통합
--   타임라인이 원생 단위로 조회되기 때문이다.
--   공유 본문 + 참조 구조로 만들면 그 세 가지가 전부 특수 케이스가 된다.
CREATE TABLE daily_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID NOT NULL REFERENCES merchants (id)   ON DELETE CASCADE,
    enrollment_id UUID NOT NULL REFERENCES enrollments (id) ON DELETE CASCADE,

    -- 그날의 등원 기록. 결석일에도 알림장을 쓸 수 있어야 하므로 nullable.
    attendance_id UUID REFERENCES attendances (id) ON DELETE SET NULL,

    note_date DATE NOT NULL,

    -- 항목. 값이 비어 있는 항목은 화면에서 통째로 감춘다.
    activity  TEXT, -- 활동
    meal      TEXT, -- 식사
    bathroom  TEXT, -- 배변
    condition TEXT, -- 컨디션
    remark    TEXT, -- 특이사항

    author_user_id UUID REFERENCES users (id) ON DELETE SET NULL,

    -- draft: 작성 중(보호자에게 안 보임) / sent: 발송됨
    -- ⚠️ 발송 전에는 보호자 조회 응답에서 제외할 것 — 쓰다 만 알림장이 새면
    --    그 자체로 CS가 된다.
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'sent')),
    sent_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ, -- 보호자가 연 시각(KG-10 읽음 표시)

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 한 원생에게 하루 한 장.
    CONSTRAINT uq_daily_notes_per_day UNIQUE (enrollment_id, note_date)
);

-- [조회 성능] KG-10 알림장 목록 — 날짜 역순이 기본이고 기간 검색이 붙는다.
CREATE INDEX idx_daily_notes_enrollment
    ON daily_notes (enrollment_id, note_date DESC);
-- [조회 성능] PN-14 작성 화면 — 오늘 매장 전체의 작성 현황(누가 안 썼는지).
CREATE INDEX idx_daily_notes_merchant_date
    ON daily_notes (merchant_id, note_date, status);


-- ============================================================
-- 4. merchant_media — 사진 · 영상 (PN-15, KG-12 앨범)
-- ============================================================
-- ⚠️ PC-23 미디어 보관 비용
--   유치원 1곳이 하루 100장을 올리면 스토리지가 빠르게 부푼다. CONCEPT.md 4절의
--   "트래픽 연동형 인프라로 고정비 0" 원칙과 정면으로 충돌하는 유일한 기능이다.
--   그래서 용량·해상도를 행마다 기록해 둔다 — 정책(UD-C)이 정해지기 전이라도
--   실제 증가 속도를 측정할 수 있어야 나중에 수치를 정할 근거가 생긴다.
CREATE TABLE merchant_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    media_type TEXT NOT NULL CHECK (media_type IN ('image', 'video')),
    url TEXT NOT NULL,
    thumbnail_url TEXT,

    -- FR-PN15-02 용량·해상도 정책의 측정값. 리사이즈 후 값을 넣는다.
    size_bytes BIGINT CHECK (size_bytes IS NULL OR size_bytes >= 0),
    width  INTEGER,
    height INTEGER,

    taken_on DATE, -- 촬영일. KG-12 앨범이 날짜별 그리드라 정렬 축이 된다.
    uploaded_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] KG-12 활동 앨범 — 날짜별 그리드.
CREATE INDEX idx_merchant_media_merchant_date
    ON merchant_media (merchant_id, taken_on DESC);


-- ============================================================
-- 5. media_tags — 원생 태깅 (FR-PN15-01)
-- ============================================================
-- 사진 한 장에 여러 강아지가 찍히고, 그 사진이 각 보호자의 앨범으로 배분된다.
-- 다대다이므로 별도 테이블이다.
--
-- ⚠️ PC-29 동반 촬영 동의
--   단체 사진에 다른 강아지가 함께 찍히는 것이 기본이다. 이 사진을 보호자에게
--   배분하는 것과, 보호자가 커뮤니티로 내보내는 것(F-16)은 다른 동의다.
--   내보내기 시점에 enrollment → application → consented_items 의 촬영·공개 동의를
--   확인할 것 — 그 동의를 항목으로 분리해 둔 이유가 이것이다(V7 2번 주석).
CREATE TABLE media_tags (
    media_id      UUID NOT NULL REFERENCES merchant_media (id) ON DELETE CASCADE,
    enrollment_id UUID NOT NULL REFERENCES enrollments (id)    ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (media_id, enrollment_id)
);

-- [조회 성능] KG-11/KG-12 "내 강아지가 찍힌 사진만" — 보호자 앨범의 주 동선.
--   PK가 (media_id, ...) 선두라 반대 방향은 따로 색인한다.
CREATE INDEX idx_media_tags_enrollment ON media_tags (enrollment_id);


-- ============================================================
-- 6. merchant_announcements — 보호자 공지 (PN-16)
-- ============================================================
-- 일괄 푸시. 개별 연락(전화·앱 내 메시지)은 기존 채팅을 재사용하지 않는다 —
-- 채팅(chat_messages)은 friendship 에 매달려 있어 점주-보호자 관계를 표현할 수 없다.
CREATE TABLE merchant_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    title TEXT NOT NULL,
    body  TEXT NOT NULL,

    -- all: 전체 원생 보호자 / selected: 지정 원생만(target_enrollment_ids)
    -- expiring: 결제 임박 대상(PN-17에서 집계한 목록)
    audience TEXT NOT NULL DEFAULT 'all'
        CHECK (audience IN ('all', 'selected', 'expiring')),
    -- audience='selected' 일 때의 대상. 발송 시점 스냅샷이므로 FK를 걸지 않는다 —
    -- 이후 퇴원한 원생에게도 "보냈다"는 사실은 남아야 한다.
    target_enrollment_ids JSONB NOT NULL DEFAULT '[]'::jsonb,

    sent_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    sent_at TIMESTAMPTZ,
    recipient_count INTEGER, -- 실제 발송 대상 수(사후 검증용)

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_merchant_announcements_merchant
    ON merchant_announcements (merchant_id, created_at DESC);


-- ============================================================
-- 7. merchant_reviews — 리뷰 및 점주 답글 (PN-20, KG-02)
-- ============================================================
-- ★ 실제 이용자만 쓸 수 있다
--   reservation_id 를 필수로 걸어 "이용한 적 있는 사람의 리뷰"임을 스키마로 보장한다.
--   이게 없으면 경쟁 매장이나 무관한 사람이 평점을 흔들 수 있고, 그러면 KG-02의
--   리뷰가 견주에게 아무 신호도 못 준다.
--
-- 답글을 별도 테이블로 빼지 않는 이유: 리뷰 하나에 답글 하나이고 스레드가 아니다.
-- 1:1을 두 테이블로 나누면 조회마다 조인만 늘어난다.
CREATE TABLE merchant_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,

    -- 이용 근거. ⚠️ 예약이 지워져도 리뷰는 남아야 하므로 SET NULL 이지만,
    --    작성 시점에는 반드시 존재해야 한다(서버에서 검증).
    reservation_id UUID REFERENCES reservations (id) ON DELETE SET NULL,

    author_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    author_name_snapshot TEXT NOT NULL, -- 탈퇴 후에도 리뷰는 남는다

    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content TEXT,

    -- FR-PN20(화면목록): 점주 답글.
    reply_body TEXT,
    replied_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    replied_at TIMESTAMPTZ,

    -- SAFETY 도메인과 같은 soft delete. 신고 처리 이력을 보존해야 한다(PC/FR-CM03-03).
    deleted_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] KG-02 매장 상세의 리뷰 목록 · PN-20 리뷰 관리.
CREATE INDEX idx_merchant_reviews_merchant
    ON merchant_reviews (merchant_id, created_at DESC)
    WHERE deleted_at IS NULL;
-- [조회 성능] PN-20 "답글 안 단 리뷰" — 점주가 실제로 찾는 것은 이쪽이다.
CREATE INDEX idx_merchant_reviews_unanswered
    ON merchant_reviews (merchant_id, created_at DESC)
    WHERE reply_body IS NULL AND deleted_at IS NULL;

-- [비즈니스 규칙] 예약 한 건당 리뷰 하나.
CREATE UNIQUE INDEX uq_merchant_reviews_reservation
    ON merchant_reviews (reservation_id)
    WHERE reservation_id IS NOT NULL AND deleted_at IS NULL;
