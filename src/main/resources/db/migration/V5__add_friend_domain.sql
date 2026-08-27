-- V5: 강아지 친구 도메인 — FRIEND-1/2/3
--
-- 왜 지금인가:
--   추천(FRIEND-1) → 신청/수락(FRIEND-2) → 친구 목록(FRIEND-3)이 한 덩어리다.
--   추천 화면은 "이미 신청했는가 / 이미 친구인가"를 알아야 requestStatus를 내려줄 수
--   있으므로, 세 테이블이 동시에 있어야 첫 화면이 완성된다.
--
-- users.walk_time_slots를 여기 동봉하는 이유:
--   친구 추천의 2순위 신호(가중치 15)다. V3가 personality_traits를 FRIEND 착수 전에
--   넣은 것과 같은 이유 — 추천 알고리즘의 입력값은 추천을 만들기 전에 있어야 하고,
--   나중에 추가하면 기존 유저 전원 백필이 필요하다.
--
-- 최종형 출처: PawWalk-ios/docs/DB_SCHEMA.sql
-- 알고리즘 명세: PawWalk-ios/docs/FRIEND_RECOMMENDATION_SPEC.md


-- ============================================================
-- 1. users — 산책 시간대 (추천 신호, 2026-08-27 신설)
-- ============================================================
-- 왜 산책 기록 집계가 아니라 온보딩 선언값인가:
--   기록 누적을 기다리면 밀도 0인 초기에 신호가 죽어 있다. 선언값으로 받으면 첫
--   유저부터 작동하고, 기록이 쌓이면 실측으로 보정한다(2단계 설계).
--   성향 태그(personality_traits)와 동일한 판단이다.
--
-- 값 목록을 DB CHECK에 넣지 않는 이유:
--   슬롯 세트는 실사용 데이터로 바뀔 수 있고(의미가 겹치는 구간 병합 등), 문자열을
--   박아두면 바꿀 때마다 마이그레이션이 필요해진다. 값 유효성은 서버 enum이 맡고
--   DB는 개수만 방어한다 — personality_traits와 같은 분담.
--
-- 허용 값(서버 enum): dawn, morning, noon, afternoon, evening, night
ALTER TABLE users
    ADD COLUMN walk_time_slots JSONB NOT NULL DEFAULT '[]'::jsonb;

-- ⚠️ 최대 3개 제한은 추천 알고리즘의 전제다. time_slot_score는 중첩계수
--    |교집합| / min(|A|,|B|) 로 계산하므로, 상한이 없으면 6개를 전부 고른 유저가
--    모두와 1.0으로 겹쳐 추천 상위를 독식한다.
ALTER TABLE users
    ADD CONSTRAINT chk_users_walk_time_slots CHECK (
        CASE WHEN jsonb_typeof(users.walk_time_slots) = 'array'
             THEN jsonb_array_length(users.walk_time_slots) <= 3
             ELSE false
        END
        );


-- ============================================================
-- 2. friend_requests — 친구 신청 (FRIEND-2)
-- ============================================================
-- 신청은 강아지 단위로 하고, 수락 판단은 상대 견주가 한다.
-- 견주 단위가 아니라 강아지 단위인 이유: 다견종 유저가 "몽이의 친구"와 "초코의 친구"를
-- 구분할 수 있어야 하고, 추천 자체가 강아지 대 강아지로 이뤄지기 때문이다.
CREATE TABLE friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_dog_id UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    receiver_dog_id  UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,

    message TEXT, -- 신청 시 한마디(선택)

    -- 값 목록은 서버 enum과 이중 관리다. DB CHECK는 최후 방어선이고 400 응답은
    -- 서버가 낸다(reports.status와 같은 분담).
    status TEXT NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 수락/거절 시각. ⚠️ 수락까지 걸린 시간(responded_at - created_at)이
    --   추천 가중치를 조정할 유일한 근거 지표다 — FRIEND-2에서 반드시 채울 것.
    responded_at TIMESTAMPTZ,

    CONSTRAINT chk_friend_requests_self CHECK (requester_dog_id <> receiver_dog_id)
);

-- [비즈니스 규칙] 같은 쌍에 pending 신청이 중복으로 쌓이지 않게 막는다.
--   거절/취소 후 재신청은 허용한다 — 그래서 전체 UNIQUE가 아니라 부분 유니크다.
--   WHERE 절이 있어 UNIQUE 제약으로는 표현할 수 없고 부분 인덱스로만 만들 수 있다
--   (uq_reports_pending과 같은 구조).
--
--   ⚠️ 방향이 있는 제약이다. (A→B)와 (B→A)는 서로 다른 신청으로 허용된다 —
--      양쪽이 동시에 신청하는 것은 정상 상황이고, 둘 중 하나가 수락되면
--      friendships의 UNIQUE(dog_a_id, dog_b_id)가 중복을 막는다.
CREATE UNIQUE INDEX uq_friend_requests_pending
    ON friend_requests (requester_dog_id, receiver_dog_id)
    WHERE status = 'pending';

-- [조회 성능] "내가 받은 신청을 상태별로" — 받은 신청 목록의 주 동선.
CREATE INDEX idx_friend_requests_receiver ON friend_requests (receiver_dog_id, status);
-- [조회 성능] "내가 보낸 신청을 상태별로" + 추천 피드의 requestStatus 판정.
CREATE INDEX idx_friend_requests_requester ON friend_requests (requester_dog_id, status);


-- ============================================================
-- 3. friendships — 맺어진 친구 관계 (FRIEND-3)
-- ============================================================
-- 신청 수락 시 생성된다. 채팅(CHAT-1) 권한과 산책 약속(CHAT-2)의 기준이 되고,
-- 추천 피드에서 "이미 친구인 상대"를 제외하는 근거이기도 하다.
--
-- ★ 관계의 주체는 강아지가 아니라 견주다 (2026-08-27 결정).
--   신청은 강아지 단위로 하지만(추천이 강아지 단위이므로), 맺어진 관계는 견주 쌍에
--   유일하다. 강아지 쌍을 유일 단위로 잡으면 다견 견주에게서 곧바로 깨진다:
--
--     나: 몽이·초코,  상대: 두부
--     몽이→두부 수락 → friendship#1 → 채팅방 1
--     초코→두부 수락 → friendship#2 → 채팅방 2   ← 같은 두 사람에게 방이 2개
--
--   채팅(chat_messages.friendship_id)과 산책 약속(walk_appointments.friendship_id)이
--   전부 이 테이블에 매달리는데, 사람 둘이 만나는 약속이 어느 행에 붙어야 하는지
--   정할 근거가 없어진다. 차단(blocks)도 이미 견주 단위라 결이 어긋난다.
--
--   → 만남은 가구 단위로 일어난다. 강아지는 만남의 '이유'이자 '표현'이지
--     관계의 주체가 아니다.
--
--   ⚠️ 유일성의 결(grain)은 나중에 바꾸기가 매우 비싸다 — 이미 생긴 중복 행과
--      채팅방을 사후 병합해야 한다. 반대로 강아지 단위 세분화가 나중에 필요해지면
--      friendship_dogs 테이블을 '추가'하고 아래 dog_a_id/dog_b_id 에서 백필하면
--      된다. 어려운 쪽을 지금 맞추고 쉬운 쪽을 미루는 선택이다.
CREATE TABLE friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ⚠️ Service 계층에서 (user_a_id < user_b_id)로 정렬한 뒤 INSERT할 것.
    --    정렬하지 않으면 (A,B)와 (B,A)가 값이 다른 두 행이 되어 아래 UNIQUE가
    --    중복을 막아주지 못한다.
    user_a_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- 이 관계가 시작된 강아지 쌍. 유일성에는 관여하지 않고 표시·추천 이유 문장에 쓴다.
    -- ⚠️ user_a_id 정렬에 맞춰 dog_a_id 는 user_a_id 의 강아지여야 한다 —
    --    짝이 어긋나면 "누구의 강아지인지"를 다시 조인해서 알아내야 한다.
    dog_a_id  UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    dog_b_id  UUID NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,

    -- 어느 신청에서 비롯됐는지. 수락률·수락 소요시간 분석의 연결고리.
    -- MEET-2(스침 상호 확인)로 생긴 친구는 NULL — 경유 경로별 성과를 분리 측정할 수
    -- 있어야 하므로 nullable로 둔다.
    source_request_id UUID REFERENCES friend_requests (id) ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 견주 쌍 기준 유일. 같은 두 사람 사이에 관계는 하나뿐이다.
    CONSTRAINT uq_friendships_user_pair UNIQUE (user_a_id, user_b_id),
    CONSTRAINT chk_friendships_self_user CHECK (user_a_id <> user_b_id),
    CONSTRAINT chk_friendships_self_dog  CHECK (dog_a_id <> dog_b_id)
);

-- [조회 성능] "내 친구 목록" — 내가 a쪽일 수도 b쪽일 수도 있어 양쪽 모두 색인한다.
--   uq_friendships_user_pair 가 (user_a_id, ...) 선두 인덱스를 겸하므로
--   user_a 쪽 단독 인덱스는 만들지 않는다. b 쪽만 보완하면 된다.
CREATE INDEX idx_friendships_user_b ON friendships (user_b_id);
