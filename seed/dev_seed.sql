-- 로컬 개발용 더미 후보 데이터 (FRIEND-1 추천 검증용)
--
-- 실행:  docker exec -i kkodong-postgres psql -U postgres -d kkodong < seed/dev_seed.sql
--        ⚠️ docker exec 에 -i 가 없으면 stdin 이 전달되지 않아 조용히 아무 일도 안 일어난다.
-- 정리:  DELETE FROM users WHERE email LIKE 'seed-%@test.com';   (dogs 는 ON DELETE CASCADE)
--
-- ⚠️ 이 파일은 db/migration 이 아니다 — Flyway 가 읽지 않는다. 절대 옮기지 말 것.
-- ⚠️ 실서버에서 실행 금지. password_hash 는 로그인 불가능한 더미값이다.
--
-- 기준점: 서울시청 (37.5665, 126.9780). 본인 계정의 home_location 과 맞춰야 거리가 의미 있다.
--   내 좌표 확인:
--     SELECT ST_Y(home_location::geometry), ST_X(home_location::geometry) FROM users WHERE email='...';

BEGIN;

DELETE FROM users WHERE email LIKE 'seed-%@test.com';

INSERT INTO users (id, email, password_hash, display_name, home_location, walk_time_slots) VALUES
  ('11111111-0000-0000-0000-000000000001','seed-1@test.com','$2a$10$seedonly.not.a.real.hash.................','두부맘',
   ST_SetSRID(ST_MakePoint(126.9780, 37.569199),4326)::geography, '["evening"]'),                    -- ~300m
  ('11111111-0000-0000-0000-000000000002','seed-2@test.com','$2a$10$seedonly.not.a.real.hash.................','초코파파',
   ST_SetSRID(ST_MakePoint(126.9780, 37.573698),4326)::geography, '["evening","morning"]'),          -- ~800m
  ('11111111-0000-0000-0000-000000000003','seed-3@test.com','$2a$10$seedonly.not.a.real.hash.................','보리맘',
   ST_SetSRID(ST_MakePoint(126.9780, 37.579997),4326)::geography, '["dawn"]'),                       -- ~1.5km
  ('11111111-0000-0000-0000-000000000004','seed-4@test.com','$2a$10$seedonly.not.a.real.hash.................','콩자바맘',
   ST_SetSRID(ST_MakePoint(126.9780, 37.593493),4326)::geography, '["evening","night"]'),            -- ~3km
  ('11111111-0000-0000-0000-000000000005','seed-5@test.com','$2a$10$seedonly.not.a.real.hash.................','시루파파',
   ST_SetSRID(ST_MakePoint(126.9780, 37.606990),4326)::geography, '[]'),                             -- ~4.5km, 시간대 미입력(중립 검증용)
  -- 반경 밖. 5km 필터가 실제로 동작하는지 확인하는 음성 케이스다. 결과에 뜨면 버그.
  ('11111111-0000-0000-0000-000000000006','seed-6@test.com','$2a$10$seedonly.not.a.real.hash.................','멀리맘',
   ST_SetSRID(ST_MakePoint(126.9780, 37.746456),4326)::geography, '["morning"]'),                    -- ~20km
  -- home_location 이 NULL. 규약 8 — 후보가 될 수 없다. 결과에 뜨면 버그.
  ('11111111-0000-0000-0000-000000000007','seed-7@test.com','$2a$10$seedonly.not.a.real.hash.................','위치없음맘',
   NULL, '["evening"]');

INSERT INTO dogs (id, owner_id, name, breed, birth_date, gender, size, energy_level, neutered, personality_traits) VALUES
  ('22222222-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000001','두부','푸들',      '2022-03-10','female','small', 'high',  true, '["활발함","사교적"]'),
  ('22222222-0000-0000-0000-000000000002','11111111-0000-0000-0000-000000000002','초코','말티즈',    '2021-11-02','male',  'small', 'medium',true, '["차분함"]'),
  ('22222222-0000-0000-0000-000000000003','11111111-0000-0000-0000-000000000003','보리','비글',      '2019-05-20','male',  'medium','high',  false,'["장난꾸러기","활발함"]'),
  ('22222222-0000-0000-0000-000000000004','11111111-0000-0000-0000-000000000004','콩자바','포메라니안','2023-01-15','female','small','medium',true, '["애교많음","사교적","활발함"]'),
  -- 성향 태그 미입력. 중립(0.5) 처리가 되는지 확인하는 케이스 — 0점으로 새면 추천 하위에 깔린다.
  ('22222222-0000-0000-0000-000000000005','11111111-0000-0000-0000-000000000005','시루','믹스',      NULL,        NULL,   NULL,    NULL,    NULL, '[]'),
  ('22222222-0000-0000-0000-000000000006','11111111-0000-0000-0000-000000000006','멀리','비글',      '2020-07-07','male',  'medium','low',   true, '["독립적"]'),
  ('22222222-0000-0000-0000-000000000007','11111111-0000-0000-0000-000000000007','유령','치와와',    '2022-02-02','female','small', 'high',  true, '["겁많음"]');

COMMIT;
