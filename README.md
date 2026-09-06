# kkodong-server

**꼬동(KKODONG)** 백엔드. [spring-boot-boilerplate](https://github.com/proteinJ/spring-boot-boilerplate)에서
생성, 인증/에러처리 인프라는 그대로 두고 전용 설정(패키지명, PostGIS, Flyway)을 추가했습니다.

> **서비스명·코드네임·패키지 모두 "꼬동(KKODONG)"으로 통일** (2026-08-19 결정).
> 2026-08-15에는 "코드네임/패키지는 PawWalk 유지"였으나, 리포지토리를 열었을 때
> 어떤 프로젝트인지 이름만으로 식별되지 않는 문제가 있어 뒤집었다. 클라이언트가
> 아직 없어 리네임 비용이 가장 싼 시점이라는 판단.
>
> ⚠️ 단 `src/main/resources/db/migration/*.sql`은 예외로 "PawWalk" 표기가 남아 있다 —
> 이미 적용된 마이그레이션은 주석 한 글자만 바꿔도 Flyway 체크섬 검증이 깨진다.

기획/API 설계 문서는 `PawWalk-ios` 레포 참조:
- **`docs/KKODONG_CONCEPT.md`** ← 현행 기준 기획서(v3). `PAWWALK_CONCEPT*.md`는 superseded
- `docs/TICKETS_MVP.md`, `docs/api/API_SPEC.md`, `docs/api/openapi.yaml`, `docs/DB_SCHEMA.sql`

## 스택

- Java 17, Spring Boot 3.5, Gradle
- Spring Security + JWT (Access/Refresh Token, Redis 저장)
- Spring Data JPA + PostgreSQL (Supabase 호스팅) + **PostGIS** (위치 기반 매칭)
- Flyway (스키마 마이그레이션, `src/main/resources/db/migration/`)
- Redis (Refresh Token 저장, 로그아웃 블랙리스트)
- Swagger UI (SpringDoc OpenAPI)

## 시작하기

1. 환경변수 설정

```
DB_URL=jdbc:postgresql://<Supabase 프로젝트 호스트>:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=<Supabase DB 비밀번호>
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=<충분히 긴 랜덤 문자열>
```

2. Supabase 프로젝트 대시보드 → Database → Extensions에서 **postgis 활성화**
   (Flyway 마이그레이션이 `CREATE EXTENSION IF NOT EXISTS postgis`를 실행하긴 하지만,
   Supabase 관리형 환경에서는 대시보드에서 먼저 켜두는 걸 권장)
3. 로컬 Redis 실행 (`brew services start redis` 또는 Docker)
4. 실행 — `./gradlew bootRun` (기동 시 Flyway가 `V1`~`V5`를 순서대로 자동 적용)

### 로컬 개발용 더미 데이터

추천(FRIEND-1)은 후보가 될 다른 유저가 있어야 결과를 확인할 수 있다. `local` 프로파일의
Postgres 컨테이너에 더미 견주·강아지 7건을 넣는 스크립트:

```bash
docker exec -i kkodong-postgres psql -U postgres -d kkodong < seed/dev_seed.sql
```

- ⚠️ `docker exec`에 **`-i`가 없으면 stdin이 전달되지 않아 조용히 아무 일도 안 일어난다**
- 재실행 안전(`seed-%@test.com` 계정을 지우고 다시 넣는다). 정리는
  `DELETE FROM users WHERE email LIKE 'seed-%@test.com';`
- 반경 밖(20km)·`home_location` NULL·성향 태그 미입력 케이스가 포함돼 있다 —
  각각 반경 필터·규약 8·중립(0.5) 처리를 검증하는 음성 케이스다
- 좌표 기준점은 서울시청이므로 **본인 테스트 계정의 `home_location`과 맞춰야 한다**
- 이 파일은 `db/migration`이 아니라 `seed/`에 있다(Flyway가 읽지 않는다). 옮기지 말 것

로컬 DB는 서버를 재시작해도 초기화되지 않는다 — `ddl-auto: validate`라 Hibernate가
스키마를 건드리지 않고, Flyway는 새 마이그레이션만 적용하며, 데이터는 도커 볼륨
(`kkodong-pg-data`)에 남는다. 날아가는 것은 `docker compose down -v`를 했을 때뿐이다.

## API

Swagger UI: `/swagger-ui/index.html` (전체 API 문서 + JWT Authorize 테스트)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 (AT/RT 발급) |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| POST | `/api/v1/auth/apple` | Sign in with Apple |
| GET | `/api/v1/users/me` | 내 정보 조회 |
| PATCH | `/api/v1/users/me` | 프로필 수정 |
| PATCH | `/api/v1/users/me/password` | 비밀번호 변경 |
| DELETE | `/api/v1/users/me` | 회원 탈퇴 (Apple 심사 가이드라인 5.1.1(v) 대응) |
| POST/GET/PATCH/DELETE | `/api/v1/dogs`, `/api/v1/dogs/{id}` | 반려견 CRUD |
| POST | `/api/v1/dogs/{id}/photo` | 프로필 사진 업로드 (Cloudflare R2) |
| PATCH | `/api/v1/users/me/onboarding` | 온보딩 완료 처리 (선행조건 검증 있음) |
| POST | `/api/v1/dogs/{id}/cutout` | 누끼 이미지 업로드 (Cloudflare R2) |
| POST/DELETE/GET | `/api/v1/blocks`, `/api/v1/blocks/{blockedId}` | 유저 차단·해제·목록 (SAFETY-1) |
| POST | `/api/v1/reports` | 신고 접수 (SAFETY-2) |
| GET | `/api/v1/friends/recommendations` | 주변 강아지 추천 (FRIEND-1) — **골격만. 아래 참조** |

✅ **경로 리네임 완료(2026-08-19)**: `/api/v1/members/*` → `/api/v1/users/*`,
`Member*` 클래스·식별자·JWT 클레임(`memberId` → `userId`)까지 전부 `User`로 통일.

⚠️ **JSON 필드는 camelCase를 쓴다(2026-08-19 결정)**. Jackson 기본값이라 별도 설정이
없고, Swift `JSONDecoder`가 어느 쪽이든 흡수하므로 클라이언트 편의는 근거가 되지 않는다.
✅ `docs/api/API_SPEC.md`·`openapi.yaml` 반영 완료(2026-08-27) — 단 DB 컬럼/테이블명은
snake_case 유지다.

⚠️ **성공 응답에 204를 쓰지 않는다.** 모든 응답이 `ApiResponse` envelope로 감싸이므로
삭제·로그아웃도 `200 + data: null`이다.

나머지 도메인은 `PawWalk-ios/docs/api/API_SPEC.md` 명세대로 구현 예정 — 아직 미구현.

## 스키마 관련 참고

- `users` 테이블 PK는 `UUID`(boilerplate 원본은 `Long`이었음 — DB_SCHEMA.sql과
  맞추기 위해 전환)
- `role` 컬럼은 boilerplate의 인증 패턴 유지를 위해 추가(DB_SCHEMA.sql 원본에는 없었음)
- `display_name`은 nullable로 조정 — 회원가입 시점엔 비워두고 온보딩에서 채우는 설계

## 아직 안 된 것 (2026-08-15, 기획 v3 기준)

**완료 (2026-08-19)**
- `V3__v3_baseline.sql` — v2 죽은 테이블 9개 DROP + `dogs`/`users` 컬럼 확장
- `dogs` 확장: `personality_traits`(JSONB, 최대 3개 CHECK), `cutout_image_url`, `weight_kg`
  + `PersonalityTrait` enum 값 검증, `POST /dogs/{id}/cutout` 엔드포인트
- `/members` → `/users` 리네임, `Member` → `User` 전면 통일

**완료 (2026-08-27)**
- 온보딩 완료 처리 API (`PATCH /users/me/onboarding`)
- **SAFETY-1/2** — 차단 CRUD + 신고 접수, `V4__add_reports.sql`
- `V5__add_friend_domain.sql` — `friend_requests` + `friendships`
  + `users.walk_time_slots`(최대 3개 CHECK)
- `users.home_location` 엔티티 매핑 + `PATCH /users/me`의 `homeLocation`/`walkTimeSlots`
- 추천 알고리즘 설정값 (`kkodong.recommendation.*`, `kkodong.dog.*`, `kkodong.user.*`)
- `SchemaDriftTest` — DB에만 있고 엔티티가 모르는 컬럼 검출(허용목록 방식)

**진행 중 — FRIEND-1 (추천 피드)**

후보 조회까지 동작한다. `GET /friends/recommendations`가 반경 5km 안의 강아지를
가까운 순으로 내려준다. 남은 것은 계산 로직이고, 응답의 `dog`/`owner`/`reason`은
아직 전부 `null`이다.

- ✅ 배선 — 선행 검증(`home_location`·강아지 소유권), 제외 집합(차단 양방향 +
  이미 친구인 견주), PostGIS 후보 조회, 응답 조립
- ⬜ `RecommendationScorer` — 6개 신호 점수·합산·동점 셔플. **핵심이자 미착수**
- ⬜ `RecommendationReasonBuilder` — 이유 문장 조립
- ⬜ `RecommendationCursor` — 반경/오프셋 인코딩. 지금은 반경이 상한(5km)에 고정돼
  있고 `1→2→3→5km` 자동 확장이 없다
- ⬜ 견종 그룹 테이블(`breed_score`의 입력) — 설정에 아직 없다
- ⬜ `FriendRequest` 엔티티 — 없어서 `requestStatus`가 `"none"` 고정이고
  pending 신청 상대를 제외하지 못한다
- ⬜ `DogResponse.publicInfo`에 `id`·`ageMonths` 추가 — 지금은 클라이언트가
  친구 신청을 보낼 대상 id를 받을 수 없다

⚠️ **후보 풀 상한(`candidate-cap`)은 요청 `limit`과 별개다.** 같은 값으로 두면
가까운 순 `limit`개만 뽑아 재정렬하는 꼴이라 **거리가 하드 필터로 작동**해
나머지 5개 신호가 무력해진다(API_SPEC 2.1 규약 1 위반). `PropertiesBindingTest`가
이 관계를 검증한다.

**먼저 해야 할 것** — 전부 명세와 어긋나는 항목이다.
상세: `PawWalk-ios/docs/api/API_SPEC.md` "미해결: 구현이 명세와 어긋나는 항목"
- 탈퇴 시 Redis RT가 자연 TTL(7일)까지 잔존 — `RefreshToken`에 userId 보조 인덱스 필요
- `DogService`의 enum 파싱이 잘못된 값에 500을 낸다 — `ReportService.parseXxx` 방식으로
- 없는 경로가 404가 아니라 500으로 나감 — `NoResourceFoundException` 핸들러 추가
- 패스권 마릿수 제한 검증 (`users.subscription_tier` 기준, QUESTIONS.md Q13 대기)

**미구현 도메인** (착수 권장 순: friend → chat → walk → card → meet → community)
- `friend`(추천/신청/친구), `chat`+`walk-appointment`, `walk`(세션/배변/저장경로),
  `walk-card`, `meet`(스침 상호 확인), `community`(글/댓글/좋아요), `care-record`,
  `service-area`, `subscription`

✅ **`users.walk_time_slots`는 `V5`에 동봉 완료** — 추천 가중치가 2026-08-27에 개정되어
**산책 시간대 15점**이 신설됐고, 컬럼·설정값·입력 API가 모두 준비돼 있다.
`UserResponse`로 되받는 것까지 되므로 온보딩 UI가 선택 상태를 복원할 수 있다.

⚠️ **산책 시간대는 추천의 선행조건이 아니다.** 미입력은 중립(0.5)으로 처리되며
불이익이 없다(`FRIEND_RECOMMENDATION_SPEC.md` 2절 / API_SPEC 2.1 규약 6) — "건너뛰기
허용"이 확정 설계이므로 이 값을 필수로 요구하는 검증을 어디에도 넣지 말 것.
거리 계산의 기준점인 `home_location`만이 추천의 실제 선행조건이다(규약 8).

⚠️ **FRIEND/CHAT/COMMUNITY 착수 시 차단(SAFETY-1) 검증을 직접 넣을 것.** 차단 테이블만
있고 "차단이 다른 기능에 미치는 영향"은 아무 데도 구현돼 있지 않다 — RLS가 없으므로
추천 제외(양방향)·신청 403·채팅 SUBSCRIBE/SEND 거부·커뮤니티 목록 제외를 각 Service에
직접 작성해야 한다. SAFETY를 먼저 만든 이유가 이것이다.

⚠️ **WALK 착수 시 필수** — 경로에 **타일 + 타임스탬프** 기록. 없으면 과거 데이터에
소급이 불가능해 MEET-2(스침 상호 확인)를 영영 못 만든다.

**만남 진입로 재설계 (2026-08-27)**: 온천천 현장 확인 결과 "개는 다니지만 모이지
않는다"가 관측되어, 핫플레이스(지도 ○)를 Phase 2 이후로 연기하고
**운영자 산책 모임(MEET-1) → 시간대 신호 → MEET-2** 순서로 전환했다.
근거: `PawWalk-ios/docs/MARKET_ANALYSIS.md` 4절 ②.

**폐기된 도메인** (V1 스키마에는 있으나 v3에서 개념 자체가 사라짐 — 구현하지 말 것)
- `walk_posts`, `walk_post_requests`(→ `friend_requests`), `connections`(→ `friendships`),
  `vaccination_records`(→ `care_records`)

**확정된 것 (2026-08-15)**
- **친구 추천 알고리즘** — `PawWalk-ios/docs/FRIEND_RECOMMENDATION_SPEC.md`.
  점수 `30×거리 + 15×산책시간대 + 20×성격 + 15×나이 + 12×체급 + 8×견종`
  (**2026-08-27 개정** — 시간대 15점 신설, 합계 100 유지를 위해 나머지 재배분),
  성향 태그 8종 중 최대 3개.
  ⚠️ 가중치·반경·태그 세트는 **`application.yml`의 `kkodong.recommendation.*`** 설정값으로
  넣고 코드에 하드코딩하지 말 것 — 실사용 데이터로 조정할 값들이다

**미정**
- 날씨 API (CARD-1 의존성)
- 반경 자동 확장 상한 (QUESTIONS.md Q8 — 잠정 5km로 추천 공식에 반영됨)
