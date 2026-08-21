-- V3: 기획 v3(KKODONG_CONCEPT.md) 반영
--
-- 왜 이 마이그레이션이 필요한가:
--   핵심 기능이 "산책 모집글 매칭"(v2)에서 "강아지 친구 추천 → 신청 → 수락"(v3)으로
--   바뀌었다. 모집글 도메인이 통째로 사라지고 friend_requests/friendships가 대체한다.
--   또한 dogs.personality_traits는 친구 추천 알고리즘의 25점짜리 입력 신호라
--   FRIEND-1 착수 전에 반드시 있어야 한다 — 나중에 추가하면 기존 유저 전원 백필 필요.
--
-- DROP이 안전한 근거:
--   대상 테이블 전부 대응하는 JPA 엔티티가 없다. 즉 애플리케이션이 단 한 줄도
--   INSERT한 적이 없어 데이터가 0건이다. 현재 구현된 도메인은 users(User)와
--   dogs(Dog) 둘뿐이며, 이 둘은 건드리지 않고 컬럼만 추가한다.
--
-- DROP 대상의 세 부류:
--   (1) v3에서 개념 자체가 소멸 — walk_posts, walk_post_requests
--       connections → friendships(강아지 단위로 확장), vaccination_records → care_records
--   (2) FK 대상이 사라져 연쇄로 정리 — chat_messages(→connections),
--       walk_sessions(→walk_posts), walk_cards(→walk_sessions).
--       최종형은 friendships/walk_appointments를 참조하는데, 그 테이블들은
--       FRIEND/CHAT 도메인 착수 시점에 만든다
--   (3) ⚠ ALTER로도 가능했지만 DROP을 택함 — reports, subscriptions.
--       DB_SCHEMA.sql 상단 지침(ALTER)과 다른 선택이다. 착수 시점이 멀어서
--       (reports=SAFETY-2, subscriptions=PASS-1) 지금 모양을 확정해봐야 그때 또
--       바뀔 가능성이 높고, Flyway는 적용된 파일을 수정할 수 없다.
--       해당 도메인 착수 시 DB_SCHEMA.sql의 최종형으로 새로 CREATE한다
--
-- 지운 테이블들의 최종형은 PawWalk-ios/docs/DB_SCHEMA.sql 참조.
-- 이 파일은 스키마를 "v3 출발점"까지만 정리한다 — 신규 테이블 생성은
-- 각 도메인 착수 시점의 마이그레이션(V4~)에서.


-- ============================================================
-- 1. v2 도메인 정리 — 참조하는 쪽부터
-- ============================================================
-- 순서 근거: walk_cards → walk_sessions → walk_posts 순으로 FK가 걸려 있다
DROP TABLE IF EXISTS walk_cards;
DROP TABLE IF EXISTS walk_sessions;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS connections;
DROP TABLE IF EXISTS walk_post_requests;
DROP TABLE IF EXISTS walk_posts;
DROP TABLE IF EXISTS vaccination_records;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS subscriptions;


-- ============================================================
-- 2. dogs — 추천 알고리즘 입력값 (FRIEND-1의 선행조건)
-- ============================================================
ALTER TABLE dogs
    ADD COLUMN personality_traits JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN cutout_image_url TEXT,
    ADD COLUMN weight_kg NUMERIC;

ALTER TABLE dogs
    ADD CONSTRAINT chk_dogs_personality_traits CHECK (
        CASE WHEN jsonb_typeof(dogs.personality_traits) = 'array'
             THEN jsonb_array_length(dogs.personality_traits) <= 3
             ELSE false
        END
        );

-- ============================================================
-- 3. users — 소셜 로그인 확장 (QUESTIONS.md Q7)
-- ============================================================
ALTER TABLE users
    ADD COLUMN kakao_user_id     TEXT UNIQUE,
    ADD COLUMN google_user_id    TEXT UNIQUE,
    ADD COLUMN phone_number      TEXT,
    ADD COLUMN phone_verified_at TIMESTAMPTZ;