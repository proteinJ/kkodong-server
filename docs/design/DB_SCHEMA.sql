-- 꼬동(KKODONG) Phase 1 DB Schema — PostgreSQL (Spring Boot + Supabase Postgres 호스팅)
-- 코드네임/리포지토리는 PawWalk 유지, 서비스명만 "꼬동"(2026-08-15 결정)
--
-- 2026-08-15: 기획 v3(KKODONG_CONCEPT.md) 전면 반영해 재작성.
--   주요 변경 — 핵심 기능이 "산책 모집글 매칭"에서 "강아지 친구 추천/신청"으로 바뀜:
--     · walk_posts / walk_post_requests  →  삭제 (모집글 개념 자체가 v3에 없음)
--     · connections                      →  friendships 로 개명 + 강아지 단위 정보 추가
--     · friend_requests, walk_appointments 신설 (친구 신청 → 수락 → 산책 약속 루프)
--     · dogs 에 personality_traits(성향 태그), cutout_image_url(누끼) 추가 — 추천 알고리즘
--       + 브랜드 자산의 핵심 컬럼
--     · walk_bathroom_logs 신설 (산책 중 똥/오줌 버튼 기록), walk_sessions 에 집계 컬럼
--     · saved_routes 신설 (지난 산책 경로 다시보기/즐겨찾기)
--     · community_posts / community_comments / community_post_likes 신설 (자유 커뮤니티)
--     · vaccination_records → care_records 로 확장 (예방접종 차수 + 심장사상충 + 구충 + 건강검진)
--
-- 이전 이력:
--   2026-07-08 최초 작성, 백엔드 아키텍처 변경(Spring Boot 자체 서버, Supabase는 DB 호스팅만)
--   2026-07-09 Refresh Token은 Postgres가 아닌 **Redis** 저장으로 정정(관련 테이블 없음)
--   2026-07-09 위치 컬럼 PostGIS geography 전환
--
-- 용도: **현재 목표 스키마의 기준 문서(desired state)**.
--
-- ⚠️ 이 파일은 적용 대상이 아니다. 실제 스키마는 Flyway 마이그레이션이 만든다.
--    (`kkodong-server/src/main/resources/db/migration/`. 2026-08-19에 서버 리포지토리
--     이름이 `server` → `kkodong-server`로 바뀌었다.)
--    Flyway는 적용된 마이그레이션의 체크섬을 검증하므로 이미 적용된 파일은
--    주석 한 글자도 고칠 수 없다. 변경분은 항상 새 버전 파일로 만든다.
--
-- 적용 현황 (2026-08-27):
--   V1__init.sql                  v2 베이스라인
--   V2__add_user_home_location.sql users.home_location + GiST 인덱스
--   V3__v3_baseline.sql            v2 죽은 테이블 9개 DROP
--                                  + dogs(+personality_traits, +cutout_image_url, +weight_kg)
--                                  + users(+kakao_user_id, +google_user_id, +phone_number,
--                                          +phone_verified_at)
--   V4__add_reports.sql            reports 재생성(다형 참조) + uq_reports_pending
--   V5__add_friend_domain.sql      users.walk_time_slots + friend_requests + friendships
--   → 다음 마이그레이션은 **V6**부터.
--
--   ⚠️ V3는 DROP만 했고 CREATE는 하지 않았다 — "지운 테이블의 최종형은 해당 도메인
--      착수 시점에 새로 만든다"가 V3의 판단이었기 때문이다. 그래서 아래 목표 스키마
--      중 아직 DB에 없는 테이블이 많다.
--
-- 아직 만들어지지 않은 테이블(착수 시점의 마이그레이션에서 생성):
--   walk_appointments, chat_messages*, walk_sessions*, walk_bathroom_logs, saved_routes,
--   walk_encounters, walk_cards*, community_posts, community_comments,
--   community_post_likes, care_records, subscriptions*
--   (* 표시는 V1에 있었다가 V3에서 DROP된 것 — 최종형으로 다시 만든다)
--
-- 현재 DB에 실제로 있는 테이블: users, dogs, service_areas, waitlist_signups,
--   device_tokens, blocks, reports, friend_requests, friendships
--
--    users.display_name은 server 쪽에서 이미 nullable로 조정됨(가입 시점엔 비워두고
--    온보딩에서 채우는 설계) — 아래 DDL도 그에 맞춰 NOT NULL을 뺐다.
--
-- ERDCloud 가져오는 법:
--   1. erdcloud.com에서 새 ERD 생성 → 2. "가져오기(Import)" → "SQL Import"
--   3. Dialect: PostgreSQL → 4. 이 파일 전체 붙여넣기
--
-- 설계 원칙:
--   - **Phase 1 범위만 DDL로 작성한다.** 매장/예약/점주앱(Phase 3), 커머스(Phase 4)
--     도메인은 이 파일 맨 아래 "Phase 2+ 예정 도메인"에 설계 개요만 주석으로 남긴다
--     (KKODONG_CONCEPT.md 5절 로드맵 참조)
--   - enum은 CHECK 제약으로 표현 (커스텀 TYPE은 ERD 임포터 호환성 낮음)
--   - 권한 검증(RLS)은 안 씀 — Spring Boot Service 계층 코드에서 처리
--     (DEV_STACK_PIPELINE.md 2절 참조)
--   - 위치 컬럼은 PostGIS geography(Point,4326) 사용
--     ⚠ PostGIS는 좌표 순서가 (경도 lng, 위도 lat)이다 — 사람이 흔히 쓰는 "위도,경도"
--       순서와 반대라 실수하기 쉬우니 ST_MakePoint(lng, lat) 순서를 항상 확인할 것
--   - 이 스키마를 실행하는 Postgres에 PostGIS 확장이 먼저 설치되어 있어야 함:
--       CREATE EXTENSION IF NOT EXISTS postgis;
--     Supabase 프로젝트는 대시보드 Database → Extensions에서 postgis 토글 On으로 설치 가능

CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- 유저
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT, -- bcrypt 해시. 소셜 로그인 전용 계정이면 NULL 가능
    apple_user_id TEXT UNIQUE,  -- Sign in with Apple 고유 식별자(sub claim)
    kakao_user_id TEXT UNIQUE,  -- 2026-08-15 추가 (v2 8절 가입수단 확장 결정). 기술 명세는 QUESTIONS.md Q7
    google_user_id TEXT UNIQUE, -- 2026-08-15 추가 (동상)
    phone_number TEXT,          -- 전화번호 인증(모든 가입수단 공통). 인증 방식 미정 — QUESTIONS.md Q7
    phone_verified_at TIMESTAMPTZ,
    display_name TEXT, -- 가입 시점엔 NULL, 온보딩에서 채움
    -- 2026-07-09 추가. DB_SCHEMA.sql 원본에는 없었고, 보일러플레이트의 Spring Security
    -- 인증 패턴을 그대로 쓰기 위해 V1__init.sql에서 들어왔다. 제품 기능은 아니다.
    role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    profile_image_url TEXT,
    -- 패스권(구독) 등급. 'free' 외 등급은 강아지 추가 등록 + 산책카드 커스터마이징 해제
    -- (KKODONG_CONCEPT.md 4절 ① 축). 상품 구성/가격은 QUESTIONS.md Q13
    subscription_tier TEXT NOT NULL DEFAULT 'free' CHECK (subscription_tier IN ('free', 'plus', 'pro')),
    -- 견주의 대략적 위치("집 근처"). V2__add_user_home_location.sql로 이미 적용됨.
    -- 설계 방향(2026-07-19, v3에서도 유지):
    --   · 위치는 강아지가 아니라 견주에 귀속 — 다견종 유저도 위치 1개로 충분
    --   · GPS 실시간 추적이 아니라 온보딩 시 한 번 설정하는 값
    --   · 원본 좌표는 정밀하게 저장하고, 흐림(fuzzing)은 API 응답 직렬화 단계에서
    --     반올림/지터로 처리 — 흐린 좌표를 별도 컬럼으로 중복 저장하지 않는다
    -- ★ v3에서 이 컬럼이 친구 추천의 "거리" 신호 기준점이 된다(KKODONG_CONCEPT.md 2.1)
    home_location GEOGRAPHY(POINT, 4326), -- nullable — 온보딩 전에는 미설정
    -- 주로 산책하는 시간대 (2026-08-27 신설 — 친구 추천의 2순위 신호, 가중치 15)
    -- 왜 산책 기록이 아니라 온보딩 선언값인가:
    --   기록 누적을 기다리면 밀도 0에서 작동하지 않는다. 선언값으로 받으면 즉시 켜지고,
    --   기록이 쌓이면 실측으로 보정한다(2단계). 성향 태그와 동일한 방식.
    -- 값 유효성은 서버 enum, 개수 제한(≤3)은 아래 CHECK — 성향 태그와 같은 분담이다.
    --   태그 세트는 실사용 데이터로 바뀔 수 있어 DB에 문자열을 박아두지 않는다.
    -- ⚠️ 최대 3개 제한은 알고리즘의 전제다. 상한이 없으면 6개를 전부 고른 유저가
    --    모두와 100% 겹쳐(중첩계수 1.0) 추천 상위를 독식한다.
    walk_time_slots JSONB NOT NULL DEFAULT '[]'::jsonb,
    onboarding_completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 최소 하나의 로그인 수단 필요.
    -- ⚠️ 현재 DB의 실제 제약은 V1__init.sql의 `password_hash IS NOT NULL OR
    --    apple_user_id IS NOT NULL`뿐이다. V3가 kakao/google 컬럼만 추가하고 CHECK는
    --    손대지 않았다 — 소셜 로그인 API가 없어 해당 컬럼만 채워진 행이 생길 수
    --    없으므로 급하지 않다는 판단. 카카오/구글 로그인(QUESTIONS.md Q7) 착수 시
    --    CHECK를 아래 형태로 교체하는 마이그레이션이 함께 필요하다.
    CHECK (
        password_hash IS NOT NULL OR apple_user_id IS NOT NULL
        OR kakao_user_id IS NOT NULL OR google_user_id IS NOT NULL
    ),
    CONSTRAINT chk_users_walk_time_slots CHECK (
        CASE WHEN jsonb_typeof(walk_time_slots) = 'array'
             THEN jsonb_array_length(walk_time_slots) <= 3
             ELSE false END
    )
);

-- ============================================================
-- 인증: Refresh Token 관리 — 2026-07-09 정정
-- RT는 Postgres가 아닌 **Redis**에 저장하고, 로그아웃 시 AT를 Redis 블랙리스트에
-- 등록한다(기존 템플릿 github.com/proteinJ/runApp 패턴). Postgres에 RT 테이블 없음.
-- Access Token은 stateless로 서명만 검증(블랙리스트 조회는 Redis).
-- ============================================================

-- ============================================================
-- 반려견 — 친구 추천 알고리즘의 입력값이 전부 여기 모인다
-- (KKODONG_CONCEPT.md 2.1 추천 신호)
-- ============================================================

CREATE TABLE dogs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    breed TEXT,                                    -- 추천 신호: 견종
    birth_date DATE,                               -- 추천 신호: 나이
    gender TEXT CHECK (gender IN ('male', 'female')),
    size TEXT CHECK (size IN ('small', 'medium', 'large')), -- 추천 신호: 체급(안전)
    weight_kg NUMERIC,                             -- 2026-08-15 추가. 예약 시 매장에 전달(Phase 3)
    energy_level TEXT CHECK (energy_level IN ('low', 'medium', 'high')),
    -- 2026-08-15 추가. 추천 신호: 성격(가중치 20 — 2026-08-27 시간대 신설로 25에서 조정).
    -- 온보딩에서 견주가 선택한 성향 태그.
    -- 확정 태그 8종: 활발함/차분함/사교적/낯가림/겁많음/장난꾸러기/독립적/애교많음
    -- (QUESTIONS.md Q10 해결, 상세 FRIEND_RECOMMENDATION_SPEC.md 1절)
    --
    -- ⚠ 개수 제한만 DB CHECK로 걸고, **태그 값 유효성은 서버 설정값에서 검증**한다
    --   (`kkodong.dog.personality.tags` — enum이 아니라 application.yml이다).
    --   태그 세트는 실사용 데이터에 따라 바뀔 수 있는데(예: 의미가 겹치는 태그 병합),
    --   문자열을 DB 제약에 박아두면 바꿀 때마다 마이그레이션이 필요해진다.
    personality_traits JSONB NOT NULL DEFAULT '[]'::jsonb,
    neutered BOOLEAN,
    profile_image_url TEXT,     -- 원본 사진
    cutout_image_url TEXT,      -- 2026-08-15 추가. 누끼(배경제거) 이미지.
                                -- 온보딩에서 온디바이스(Vision)로 생성 후 업로드.
                                -- 프로필/산책카드/추천피드에 공통 사용 (KKODONG_CONCEPT.md 3.5)
    animal_registration_number TEXT, -- 표시용만, API 연동 없음 (QUESTIONS.md Q1)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 성향 태그는 배열이어야 하고 최대 3개 (FRIEND_RECOMMENDATION_SPEC.md 1절).
    -- CASE로 감싼 이유: jsonb_array_length()는 인자가 배열이 아니면 false를 돌려주는 게
    -- 아니라 에러를 낸다. Postgres는 AND의 단축 평가 순서를 보장하지 않으므로
    -- 타입 검사를 먼저 통과시키려면 CASE(평가 순서 보장)를 써야 한다.
    CONSTRAINT chk_dogs_personality_traits CHECK (
        CASE WHEN jsonb_typeof(personality_traits) = 'array'
             THEN jsonb_array_length(personality_traits) <= 3
             ELSE false
        END
    )
);

CREATE INDEX idx_dogs_owner ON dogs (owner_id);
CREATE INDEX idx_users_home_location ON users USING GIST (home_location);

-- 친구 추천 후보 조회 예시.
-- 거리 기준점은 견주의 users.home_location (강아지별 위치 테이블을 따로 두지 않는다 —
-- 다견종 유저도 위치 1개면 충분, V2 마이그레이션 설계 방향 유지).
-- ⚠ 하드 필터 금지 — 반경만 WHERE 조건이고 성격/견종/나이는 전부 ORDER BY 가중치다:
--   SELECT d.* FROM dogs d JOIN users u ON u.id = d.owner_id
--   WHERE u.id <> :me
--     AND ST_DWithin(u.home_location, ST_SetSRID(ST_MakePoint(:lng,:lat),4326)::geography, :radius_m)
--   ORDER BY <가중치 점수> DESC LIMIT :n
-- 후보가 :n 미만이면 :radius_m 을 단계적으로 확장해 재조회 (KKODONG_CONCEPT.md 2.2)

-- ============================================================
-- 서비스 지역 게이팅 (ONBOARD-2)
-- ⚠ v3에서 필요성이 약해졌다 — 반경 자동 확장이 "후보 0" 문제를 흡수하므로 하드
--   게이팅을 유지할지 재검토 중(QUESTIONS.md Q8). 테이블은 유지하되 게이팅 정책은
--   Q8 결정에 따른다.
-- ============================================================

CREATE TABLE service_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL, -- 예: '이촌동'
    center_location GEOGRAPHY(POINT, 4326) NOT NULL, -- ST_MakePoint(lng, lat) 순서 주의
    radius_meters INTEGER NOT NULL DEFAULT 2000,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'planned')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_areas_location ON service_areas USING GIST (center_location);

CREATE TABLE waitlist_signups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL,
    location GEOGRAPHY(POINT, 4326), -- nullable, 위치 미제공 대기등록 허용
    nearest_service_area_id UUID REFERENCES service_areas (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 반경 검색 대상이 아니라(집계용) GiST 인덱스는 생략

-- ============================================================
-- ★ 핵심 기능: 강아지 친구 (FRIEND-1~3)
-- 추천 → 신청 → 수락 → 친구 → 채팅 → 산책 약속
-- ============================================================

-- 친구 신청. 강아지 단위로 신청하고, 승낙 판단은 상대 견주가 한다.
CREATE TABLE friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_dog_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    receiver_dog_id  UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    message TEXT, -- 신청 시 한마디(선택)
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_friend_requests_self CHECK (requester_dog_id <> receiver_dog_id)
);

-- 같은 쌍에 pending 신청이 중복으로 쌓이지 않게 막는다(거절/취소 후 재신청은 허용).
-- ⚠️ 방향이 있는 제약이다. (A→B)와 (B→A)는 서로 다른 신청으로 허용된다 — 양쪽이 동시에
--    신청하는 것은 정상 상황이고, 둘 중 하나가 수락되면 friendships의
--    uq_friendships_user_pair가 중복을 막는다.
CREATE UNIQUE INDEX uq_friend_requests_pending
    ON friend_requests (requester_dog_id, receiver_dog_id)
    WHERE status = 'pending';
-- "내가 받은 신청을 상태별로" — 받은 신청 목록의 주 동선.
CREATE INDEX idx_friend_requests_receiver ON friend_requests (receiver_dog_id, status);
-- "내가 보낸 신청을 상태별로" + 추천 피드의 requestStatus 판정.
CREATE INDEX idx_friend_requests_requester ON friend_requests (requester_dog_id, status);

-- 신청 수락 시 생성되는 친구 관계.
-- 채팅(CHAT-1) 권한과 산책 약속(CHAT-2)의 기준이 된다.
-- (구 `connections` 테이블을 개명·확장한 것)
--
-- ★ 관계의 주체는 강아지가 아니라 견주다 (2026-08-27 결정).
--   신청은 강아지 단위지만(추천이 강아지 단위이므로) 관계는 견주 쌍에 유일하다.
--   강아지 쌍을 유일 단위로 잡으면 다견 견주에게서 곧바로 깨진다 —
--   몽이→두부, 초코→두부가 각각 수락되면 같은 두 사람에게 채팅방이 2개 생기고,
--   사람 둘이 만나는 산책 약속이 어느 행에 붙어야 하는지 정할 근거가 없어진다.
--   blocks 가 이미 견주 단위라는 점과도 결이 맞는다.
--
--   ⚠️ 유일성의 결은 나중에 바꾸기가 매우 비싸다(중복 행·채팅방 사후 병합).
--      강아지 단위 세분화가 필요해지면 friendship_dogs 를 '추가'하고
--      dog_a_id/dog_b_id 에서 백필하면 된다 — 어려운 쪽을 먼저 맞춘다.
CREATE TABLE friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- ⚠️ Service 계층에서 (user_a_id < user_b_id) 정렬 후 INSERT.
    user_a_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 관계가 시작된 강아지 쌍. 유일성에는 관여하지 않고 표시·이유 문장에 쓴다.
    -- dog_a_id 는 user_a_id 의 강아지여야 한다(짝 정렬).
    dog_a_id  UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    dog_b_id  UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    -- MEET-2(스침 상호 확인)로 생긴 친구는 NULL — 경로별 성과 분리 측정용
    source_request_id UUID REFERENCES friend_requests (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friendships_user_pair UNIQUE (user_a_id, user_b_id),
    CONSTRAINT chk_friendships_self_user CHECK (user_a_id <> user_b_id),
    CONSTRAINT chk_friendships_self_dog  CHECK (dog_a_id <> dog_b_id)
);

-- uq_friendships_user_pair 가 user_a 선두 인덱스를 겸하므로 b 쪽만 보완한다.
CREATE INDEX idx_friendships_user_b ON friendships (user_b_id);

-- 산책 약속. 친구 루프의 목적지 — 채팅으로만 두면 실제 만남으로 이어지지 않으므로
-- 1급 기능으로 분리한다 (KKODONG_CONCEPT.md 2.3 ⑤)
CREATE TABLE walk_appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    friendship_id UUID NOT NULL REFERENCES friendships (id) ON DELETE CASCADE,
    proposed_by_user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scheduled_at TIMESTAMPTZ NOT NULL,
    place_name TEXT,
    location GEOGRAPHY(POINT, 4326), -- ST_MakePoint(lng, lat) 순서 주의
    status TEXT NOT NULL DEFAULT 'proposed'
        CHECK (status IN ('proposed', 'accepted', 'declined', 'cancelled', 'completed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ
);

CREATE INDEX idx_walk_appointments_friendship ON walk_appointments (friendship_id, scheduled_at);

-- ============================================================
-- 채팅 (CHAT-1, Phase 1은 1:1만 — 그룹 채팅은 Phase 2)
-- ============================================================

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    friendship_id UUID NOT NULL REFERENCES friendships (id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users (id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_friendship ON chat_messages (friendship_id, created_at DESC);

-- ============================================================
-- 푸시 알림
-- 채팅/친구신청/약속/케어 D-day 원격 푸시(APNs)의 전제 조건. API_SPEC.md 5절 참조.
-- ============================================================

CREATE TABLE device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    apns_token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'ios' CHECK (platform IN ('ios')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, apns_token)
);

-- ============================================================
-- 산책 기록 (WALK-1~3)
-- ============================================================

CREATE TABLE walk_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dog_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    -- 친구와 함께한 산책이면 연결, 혼자 산책이면 NULL
    walk_appointment_id UUID REFERENCES walk_appointments (id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    distance_meters NUMERIC,
    route JSONB, -- [{lat, lng, ts}, ...] 경로 포인트 배열. PostGIS LINESTRING으로
                 -- 바꾸지 않은 이유: 포인트별 시각(ts)이 필요한데 기본 PostGIS 지오메트리는
                 -- 정점별 타임스탬프를 자연스럽게 못 담음(재생/타임랩스 용도로 JSONB가 더 실용적)
    -- 2026-08-15 추가. 배변 횟수 집계값 — 원본은 walk_bathroom_logs.
    -- 산책 카드/피드가 매번 COUNT 하지 않도록 비정규화해 둔다(로그 INSERT 시 함께 갱신).
    poop_count INTEGER NOT NULL DEFAULT 0,
    pee_count  INTEGER NOT NULL DEFAULT 0,
    weather_condition TEXT,
    weather_temp_celsius NUMERIC,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_walk_sessions_dog ON walk_sessions (dog_id, started_at DESC);

-- 2026-08-15 신설. 산책 중 똥/오줌 버튼을 누른 기록.
-- 단순 카운터가 아니라 이벤트로 저장하는 이유: 시각·위치가 남아야 배변 빈도 변화
-- (건강 지표)와 지도 표시로 확장할 수 있다. 카운터는 walk_sessions의 집계 컬럼.
CREATE TABLE walk_bathroom_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    walk_session_id UUID NOT NULL REFERENCES walk_sessions (id) ON DELETE CASCADE,
    -- 클라이언트가 생성하는 멱등키. 산책 중 네트워크가 끊겨 오프라인 큐를 재전송할 때
    -- 같은 기록이 두 번 들어가 카운트가 중복 증가하는 것을 막는다(API_SPEC.md 5.2).
    client_log_id TEXT,
    type TEXT NOT NULL CHECK (type IN ('poop', 'pee')),
    location GEOGRAPHY(POINT, 4326), -- nullable(GPS 순간 불가 대비)
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_walk_bathroom_logs_session ON walk_bathroom_logs (walk_session_id);
-- 멱등키가 있는 요청만 중복 차단(NULL은 제약 대상 아님 → 부분 유니크 인덱스)
CREATE UNIQUE INDEX uq_walk_bathroom_logs_client
    ON walk_bathroom_logs (walk_session_id, client_log_id)
    WHERE client_log_id IS NOT NULL;

-- 2026-08-15 신설. 지난 산책 경로 저장/즐겨찾기 (KKODONG_CONCEPT.md 3.2).
-- 밀도와 무관하게 유저 1명이어도 가치가 생기므로 Phase 1.
-- "동네 인기 경로 추천"(밀도 의존)은 Phase 2 — 아래 Phase 2+ 섹션 참조.
CREATE TABLE saved_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    dog_id UUID REFERENCES dogs (id) ON DELETE SET NULL,
    source_session_id UUID REFERENCES walk_sessions (id) ON DELETE SET NULL,
    name TEXT NOT NULL, -- 유저가 붙인 이름. 예: '한강 코스'
    path JSONB NOT NULL, -- walk_sessions.route 스냅샷
    distance_meters NUMERIC,
    -- 반경 검색(이 근처에 저장한 경로 있나)용 시작점
    start_location GEOGRAPHY(POINT, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_saved_routes_user ON saved_routes (user_id);
CREATE INDEX idx_saved_routes_start ON saved_routes USING GIST (start_location);

-- ============================================================
-- 산책 중 '스침' — MEET-2 (2026-08-27 신설)
-- 산책 종료 후 "오늘 만난 강아지가 있나요?"의 후보와 상호 확인 결과를 담는다.
--
-- 왜 필요한가: 친구 신청 퍼널은 신청 단계에서 대부분 이탈한다(소개팅 앱과 같은
--   심리 장벽). 이미 실제로 마주친 상대끼리 양쪽이 서로를 고를 때만 연결하면
--   거절이라는 개념 자체가 발생하지 않는다. 상세: MARKET_ANALYSIS.md 4절 ②.
--
-- ⚠️ 스침의 시각·좌표는 저장하되 **API 응답에 절대 포함하지 않는다.** 이동 이력이라
--    노출되는 순간 home_location 흐림이 무의미해진다.
-- ============================================================

CREATE TABLE walk_encounters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    walk_session_id UUID NOT NULL REFERENCES walk_sessions (id) ON DELETE CASCADE,

    -- 스침의 두 당사자. dog_a_id < dog_b_id 로 정렬해 저장한다 —
    -- 안 그러면 (A,B)와 (B,A)가 별개 행이 되어 상호 확인 판정이 깨진다.
    dog_a_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    dog_b_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,

    -- 판정 근거(디버깅·임계치 튜닝용). 응답 직렬화에서 제외할 것.
    encountered_at TIMESTAMPTZ NOT NULL,
    tile_id BIGINT NOT NULL,

    -- 상호 확인 상태. 양쪽이 모두 true 가 될 때만 friendships 가 생성된다.
    -- ⚠️ 한쪽만 true 인 사실은 어떤 API 로도 상대에게 노출하지 않는다 —
    --    노출되는 순간 '거절'이 생기고 이 설계의 존재 이유가 사라진다.
    confirmed_by_a BOOLEAN NOT NULL DEFAULT false,
    confirmed_by_b BOOLEAN NOT NULL DEFAULT false,
    matched_at TIMESTAMPTZ,  -- 양쪽 확인이 성립한 시각

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_walk_encounters_order CHECK (dog_a_id < dog_b_id)
);

-- 같은 산책에서 같은 쌍이 여러 번 스쳐도 행은 하나다(오가며 여러 번 마주치는 게 정상).
CREATE UNIQUE INDEX uq_walk_encounters
    ON walk_encounters (walk_session_id, dog_a_id, dog_b_id);

-- "이 산책의 스침 후보를 뽑아라" — 산책 종료 직후의 유일한 조회 동선.
CREATE INDEX idx_walk_encounters_session ON walk_encounters (walk_session_id);

-- ============================================================
-- 산책 완료 카드 (CARD-1, CARD-2) — 바이럴 엔진
-- ============================================================

CREATE TABLE walk_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    walk_session_id UUID NOT NULL REFERENCES walk_sessions (id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    -- 패스권 커스터마이징 대상 (수익모델 ① 축). Free는 'default'만 허용 — 서버 검증
    template_style TEXT NOT NULL DEFAULT 'default',
    shared_at TIMESTAMPTZ, -- SNS 공유 시점 (바이럴 측정용)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 커뮤니티 (COMMUNITY-1) — 2026-08-15 신설
-- v2에서 NO-GO였다가 v3에서 되살아난 영역. 범용 커뮤니티가 아니라 동네/친구 맥락
-- 기반으로 노출 우선순위를 두는 게 차별점 (KKODONG_CONCEPT.md 3.4)
-- ============================================================

CREATE TABLE community_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- 동네 기반 노출용. NULL이면 전체 공개 글
    service_area_id UUID REFERENCES service_areas (id),
    title TEXT,
    content TEXT NOT NULL,
    image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    like_count INTEGER NOT NULL DEFAULT 0,    -- 비정규화 집계
    comment_count INTEGER NOT NULL DEFAULT 0, -- 비정규화 집계
    deleted_at TIMESTAMPTZ, -- soft delete(신고 처리/작성자 삭제 이력 보존)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_community_posts_area ON community_posts (service_area_id, created_at DESC);
CREATE INDEX idx_community_posts_author ON community_posts (author_id, created_at DESC);

CREATE TABLE community_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_comment_id UUID REFERENCES community_comments (id) ON DELETE CASCADE, -- 대댓글
    content TEXT NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_community_comments_post ON community_comments (post_id, created_at);

CREATE TABLE community_post_likes (
    post_id UUID NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

-- ============================================================
-- 반려견 케어 (PET-2) — 2026-08-15 확장
-- 구 `vaccination_records`를 `care_records`로 일반화: 예방접종 차수뿐 아니라
-- 심장사상충 예방약(월 1회 반복), 구충제, 건강검진(연 1회)까지 한 테이블에서 관리
-- (KKODONG_CONCEPT.md 3.6). 입력은 전부 유저 수동 — 동물등록번호 API 연동 폐기(Q1)
-- ============================================================

CREATE TABLE care_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dog_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    care_type TEXT NOT NULL CHECK (
        care_type IN ('vaccination', 'heartworm', 'deworming', 'checkup', 'other')
    ),
    -- 세부 항목명. 예: '종합백신', '광견병', '켄넬코프'
    care_name TEXT NOT NULL,
    -- 접종 차수(1차, 2차...). 반복형(심장사상충 등)이면 NULL
    dose_sequence INTEGER,
    administered_date DATE NOT NULL,
    -- D-day 알림 기준일. 앱이 기본 주기 템플릿으로 계산해 제안하고 유저가 수정 가능
    next_due_date DATE,
    -- 반복 주기(일). 예: 심장사상충 30. 1회성이면 NULL
    repeat_interval_days INTEGER,
    memo TEXT,
    reminder_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_care_records_dog_due ON care_records (dog_id, next_due_date);

-- ============================================================
-- 안전: 차단 & 신고 (SAFETY-1, SAFETY-2)
-- ============================================================

CREATE TABLE blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

-- 신고 대상이 v3에서 늘었다: 유저뿐 아니라 커뮤니티 글/댓글도 신고 대상이다.
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type TEXT NOT NULL CHECK (target_type IN ('user', 'community_post', 'community_comment')),
    target_id UUID NOT NULL, -- target_type에 따라 users/community_posts/community_comments의 id
    reason TEXT NOT NULL CHECK (
        reason IN ('inappropriate_behavior', 'safety_concern', 'harassment', 'fake_profile', 'spam', 'other')
    ),
    details TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'reviewed', 'action_taken', 'dismissed')),
    -- Phase 1은 별도 관리자 UI 없음 — 개발자가 Supabase 대시보드에서 직접 review/status 갱신
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "처리 대기 중인 신고를 최신순으로" — Phase 1의 유일한 신고 처리 동선.
--   등호로 거르는 status가 앞, 정렬에 쓰는 created_at이 뒤여야 별도 Sort가 생략된다.
CREATE INDEX idx_reports_status ON reports (status, created_at);

-- 한 사람이 같은 대상을 처리 대기 중에 두 번 신고할 수 없다(처리 완료 후 재신고는 허용).
-- WHERE 절이 있어 UNIQUE 제약으로는 표현할 수 없고 부분 유니크 인덱스로만 만들 수 있다.
-- 부수 효과로 인덱스가 훨씬 작아진다(신고 대부분은 처리 완료 상태라 색인에서 빠진다).
CREATE UNIQUE INDEX uq_reports_pending
    ON reports (reporter_id, target_type, target_id)
    WHERE status = 'pending';

-- ============================================================
-- 패스권 / 구독 (수익 모델 ① 축)
-- ============================================================

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tier TEXT NOT NULL CHECK (tier IN ('plus', 'pro')),
    apple_transaction_id TEXT UNIQUE, -- StoreKit 2 원본 거래 ID
    product_id TEXT,                  -- App Store Connect 상품 ID (QUESTIONS.md Q13)
    started_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_user ON subscriptions (user_id, status);

-- ============================================================
-- Phase 2+ 예정 도메인 — 설계 개요만 (DDL 미작성)
-- Phase 1 스키마를 가볍게 유지하기 위해 실제 테이블은 해당 Phase 착수 시 추가한다.
-- 로드맵: KKODONG_CONCEPT.md 5절
-- ============================================================
--
-- [Phase 2 — 동네 밀도]
--   popular_routes      : 동네별 인기 산책로 집계(service_area_id, path, walk_count,
--                         avg_distance, last_aggregated_at). walk_sessions.route를
--                         배치로 집계. 점진 활성화 임계치는 QUESTIONS.md Q9
--   group_walks         : 그룹 산책(여러 friendship이 한 산책에 참여)
--
-- [Phase 3 — 매장 생태계 / 꼬동 파트너]
--   stores              : 매장(동물병원/미용/카페/유치원/훈련소/호텔/펫샵),
--                         location GEOGRAPHY(POINT,4326) + GiST 인덱스, 영업시간, 사진
--   store_owners        : 점주 계정(users와 분리할지 role로 둘지 — QUESTIONS.md Q11)
--   store_reviews       : 견주 평점/리뷰 + 점주 답글
--   reservations        : 예약. **등록된 반려견 정보(dogs)를 스냅샷으로 함께 저장**해
--                         점주가 예약 시점 정보를 그대로 보게 한다(사후 프로필 수정과 무관하게)
--   store_promotions    : 매장 소식/이벤트/쿠폰
--
-- [Phase 4 — 커머스]
--   sellers, products, product_options, carts, orders, order_items, settlements
--   → 중개 플랫폼이므로 정산(settlements)과 PG 연동이 핵심. QUESTIONS.md Q12
