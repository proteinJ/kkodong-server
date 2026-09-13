-- 견주 지도(MAP-01/02) 확인용 더미 매장
--
-- 지금은 점주가 매장을 등록해도 지도에 뜨지 않는다. 매장이 active 가 되는 길은 사업자
-- 진위확인 통과뿐인데, 진위확인이 꺼져 있으면(NTS_VERIFICATION_ENABLED 기본 false)
-- 매장이 pending 으로 남는다. 로컬에서 지도 API 를 눌러보려면 active + 좌표가 있는
-- 매장을 직접 넣어야 한다.
--
-- 사용법 (기존 seed/*.sql 과 같은 방식):
--   docker exec -i kkodong-postgres psql -U postgres -d kkodong < seed/map_dev_seed.sql
--
-- 기준 위치: 서울 마포구 망원·합정 근처 (API_SPEC 14.2 예시 좌표)
--   GET /api/v1/places?lat=37.5605&lng=126.9237
--
-- 재실행해도 안전하다 — 사업자등록번호 9990000xxx 대역 매장과 map-seed-* 계정을 지우고 다시 넣는다.
-- 친구 강아지 수는 kg-test@kkodong.dev 계정에 강아지가 1마리 이상 있을 때만 만든다.
--
-- ┌─────┬────────────────────────┬──────────┬───────────┬──────────────────────────────────────┐
-- │ 번호│ 상호                   │ 업종     │ 거리(약)  │ 무엇을 확인하나                      │
-- ├─────┼────────────────────────┼──────────┼───────────┼──────────────────────────────────────┤
-- │ 01  │ 망원 댕댕유치원        │ 유치원   │ 300m 북   │ 기본 영업시간 · 사진 2장 · 친구 강아지 2 │
-- │ 02  │ 합정 뽀송 미용실       │ 미용     │ 800m 동   │ 점심 휴게(하루 두 줄) · 월요일 휴무  │
-- │ 03  │ 마포 24시 동물병원     │ 병원     │ 1.2km 남  │ 00:00~00:00 = 24시간                 │
-- │ 04  │ 홍대 올빼미 동물병원   │ 병원     │ 2km 북동  │ 20:00~06:00 자정 넘김                │
-- │ 05  │ 연남 오늘휴무 미용실   │ 미용     │ 1.5km 북  │ 오늘 날짜가 휴무일 → false           │
-- │ 06  │ 서교 시간미입력 유치원 │ 유치원   │ 2.5km 동  │ 영업시간 없음 → isOpenNow null       │
-- │ 07  │ 100%펫미용             │ 미용     │ 2.8km 서  │ 검색어 % 가 글자 그대로 찾아지는지   │
-- │ 08  │ 성산 멀리있는 유치원   │ 유치원   │ 6km 북    │ radiusKm=6 이상일 때만 보임          │
-- │ 09  │ 일산 반경밖 동물병원   │ 병원     │ 15km 북   │ 최대 10km 라 절대 안 보임            │
-- │ 10  │ 대기중 유치원          │ 유치원   │ 400m      │ pending → 안 보임                    │
-- │ 11  │ 정지된 미용실          │ 미용     │ 500m      │ suspended → 안 보임                  │
-- │ 12  │ 좌표없는 동물병원      │ 병원     │ -         │ 좌표 없음 → 안 보임                  │
-- └─────┴────────────────────────┴──────────┴───────────┴──────────────────────────────────────┘

BEGIN;

-- 재실행 대비 정리. 매장을 지우면 재원(enrollments)이, 계정을 지우면 강아지·친구 관계가 CASCADE 로 따라 지워진다.
DELETE FROM merchants WHERE business_registration_number LIKE '9990000%';
DELETE FROM users WHERE email LIKE 'map-seed-%@kkodong.dev';

INSERT INTO merchants (merchant_type, name, business_registration_number, representative_name,
                       status, verified_at, address, phone, description, location,
                       image_urls, business_hours, closed_dates)
VALUES
-- 01 기본 영업시간 · 사진 2장
('kindergarten', '망원 댕댕유치원', '9990000001', '김원장', 'active', now(),
 '서울 마포구 망원로 12, 1층', '02-336-1234', '소형견 전용 공간을 따로 두고 있어요.',
 ST_SetSRID(ST_MakePoint(126.9237, 37.5632), 4326)::geography,
 '["https://picsum.photos/seed/kkodong-01a/600/400", "https://picsum.photos/seed/kkodong-01b/600/400"]',
 '[{"day":"mon","open":"08:00","close":"20:00"},{"day":"tue","open":"08:00","close":"20:00"},
   {"day":"wed","open":"08:00","close":"20:00"},{"day":"thu","open":"08:00","close":"20:00"},
   {"day":"fri","open":"08:00","close":"20:00"},{"day":"sat","open":"10:00","close":"17:00"}]',
 '[]'),

-- 02 점심 휴게(하루 두 줄) · 월요일 휴무(월요일 줄 없음)
('grooming', '합정 뽀송 미용실', '9990000002', '박원장', 'active', now(),
 '서울 마포구 합정동 381-2', '02-322-5678', NULL,
 ST_SetSRID(ST_MakePoint(126.9328, 37.5605), 4326)::geography,
 '["https://picsum.photos/seed/kkodong-02/600/400"]',
 (SELECT jsonb_agg(jsonb_build_object('day', d, 'open', t.open, 'close', t.close))
    FROM unnest(ARRAY['tue', 'wed', 'thu', 'fri', 'sat', 'sun']) AS d
   CROSS JOIN (VALUES ('10:00', '13:00'), ('14:00', '19:00')) AS t(open, close)),
 '[]'),

-- 03 24시간
('clinic', '마포 24시 동물병원', '9990000003', '이원장', 'active', now(),
 '서울 마포구 월드컵로 45', '02-333-2424', '야간 응급 진료 가능',
 ST_SetSRID(ST_MakePoint(126.9237, 37.5497), 4326)::geography,
 '[]',
 (SELECT jsonb_agg(jsonb_build_object('day', d, 'open', '00:00', 'close', '00:00'))
    FROM unnest(ARRAY['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']) AS d),
 '[]'),

-- 04 자정 넘김
('clinic', '홍대 올빼미 동물병원', '9990000004', '최원장', 'active', now(),
 '서울 마포구 와우산로 99', '02-334-2000', '밤에만 여는 병원',
 ST_SetSRID(ST_MakePoint(126.9380, 37.5732), 4326)::geography,
 '["https://picsum.photos/seed/kkodong-04/600/400"]',
 (SELECT jsonb_agg(jsonb_build_object('day', d, 'open', '20:00', 'close', '06:00'))
    FROM unnest(ARRAY['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']) AS d),
 '[]'),

-- 05 오늘이 휴무일 (한국 날짜 기준)
('grooming', '연남 오늘휴무 미용실', '9990000005', '정원장', 'active', now(),
 '서울 마포구 연남로 7', NULL, NULL,
 ST_SetSRID(ST_MakePoint(126.9237, 37.5740), 4326)::geography,
 '[]',
 (SELECT jsonb_agg(jsonb_build_object('day', d, 'open', '10:00', 'close', '20:00'))
    FROM unnest(ARRAY['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']) AS d),
 jsonb_build_array(to_char((now() AT TIME ZONE 'Asia/Seoul')::date, 'YYYY-MM-DD'))),

-- 06 영업시간 미입력
('kindergarten', '서교 시간미입력 유치원', '9990000006', '한원장', 'active', now(),
 '서울 마포구 서교동 400', '02-335-0006', NULL,
 ST_SetSRID(ST_MakePoint(126.9520, 37.5600), 4326)::geography,
 '[]', '[]', '[]'),

-- 07 검색어 % 확인용
('grooming', '100%펫미용', '9990000007', '윤원장', 'active', now(),
 '서울 강서구 가양동 10', NULL, NULL,
 ST_SetSRID(ST_MakePoint(126.8920, 37.5605), 4326)::geography,
 '[]',
 (SELECT jsonb_agg(jsonb_build_object('day', d, 'open', '09:00', 'close', '21:00'))
    FROM unnest(ARRAY['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']) AS d),
 '[]'),

-- 08 6km — 반경을 넓혀야 보임
('kindergarten', '성산 멀리있는 유치원', '9990000008', '강원장', 'active', now(),
 '서울 은평구 수색로 200', NULL, NULL,
 ST_SetSRID(ST_MakePoint(126.9237, 37.6145), 4326)::geography,
 '[]', '[]', '[]'),

-- 09 15km — 최대 반경(10km) 밖
('clinic', '일산 반경밖 동물병원', '9990000009', '조원장', 'active', now(),
 '경기 고양시 덕양구 1', NULL, NULL,
 ST_SetSRID(ST_MakePoint(126.9237, 37.6955), 4326)::geography,
 '[]', '[]', '[]'),

-- 10 pending — 진위확인 전이라 견주에게 보이면 안 됨
('kindergarten', '대기중 유치원', '9990000010', '서원장', 'pending', NULL,
 '서울 마포구 망원동 1', NULL, NULL,
 ST_SetSRID(ST_MakePoint(126.9237, 37.5641), 4326)::geography,
 '[]', '[]', '[]'),

-- 11 suspended
('grooming', '정지된 미용실', '9990000011', '문원장', 'suspended', now(),
 '서울 마포구 망원동 2', NULL, NULL,
 ST_SetSRID(ST_MakePoint(126.9300, 37.5605), 4326)::geography,
 '[]', '[]', '[]'),

-- 12 좌표 없음
('clinic', '좌표없는 동물병원', '9990000012', '양원장', 'active', now(),
 '주소만 있는 병원', NULL, NULL,
 NULL,
 '[]', '[]', '[]');

-- 친구 강아지 수: kg-test 계정의 친구 1명, 그 친구의 강아지 2마리가 01 매장에 재원(1마리는 퇴원)
DO $$
DECLARE
    me_id     uuid;
    me_dog_id uuid;
    friend_id uuid := gen_random_uuid();
    dog_1     uuid := gen_random_uuid();
    dog_2     uuid := gen_random_uuid();
    dog_3     uuid := gen_random_uuid();
    store_id  uuid;
BEGIN
    SELECT id INTO me_id FROM users WHERE email = 'kg-test@kkodong.dev';
    SELECT id INTO me_dog_id FROM dogs WHERE owner_id = me_id ORDER BY created_at DESC LIMIT 1;

    IF me_id IS NULL OR me_dog_id IS NULL THEN
        RAISE NOTICE 'kg-test@kkodong.dev 계정이나 강아지가 없어 친구 데이터는 건너뛴다';
        RETURN;
    END IF;

    SELECT id INTO store_id FROM merchants WHERE business_registration_number = '9990000001';

    INSERT INTO users (id, email, password_hash, display_name)
    VALUES (friend_id, 'map-seed-friend@kkodong.dev', 'x', '두부아빠');

    INSERT INTO dogs (id, owner_id, name)
    VALUES (dog_1, friend_id, '두부'), (dog_2, friend_id, '콩이'), (dog_3, friend_id, '보리');

    INSERT INTO friendships (user_a_id, user_b_id, dog_a_id, dog_b_id)
    VALUES (me_id, friend_id, me_dog_id, dog_1);

    INSERT INTO enrollments (merchant_id, dog_id, owner_user_id, dog_name_snapshot, status)
    VALUES (store_id, dog_1, friend_id, '두부', 'active'),
           (store_id, dog_2, friend_id, '콩이', 'active'),
           (store_id, dog_3, friend_id, '보리', 'withdrawn');
END $$;

COMMIT;

-- 확인
SELECT business_registration_number AS 번호,
       name AS 상호,
       merchant_type AS 업종,
       status AS 상태,
       CASE WHEN location IS NULL THEN '없음'
            ELSE round((ST_Distance(location, ST_SetSRID(ST_MakePoint(126.9237, 37.5605), 4326)::geography))::numeric) || 'm'
       END AS 기준위치에서거리
  FROM merchants
 WHERE business_registration_number LIKE '9990000%'
 ORDER BY business_registration_number;
