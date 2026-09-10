-- 견주 유치원(KG-09) 확인용 더미 데이터
--
-- 점주 앱(PN-*)이 만들어야 할 데이터를 손으로 채운다. 견주 쪽은 읽기 전용이라
-- 이 시드 없이는 API가 항상 빈 목록을 돌려줘 아무것도 확인할 수 없다.
--
-- 사용법 (기존 seed/dev_seed.sql 과 같은 방식):
--   docker exec -i kkodong-postgres psql -U postgres -d kkodong < seed/kg_dev_seed.sql
--
-- 전제: 로그인한 유저에게 반려견이 최소 1마리 등록돼 있어야 한다.
--       가장 최근에 등록된 강아지를 대상으로 삼는다.
-- 재실행해도 안전하다 — 상호(name)를 키로 지우고 다시 넣는다.

BEGIN;

-- 재실행 대비 정리. merchants 를 지우면 아래 전부 ON DELETE CASCADE 로 따라 지워진다.
DELETE FROM merchants WHERE business_registration_number IN ('0000000001', '0000000002');

WITH target_dog AS (
    SELECT d.id AS dog_id, d.owner_id, d.name AS dog_name, d.breed
      FROM dogs d
     ORDER BY d.created_at DESC
     LIMIT 1
),
-- ── 유치원 두 곳 ────────────────────────────────────────────────
-- 두 곳을 넣는 이유: /kindergartens/my 가 목록이라 1건만 있으면
-- 정렬·다건 처리가 검증되지 않는다.
new_merchants AS (
    INSERT INTO merchants (name, business_registration_number, representative_name,
                           business_opened_on, verified_at, address, phone, status)
    VALUES ('댕댕유치원 성수점', '0000000001', '김원장', DATE '2024-03-02', now(),
            '서울 성동구 성수이로 100', '02-1234-5678', 'active'),
           ('멍멍키즈 왕십리점', '0000000002', '박원장', DATE '2023-11-11', now(),
            '서울 성동구 왕십리로 200', '02-2222-3333', 'active')
    RETURNING id, business_registration_number
),
m1 AS (SELECT id FROM new_merchants WHERE business_registration_number = '0000000001'),
m2 AS (SELECT id FROM new_merchants WHERE business_registration_number = '0000000002'),

-- ── 이용권 상품 ─────────────────────────────────────────────────
products AS (
    INSERT INTO merchant_products (merchant_id, name, product_type, total_count, valid_days, price)
    SELECT m1.id, '10회권', 'count', 10, NULL, 350000 FROM m1
    UNION ALL
    SELECT m2.id, '1개월 정기권', 'period', NULL, 30, 400000 FROM m2
    RETURNING id, merchant_id, name
),

-- ── 원생 등록 ──────────────────────────────────────────────────
enr AS (
    INSERT INTO enrollments (merchant_id, dog_id, owner_user_id,
                             dog_name_snapshot, dog_breed_snapshot, status, enrolled_on)
    SELECT m1.id, t.dog_id, t.owner_id, t.dog_name, t.breed, 'active', CURRENT_DATE - 30
      FROM m1, target_dog t
    UNION ALL
    SELECT m2.id, t.dog_id, t.owner_id, t.dog_name, t.breed, 'active', CURRENT_DATE - 7
      FROM m2, target_dog t
    RETURNING id, merchant_id
),
e1 AS (SELECT e.id FROM enr e JOIN m1 ON m1.id = e.merchant_id),
e2 AS (SELECT e.id FROM enr e JOIN m2 ON m2.id = e.merchant_id),

-- ── 이용권 발급 ─────────────────────────────────────────────────
-- 횟수권 하나(잔여 2 = 임박 강조 대상)와 기간권 하나. 계약 13.3이
-- "기간권이면 remainingCount·totalCount 가 null" 이라고 갈라 둔 두 모양을 모두 만든다.
issued AS (
    INSERT INTO passes (enrollment_id, product_id, product_name_snapshot, product_type,
                        price_snapshot, remaining_count, total_count, expires_on, status)
    SELECT e1.id, p.id, '10회권', 'count', 350000, 2, 10, CURRENT_DATE + 6, 'active'
      FROM e1, products p JOIN m1 ON m1.id = p.merchant_id
    UNION ALL
    SELECT e2.id, p.id, '1개월 정기권', 'period', 400000, NULL, NULL, CURRENT_DATE + 21, 'active'
      FROM e2, products p JOIN m2 ON m2.id = p.merchant_id
    RETURNING id
),

-- ── 오늘의 등원 ────────────────────────────────────────────────
-- 첫 번째 유치원만 오늘 등원했다. 두 번째는 행이 없어
-- "오늘은 등원하지 않는 날"(todayAttendance = null)이 되는지 확인용이다.
att AS (
    INSERT INTO attendances (merchant_id, enrollment_id, attendance_date, status, checked_in_at)
    SELECT m1.id, e1.id, CURRENT_DATE, 'attended',
           (CURRENT_DATE + TIME '09:12') AT TIME ZONE 'Asia/Seoul'
      FROM m1, e1
    RETURNING id
)

-- ── 알림장 ────────────────────────────────────────────────────
-- 4건을 넣는다(홈은 3건만 보여줘야 하므로 상한이 지켜지는지 확인).
-- draft 1건 포함 — 계약 13.1의 "DRAFT 는 절대 내보내지 않는다" 검증용이다.
INSERT INTO daily_notes (merchant_id, enrollment_id, note_date,
                         activity, meal, bathroom, condition, remark, status, sent_at, read_at)
SELECT m1.id, e1.id, CURRENT_DATE,
       '친구들이랑 공놀이 신나게 했어요. 오늘은 특히 활발했습니다', '사료 한 그릇 완식', NULL, '아주 좋음', NULL,
       'sent', now(), NULL
  FROM m1, e1
UNION ALL
SELECT m1.id, e1.id, CURRENT_DATE - 1,
       '산책 다녀왔어요', '사료 절반', '대변 1회', '좋음', NULL, 'sent', now() - INTERVAL '1 day', now() - INTERVAL '20 hours'
  FROM m1, e1
UNION ALL
SELECT m1.id, e1.id, CURRENT_DATE - 2,
       NULL, NULL, NULL, NULL, NULL, 'sent', now() - INTERVAL '2 days', NULL
  FROM m1, e1
UNION ALL
SELECT m1.id, e1.id, CURRENT_DATE - 3,
       '아직 작성 중인 초안입니다 — 견주에게 보이면 안 된다', NULL, NULL, NULL, NULL, 'draft', NULL, NULL
  FROM m1, e1;

COMMIT;

-- 확인
SELECT m.name AS 유치원, e.status AS 등록상태,
       (SELECT count(*) FROM daily_notes n WHERE n.enrollment_id = e.id AND n.status = 'sent') AS 발송알림장,
       (SELECT count(*) FROM passes p WHERE p.enrollment_id = e.id AND p.status = 'active') AS 사용가능이용권
  FROM enrollments e
  JOIN merchants m ON m.id = e.merchant_id
 WHERE m.business_registration_number IN ('0000000001', '0000000002')
 ORDER BY m.name;
