-- 견주(users)의 대략적 위치("집 근처") 추가.
--
-- 설계 방향(2026-07-19 논의):
--   - 위치는 강아지(dogs)가 아니라 견주(users)에 귀속 — 다견종 유저도 위치 1개로 충분
--   - GPS 실시간 추적이 아니라 온보딩 시 유저가 한 번 설정하는 값(예: "집 근처")
--   - 원본 좌표는 정밀하게 저장한다 — 정렬/필터링 정확도를 위해 흐림(fuzzing)은
--     저장 단계가 아니라 API 응답 직렬화 단계에서 반올림/지터로 처리할 것
--     (예: 소수점 2자리 반올림 ≈ 1km 격자, 거리도 정수 km로 반올림해서 응답)
--     ⚠ 흐린 좌표를 별도 컬럼으로 중복 저장하지 않는다 — 흐림 정책이 바뀔 때마다
--     백필해야 하고, 원본과 어긋날 위험이 있음
--   - walk_posts.location(특정 산책 약속 장소)과는 별개 — 그쪽은 의도적으로 정밀해야 함

ALTER TABLE users
    ADD COLUMN home_location GEOGRAPHY(POINT, 4326); -- nullable — 온보딩 전에는 미설정

CREATE INDEX idx_users_home_location ON users USING GIST (home_location);
