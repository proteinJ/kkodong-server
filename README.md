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
4. 실행 — `./gradlew bootRun` (기동 시 Flyway가 `V1__init.sql`을 자동 적용)

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
| POST | `/api/v1/dogs/{id}/cutout` | 누끼 이미지 업로드 (Cloudflare R2) |

✅ **경로 리네임 완료(2026-08-19)**: `/api/v1/members/*` → `/api/v1/users/*`,
`Member*` 클래스·식별자·JWT 클레임(`memberId` → `userId`)까지 전부 `User`로 통일.

⚠️ **JSON 필드는 camelCase를 쓴다(2026-08-19 결정)**. Jackson 기본값이라 별도 설정이
없고, Swift `JSONDecoder`가 어느 쪽이든 흡수하므로 클라이언트 편의는 근거가 되지 않는다.
**`docs/api/API_SPEC.md`·`openapi.yaml`이 snake_case로 남아 있어 갱신 필요** — 단
DB 컬럼/테이블명은 snake_case 유지이므로 일괄 치환하면 안 된다.

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

**먼저 해야 할 것**
- 온보딩 완료 처리 API (`PATCH /users/me/onboarding`) — FOUNDATION-1의 마지막 미구현
- 패스권 마릿수 제한 검증 (`users.subscription_tier` 기준, QUESTIONS.md Q13 대기)
- 없는 경로가 404가 아니라 500으로 나감 — `NoResourceFoundException` 핸들러 추가

**미구현 도메인** (착수 권장 순: safety → friend → chat → walk → card → community)
- `friend`(추천/신청/친구), `chat`+`walk-appointment`, `walk`(세션/배변/저장경로),
  `walk-card`, `community`(글/댓글/좋아요), `care-record`, `service-area`,
  `subscription`, `block`/`report`

**폐기된 도메인** (V1 스키마에는 있으나 v3에서 개념 자체가 사라짐 — 구현하지 말 것)
- `walk_posts`, `walk_post_requests`(→ `friend_requests`), `connections`(→ `friendships`),
  `vaccination_records`(→ `care_records`)

**확정된 것 (2026-08-15)**
- **친구 추천 알고리즘** — `PawWalk-ios/docs/FRIEND_RECOMMENDATION_SPEC.md`.
  점수 `35×거리 + 25×성격 + 15×나이 + 15×체급 + 10×견종`, 성향 태그 8종 중 최대 3개.
  ⚠️ 가중치·반경·태그 세트는 **`application.yml`의 `kkodong.recommendation.*`** 설정값으로
  넣고 코드에 하드코딩하지 말 것 — 실사용 데이터로 조정할 값들이다

**미정**
- 날씨 API (CARD-1 의존성)
- 반경 자동 확장 상한 (QUESTIONS.md Q8 — 잠정 5km로 추천 공식에 반영됨)
