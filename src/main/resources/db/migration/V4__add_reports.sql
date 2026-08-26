-- V4: 신고(reports) 테이블 — SAFETY-2
--
-- 왜 새로 만드는가:
--   V3에서 reports 를 DROP 했다. v2 스키마는 신고 대상이 유저뿐이었는데(reported_user_id),
--   v3에서 커뮤니티 게시글·댓글까지 확장되면서 컬럼 구조가 바뀌었기 때문이다.
--   "착수 시점에 최종형으로 새로 만든다"가 V3의 판단이었고, 지금이 그 시점이다.
--   차단(blocks)은 V1 정의가 목표 스키마와 동일해 손대지 않는다.
--
-- 최종형 출처: PawWalk-ios/docs/DB_SCHEMA.sql

CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- 신고 대상은 다형 참조(polymorphic reference)다. target_type 에 따라 target_id 가
    -- 가리키는 테이블이 달라진다:
    --   'user'              -> users.id
    --   'community_post'    -> community_posts.id      (COMMUNITY-1 에서 생성 예정)
    --   'community_comment' -> community_comments.id   (동상)
    --
    -- ⚠️ 그래서 target_id 에는 FK 를 걸 수 없다 — 참조 대상 테이블이 행마다 다르기 때문.
    --    대상이 실제로 존재하는지는 Service 계층에서 검증해야 한다.
    --    (현재는 'user' 만 검증 가능. 나머지는 COMMUNITY-1 착수 시 채운다.
    --     CHECK 에는 세 값을 미리 넣어둔다 — 나중에 넓히려면 마이그레이션이 필요하므로)
    target_type TEXT NOT NULL CHECK ( target_type IN ('user', 'community_post', 'community_comment')),
    target_id UUID NOT NULL,

    -- 값 목록은 서버의 enum 과 이중으로 관리된다. DB CHECK 는 최후 방어선이고,
    -- 400 응답은 서버가 낸다(성향 태그와 같은 분담).
    reason TEXT NOT NULL CHECK (
        reason IN ('inappropriate_behavior', 'safety_concern', 'harassment', 'fake_profile', 'spam', 'other')
        ),
    details TEXT,

    -- Phase 1 은 관리자 UI 가 없다. 개발자가 Supabase 대시보드에서 직접 status 를 갱신한다.
    status TEXT NOT NULL DEFAULT 'pending' CHECK ( status IN ('pending', 'reviewed', 'action_taken', 'dismissed') ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- [조회 성능] "처리 대기 중인 신고를 최신순으로" — Phase 1 의 유일한 신고 처리 동선.
--   컬럼 순서 근거: 등호로 거르는 status 가 앞, 정렬에 쓰는 created_at 이 뒤.
--   이 순서라야 status 로 좁힌 구간이 이미 시간순이라 별도 정렬(Sort)이 생략된다.
--   created_at 을 DESC 로 만들 필요는 없다 — Postgres 가 인덱스를 역방향으로 읽는다.
CREATE INDEX idx_reports_status ON reports (status, created_at);

-- [비즈니스 규칙] 한 사람이(reporter_id) 하나의 대상을(target_type + target_id)
--   처리 대기 중에 두 번 신고할 수 없다. 처리 완료 후 재신고는 허용한다.
--
--   유일성은 세 컬럼의 '조합'에 걸린다 — 같은 신고자가 다른 대상을, 다른 신고자가 같은
--   대상을 신고하는 것은 모두 허용된다.
--
--   WHERE 절이 있어 UNIQUE 제약(ALTER TABLE ... ADD CONSTRAINT)으로는 표현할 수 없고,
--   부분 유니크 인덱스로만 만들 수 있다. 부수 효과로 인덱스가 훨씬 작아진다
--   (신고 대부분은 처리 완료 상태이므로 색인 대상에서 빠진다).
CREATE UNIQUE INDEX uq_reports_pending
    ON reports (reporter_id, target_type, target_id)
    WHERE status = 'pending';
