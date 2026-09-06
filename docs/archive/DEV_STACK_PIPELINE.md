# 꼬동 — 개발 스택 & 파이프라인

2026-07-08 작성, 같은 날 백엔드 아키텍처 최종 변경 반영. 사용자가 대학교
학부생이며 목적이 사업이 아닌 **학습(취업 대비 포함)**임을 명확히 함에 따라
Supabase 전체 채택(v1) 결정을 폐기하고 **Java Spring Boot 자체 서버 + Supabase는
Postgres 호스팅만**으로 변경.

**2026-08-15 (기획 v3, ../product/CONCEPT.md)**: 서비스명이 "꼬동"으로 확정됐다.
**코드베이스·리포지토리·패키지(`com.pawwalk.server`)·Xcode 프로젝트명은 PawWalk 그대로
유지**하고 브랜드명만 바뀐다 — 리네임 비용 대비 이득이 없다는 판단.
**아키텍처 결정은 v3에서도 전부 유지된다**(Spring Boot + Supabase Postgres + Redis +
PostGIS + Flyway). v3가 스택에 추가한 요구사항은 아래 1.1절에 정리.

---

## 1.1 v3가 스택에 추가한 것

| 항목 | 내용 | 상태 |
|---|---|---|
| **누끼(배경 제거)** | iOS Vision `VNGenerateForegroundInstanceMaskRequest` (온디바이스) | **Phase 1로 승격**(구 Phase 2 저우선). **서버 비용 0** — 서버는 결과 PNG 저장만 |
| **친구 추천 랭킹** | PostGIS `ST_DWithin`(반경) + Service 계층 가중치 정렬. **별도 추천 엔진/ML 없음** | Phase 1. 가중치는 `application.yml` 설정값으로 분리 — 재배포 없이 조정 가능해야 함 |
| **커뮤니티 이미지** | Cloudflare R2 (반려견 사진과 동일 저장소 재사용) | 저장소는 이미 확정 |
| **경로 데이터** | `walk_sessions.route` JSONB. 포인트당 `{lat,lng,ts}` | Phase 1. 세션당 수백~수천 포인트이므로 **목록 API에서는 route를 빼거나 다운샘플링**할 것 |
| **배변 기록 오프라인 큐** | 클라이언트 로컬 큐 + `client_log_id` 멱등키 | Phase 1. 산책 중 네트워크 불안정이 기본 전제 |
| **동네 경로 집계** | `popular_routes` 배치 집계 (Spring `@Scheduled`) | **Phase 2** — 착수 금지 |

### v3에서 제외된 것
- **실시간 위치 공유(STOMP `/topic/location.*`)** — v3 Phase 1 범위 밖. STOMP 인프라
  자체는 채팅(CHAT-1)에 여전히 필요하므로 유지한다
- **모집글(`walk_posts`) 도메인** — 개념 자체 폐기

---

## 1. 기술 스택

| 레이어 | 선택 | 비고 |
|---|---|---|
| 클라이언트 | Swift 6, SwiftUI, MVVM + `@Observable` | v1 기획 확정, 변경 없음 |
| 로컬 저장소 | SwiftData | 오프라인 캐시(반려견 프로필, 최근 산책기록 등) |
| 지도/위치 | MapKit, CoreLocation | Apple 기본 프레임워크, 비용 없음 |
| **백엔드 서버** | **Java, Spring Boot** (Spring Web, Spring Data JPA, Spring Security, Spring WebSocket) | 2026-07-08 결정 변경 — 학습 목적 우선, Controller/Service/Repository 직접 구현 |
| **DB** | **Supabase Postgres — DB 호스팅 용도로만** | PostgREST/RLS/Realtime/Supabase Auth는 사용 안 함. Spring Boot가 JDBC로 직접 접속 |
| 스키마 마이그레이션 | **Flyway** | ../design/DB_SCHEMA.sql을 베이스라인 마이그레이션(`V1__init.sql`)으로 사용 |
| 인증 | **Spring Security + 자체 JWT(AT/RT)**, 기존 템플릿(github.com/proteinJ/runApp) 재사용 | Access Token(단기, stateless 서명 검증) + Refresh Token(장기). **RT는 Postgres가 아닌 Redis에 저장**, 로그아웃 시 AT는 Redis 블랙리스트 등록(2026-07-09 정정 — 템플릿 패턴 확인 후 Postgres `refresh_tokens` 테이블 계획 폐기). 이메일/비밀번호 + Sign in with Apple(신원 토큰을 서버가 Apple 공개키로 검증 후 자체 JWT 발급) |
| **캐시/세션** | **Redis** (신규, 2026-07-09 추가) | RT 저장 + 로그아웃 AT 블랙리스트 — 기존 템플릿 구조 그대로 재사용 |
| 실시간 통신 | **Spring WebSocket (STOMP)** | **채팅(CHAT-1) 전용**(2026-08-15 — 실시간 위치공유는 v3 Phase 1 범위 밖) |
| 배경 제거(누끼) | Vision `VNGenerateForegroundInstanceMaskRequest` | **Phase 1**(v3에서 승격). 온디바이스, 서버 비용 0 |
| 이미지 저장 | **Cloudflare R2** (S3 호환) | PET-1 구현 시점에 확정. 반려견 사진·누끼·산책 카드·커뮤니티 이미지 전부 여기 |
| 결제/구독(패스권) | **StoreKit 2** (클라이언트) + Spring Boot Controller가 Apple App Store Server API로 영수증 검증 | 클라이언트 신고값 신뢰 안 함(기존 원칙 유지) |
| 푸시 알림 | APNs + `UNUserNotificationCenter` | 케어 D-day는 로컬 알림. 친구 신청/수락·채팅·산책 약속·커뮤니티 댓글은 **Spring Boot가 직접 APNs 호출**(예: `pushy` 라이브러리) |
| 날씨 | 미정 (CARD-1 의존성) | 클라이언트가 직접 호출할지, Spring Boot가 프록시할지도 함께 결정 필요 |
| 분석/이벤트 로그 | Spring Boot DB 쿼리 기반으로 시작 | 데이터 볼륨 커지면 별도 도구 검토 |
| 크래시 리포트(클라이언트) | Xcode Organizer (기본 제공) | 변경 없음 |
| **백엔드 배포** | **Docker + 지인 컴퓨터(24시간 상시 구동)** | 컨테이너화해서 이식성/재현성 확보 |
| **CI/CD** | **GitHub Actions — 자체 호스팅 러너(Self-hosted runner)를 지인 컴퓨터에 설치** | push → main 시 자동 빌드/테스트/배포. 상세 4절 |
| 리버스 프록시/HTTPS | **Caddy 권장** (Let's Encrypt 자동 발급/갱신 내장) | 도메인 확보 후 설정 — ../TICKETS.md Q6, 지인과 협의 필요 |
| iOS CI/CD | Xcode Cloud (기존 결정 유지, 변경 없음) | 월 25시간 무료 |
| 버전관리 | Git + GitHub | 변경 없음 |

### 아키텍처 개요

```
┌─────────────────────┐
│   PawWalk iOS App    │
│  Swift 6 / SwiftUI   │
└──────────┬───────────┘
           │ HTTPS (REST) + WSS (WebSocket/STOMP)
           ▼
┌───────────────────────────────────────────┐
│   Spring Boot 서버 (지인 컴퓨터, 24시간)     │
│  ┌─────────────┐  ┌────────────────────┐  │
│  │ Controller   │  │ Spring Security     │  │
│  │ (REST API)   │  │ (JWT AT/RT 발급)    │  │
│  └──────┬──────┘  └────────────────────┘  │
│         │                                  │
│  ┌──────▼──────┐  ┌────────────────────┐  │
│  │ Service 계층  │  │ Spring WebSocket    │  │
│  │ (비즈니스로직)│  │ (STOMP, 채팅/위치)   │  │
│  └──────┬──────┘  └────────────────────┘  │
│         │                                  │
│  ┌──────▼──────┐  ┌────────────────────┐  │
│  │ JPA Repository│ │ APNs 클라이언트      │  │
│  └──────┬──────┘  │ (pushy 등)           │  │
│         │          └────────────────────┘  │
└─────────┼───────────────────────────────────┘
          │ JDBC
          ▼
┌─────────────────────┐     ┌─────────────────┐
│  Supabase Postgres    │     │  Redis (신규)     │  ← RT 저장 + AT 블랙리스트
│  (../design/DB_SCHEMA.sql +     │     │  지인 컴퓨터에      │     (docker-compose로 Spring
│   Flyway 마이그레이션) │     │  Docker로 구동      │      Boot와 함께 구동)
└─────────────────────┘     └─────────────────┘

[리버스 프록시: Caddy, 지인 컴퓨터에서 Spring Boot 앞단]
[Docker/docker-compose: Spring Boot + Redis 컨테이너화]
[GitHub Actions 자체 호스팅 러너: 지인 컴퓨터에 설치, push 시 빌드+배포]
```

### 기존 템플릿 재사용 (2026-07-09)

사용자의 기존 Spring Boot 템플릿(github.com/proteinJ/runApp — 러닝 앱, PawWalk과
도메인 유사)을 그대로 재사용할 예정. 확인된 구조:
- 패키지: `domain/{도메인별}` + `global/{config, security, error, common, init, deploy}`
- 인증: `@LoginMember` 커스텀 애노테이션으로 컨트롤러에 인증된 유저 주입
- 예외 처리: `global/error/`의 `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`
  — PawWalk도 이 패턴을 그대로 따름 (에러 응답 포맷은 ../design/API_SPEC.md 갱신 필요 시 반영)
- 이미 WebSocket+STOMP, JWT(jjwt), Redis, PostgreSQL 의존성 세팅되어 있어 별도
  구성 최소화 가능

---

## 2. RLS를 안 쓰면 권한 검증은 어떻게 하나 (2026-07-08 보완)

Supabase 전체 채택 시절엔 "누가 어떤 row를 볼 수 있는가"를 데이터베이스 RLS 정책으로
막았는데, Spring Boot가 중간에 있는 지금은 **그 역할을 Service 계층 코드가 대신
합니다.** 예:

```java
// 예시: 본인 반려견만 수정 가능하게 만드는 권한 체크
public Dog updateDog(UUID dogId, UUID requestUserId, DogUpdateRequest req) {
    Dog dog = dogRepository.findById(dogId).orElseThrow();
    if (!dog.getOwnerId().equals(requestUserId)) {
        throw new AccessDeniedException("본인 반려견만 수정할 수 있습니다");
    }
    // ...업데이트 로직
}
```

`requestUserId`는 Spring Security가 JWT Access Token을 검증하면서 꺼내주는 값이라,
Controller/Service에서 항상 "지금 요청한 사람이 누구인지"를 신뢰하고 쓸 수 있습니다.
DB 연결 자체는 Spring Boot가 하나의 신뢰된 계정으로 Postgres에 붙기 때문에(Supabase의
익명 키로 클라이언트가 직접 붙던 구조와 다름), DB 레벨 권한 분리가 필요 없어집니다.

---

## 3. 아직 미정 (별도 결정 필요, 2026-08-15 갱신)

1. ~~**이미지 저장소**~~ — **Cloudflare R2로 확정**(PET-1 구현 시점)
2. **도메인/HTTPS** — ../TICKETS.md Q6, 지인과 직접 협의
3. **날씨 API** — CARD-1 의존성, 조사 필요. 클라이언트 직접 호출 vs Spring Boot 프록시도 함께 결정
4. ~~**Sign in with Apple 신원 토큰 검증 라이브러리**~~ — 기존 템플릿이 jjwt 사용 중이라
   같은 생태계로 해결. `/auth/apple` 컨트롤러는 구현됨
5. **DB 연결 방식** — Supabase Postgres에 Spring Boot가 직접 JDBC로 붙을 때 커넥션
   풀링(HikariCP 기본 + Supabase 측 Supavisor/PgBouncer 사용 여부) 확인 필요
6. ~~**친구 추천 가중치 초기값 + 성향 태그 세트**~~ — **확정(2026-08-15, Q10 해결)**.
   명세: `../design/RECOMMENDATION.md`. 가중치·반경·태그 세트는 전부
   `application.yml`의 `kkodong.recommendation.*` 아래 설정값으로 넣는다 —
   **코드에 하드코딩 금지**(검증 수단이 실사용 데이터뿐이라 재배포가 필요하면 튜닝을 안 하게 된다)
7. **반경 자동 확장 상한** (v3 신규) — ../TICKETS.md Q8. 잠정 5km가 추천 공식의
   정규화 기준으로 이미 들어가 있어, 바뀌면 `max-radius-km`·`radius-steps-km`를 함께 갱신
8. **경로 데이터 응답 크기** (v3 신규) — 세션당 수백~수천 포인트의 `route` JSONB를
   목록 API에서 그대로 내려주면 응답이 비대해진다. 목록에서는 제외하거나 다운샘플링할 것

## 3.1 스키마 마이그레이션 현황 (2026-08-27)

위치: `kkodong-server/src/main/resources/db/migration/`

| 버전 | 내용 | 상태 |
|---|---|---|
| `V1__init.sql` | v2 기준 초기 스키마 | 적용됨 |
| `V2__add_user_home_location.sql` | `users.home_location` GEOGRAPHY + GiST 인덱스 | 적용됨 |
| `V3__v3_baseline.sql` | v2 죽은 테이블 9개 DROP + `dogs`(성향 태그·누끼·체중) / `users`(소셜 컬럼) 확장 | 적용됨 |
| `V4__add_reports.sql` | `reports` 재생성(다형 참조) + `uq_reports_pending` | 적용됨 |
| `V5__add_friend_domain.sql` | `users.walk_time_slots` + `friend_requests` + `friendships` | 적용됨 |
| `V6__…` | 다음 도메인 착수 시 | ⬜ |

⚠️ **적용된 마이그레이션은 수정하지 말 것.** Flyway가 체크섬을 검증하므로 주석 한
글자만 바꿔도 서버 기동이 깨진다 — 그래서 `db/migration/*.sql`에는 리네임 전의
"PawWalk" 표기가 그대로 남아 있다. 변경은 항상 새 버전 파일로 만든다.

⚠️ **V3는 DROP만 하고 CREATE는 하지 않았다.** "지운 테이블의 최종형은 해당 도메인
착수 시점에 새로 만든다"가 V3의 판단이었기 때문에(그때 모양을 확정해봐야 착수
시점에 또 바뀐다), 목표 스키마(`../design/DB_SCHEMA.sql`)에는 있지만 DB에는 아직 없는
테이블이 많다. 착수 티켓이 자기 테이블을 만드는 구조다.

**현재 DB에 실제로 있는 테이블**: `users`, `dogs`, `service_areas`,
`waitlist_signups`, `device_tokens`, `blocks`, `reports`, `friend_requests`,
`friendships`.

**스키마 드리프트 방어**: `SchemaDriftTest`가 "DB에만 있고 엔티티가 모르는 컬럼"을
잡는다. Hibernate의 `ddl-auto: validate`는 엔티티 → 테이블 단방향만 보기 때문에
반대 방향은 조용히 통과하는데, 실제로 `users.home_location`이 한 달 넘게 미매핑으로
방치된 적이 있다(친구 추천의 최대 가중치 신호였다). 미매핑 컬럼은 매핑하거나
**사유와 함께 허용목록에 적어야** 테스트가 통과한다 — "까먹었다"를 "결정했다"로
바꾸는 장치다. ⚠️ 로컬 Postgres(5433)가 떠 있어야 실행된다
(`docker compose up -d postgres`). CI에서 돌리려면 Testcontainers가 필요하다.

---

## 4. 개발 파이프라인

### 브랜치 전략 (기존과 동일)
```
main
 └── feature/friend1-추천피드
 └── feature/walk2-배변기록
      ...티켓 1개 = 브랜치 1개 (../TICKETS.md 공통 원칙)
```
백엔드(Spring Boot)와 프론트(iOS) 저장소를 분리할지 모노레포로 할지는 별도 결정
필요 — 우선 이 문서 체계는 저장소 구조와 무관하게 적용 가능하도록 작성함.

### 티켓 단위 파이프라인 (iOS 쪽, 기존과 동일)
```
1. 티켓 선택 (../TICKETS.md)
        │
2. [의존성 있으면] api-contract 서브에이전트로 API 명세(api/) 검증
        │
3. 구현 (swiftui-conventions 스킬 준수)
        │
4. qa-tester 서브에이전트로 테스트 작성 + 실행
        │
5. swift-reviewer 서브에이전트로 리뷰
        │   REQUEST_CHANGES → 수정 → 재리뷰 (최대 2회)
        ▼
6. PR 생성 → Xcode Cloud 자동 빌드+테스트
        │
7. 머지 → main
```

### 백엔드(Spring Boot) CI/CD — 자체 호스팅 러너 방식 (2026-07-08 신규)

가정용 네트워크는 GitHub의 클라우드 러너가 SSH로 인바운드 접속하기 까다로운 경우가
많다(방화벽/NAT). 대신 **GitHub Actions 자체 호스팅 러너(self-hosted runner)를
지인 컴퓨터에 직접 설치**하면, 그 머신이 GitHub에 폴링(아웃바운드 연결)해서 작업을
받아오므로 인바운드 포트를 열 필요가 없다.

```
1. 지인 컴퓨터에 GitHub Actions self-hosted runner 설치·등록
        │
2. [개발자] main에 push
        │
3. GitHub Actions 워크플로우 트리거 → 지인 컴퓨터의 러너가 작업 수신
        │
4. 러너가 로컬에서 실행:
   - Gradle/Maven 빌드 + 테스트
   - Docker 이미지 빌드
   - docker-compose down && docker-compose up -d (또는 systemd 서비스 재시작)
        │
5. Flyway가 컨테이너 기동 시 자동으로 미확인 마이그레이션 적용 (Supabase Postgres에)
        │
6. Caddy가 새 컨테이너로 리버스 프록시, HTTPS는 그대로 유지(인증서 갱신은 Caddy가 자동 처리)
```

### 배포 파이프라인 (전체)
```
[백엔드] push → main → 자체 호스팅 러너 → 빌드/테스트/Docker 배포 (지인 컴퓨터)
[iOS] Xcode Cloud (PR마다 빌드+테스트) → main 머지 → TestFlight
        │
        ▼
../TICKETS.md Q4 후보 지역(이촌동 등) 집중 베타 초대
   — 오프라인 홍보를 집중할 지역이지, 기능을 차단하는 지역이 아니다(Q8 참조)
        │
        ▼
   App Store 정식 출시 (전국)
```

⚠️ **2026-08-15 변경**: v3에서 하드 지역 게이팅의 필요성이 약해졌다(반경 자동
확장이 "후보 0" 문제를 흡수). 베타 단계에서 지역을 좁히는 것은 **홍보 집중** 목적이며,
앱 기능을 지역으로 차단할지는 **../TICKETS.md Q8 미결정**이다.

---

*최초 작성: 2026-07-08 (백엔드 아키텍처 변경 반영).
v3 갱신: 2026-08-15 (../product/CONCEPT.md — 브랜드명, 누끼 Phase 1 승격,
실시간 위치 제외, R2 확정, V3 마이그레이션 필요 표기).*
