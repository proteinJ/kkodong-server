# 꼬동 API 명세 (Phase 1) — Spring Boot

2026-08-15: 기획 v3(`../product/CONCEPT.md`) 반영해 재작성. `DB_SCHEMA.sql` +
`../TICKETS.md` 기준. `api-contract` 서브에이전트가 이 문서를 기준으로 iOS 프론트
네트워크 코드를 검증한다.

2026-08-27: **구현된 서버(`kkodong-server`)와 대조해 갱신.** JSON 필드 표기를 전부
camelCase로 정정하고, 구현과 어긋나던 HTTP status·요청/응답 스키마를 실제 코드에
맞췄다. 구현이 명세를 어기고 있는 항목은 고치지 않고 [미해결](#미해결-구현이-명세와-어긋나는-항목)
절에 모았다.

> **구현 상태(2026-08-27)**: 0절(인증·프로필) / 1절(반려견) / 11절(차단·신고)만
> 서버에 존재한다. 나머지 절은 **아직 코드가 없는 목표 계약**이므로, 착수 시점에
> 이 문서와 코드를 다시 대조해야 한다.

**v3 주요 변경**:
- **2절이 "산책 매칭(모집글)"에서 "강아지 친구"로 완전히 교체됨** —
  `/walk-posts`, `/walk-post-requests`, `/connections` 폐기 →
  `/friends/recommendations`, `/friend-requests`, `/friendships` 신설
- 산책 약속(`/walk-appointments`), 배변 기록(`/walk-sessions/{id}/bathroom-logs`),
  경로 저장(`/saved-routes`), 커뮤니티(`/community/*`), 누끼 업로드(`/dogs/{id}/cutout`) 신설
- `vaccination-records` → `care-records`로 확장
- **실시간 위치 공유(구 3절, MAP-1)는 v3 Phase 1 범위에서 제외** — 새 기획에 없는
  기능이고, 친구 루프의 목적지는 실시간 추적이 아니라 산책 약속(CHAT-2)이다.
  필요해지면 Phase 2에서 재도입

**백엔드는 Java Spring Boot 자체 서버.** 모든 API는 Spring Controller가 노출하는
일반 REST 엔드포인트이거나 Spring WebSocket(STOMP) 채널이다. Supabase는 DB(Postgres)
호스팅 용도로만 쓰이고, PostgREST/RLS/Realtime/Supabase Auth는 사용하지 않는다.

**Base URL**: `https://{도메인}/api/v1` (도메인은 ../TICKETS.md Q6에서 확정 예정)

**인증**: 로그인 성공 시 발급되는 Access Token(JWT, 단기)을
`Authorization: Bearer <access_token>` 헤더로 전달 (공개 엔드포인트 제외).
Access Token 만료 시 Refresh Token으로 갱신.

**응답 형식**: 모든 응답은 공통 envelope로 감싸진다(`global/common/ApiResponse`) —
`{success: boolean, message: string, data: T|null, code: string|null}`. 실패 시
`data`는 `null`, `code`는 `global/error/ErrorCode`의 코드값(예 `M001`), 성공 시
`code`는 `"SUCCESS"` 고정. 아래 표의 "응답"은 이 envelope의 `data` 필드 내용만
표기한다. 상세 스키마/에러코드별 HTTP status는 `openapi.yaml` 참조.

⚠️ **성공 응답에 204를 쓰는 엔드포인트는 없다.** 모든 응답이 envelope로 감싸이므로
바디가 항상 존재한다 — 삭제·로그아웃도 `200 + data: null`이다.

**JSON 필드는 camelCase다** (2026-08-19 결정 — Jackson 기본값, 별도 설정 없음).
**DB 컬럼/테이블명은 snake_case를 유지**하므로, 이 문서에서 `users.home_location`
처럼 쓰인 것은 DB 컬럼이지 JSON 필드가 아니다. `inappropriate_behavior`,
`community_post` 같은 **enum 값 문자열**도 snake_case 그대로다.

**위치 좌표 규약**: 클라이언트-서버 계약은 항상 **`{lat, lng}` 평범한 숫자**다
(CoreLocation이 lat/lng로 주니까). PostGIS 변환은 Service 계층에서
`ST_SetSRID(ST_MakePoint(lng, lat), 4326)` — ⚠ **순서 주의**(경도 먼저).
반경 검색은 `ST_DWithin`.

---

## 0. 인증 (FOUNDATION-1)

Spring Security + 자체 JWT(AT/RT). Supabase Auth 미사용. **RT는 Postgres가 아니라
Redis에 저장, 로그아웃 시 AT는 Redis 블랙리스트 등록.**

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 이메일 회원가입 | `POST /api/v1/auth/signup` | body: `{email, password}` (password 8자 이상). bcrypt 해시로 `users.password_hash` 저장, `display_name`은 NULL로 두고 온보딩에서 채움. **응답에 토큰 없음** — 가입 후 `/auth/login` 별도 호출. 201. 실패: 이메일 중복 400(`M001`). 구현 완료 |
| 이메일 로그인 | `POST /api/v1/auth/login` | body: `{email, password}`. 응답: `{grantType, accessToken, refreshToken, accessTokenExpiresIn}`. 실패: 자격 불일치 400(`M003` — 401 아님, `ErrorCode.INVALID_LOGIN_CREDENTIALS`가 `BAD_REQUEST`로 정의됨). 구현 완료 |
| Sign in with Apple | `POST /api/v1/auth/apple` | body: `{identityToken}`. 서버가 Apple JWKS 공개키로 서명 검증 후 `sub` claim을 `users.apple_user_id`와 매칭(없으면 신규 생성). 실패: 토큰 검증 실패 401(`A006`), **신규 생성 시 email claim이 없거나 그 이메일로 이미 가입돼 있으면 400(`M001`)** — 이메일 가입 계정과 Apple 계정을 연결(link)하는 흐름은 아직 없다. 구현 완료 |
| 토큰 갱신 | `POST /api/v1/auth/refresh` | body: `{refreshToken}`. Redis에서 유효성 확인 후 새 AT/RT 쌍 발급(RT 회전 — 기존 RT 즉시 폐기). 실패: 401(`A002`/`A003`/`A004`). 구현 완료 |
| 로그아웃 | `POST /api/v1/auth/logout` | **Authorization 헤더(AT) + body `{refreshToken}` 둘 다 필요.** Redis에서 RT 삭제 + 현재 AT를 블랙리스트 등록. 구현 완료 |
| 카카오 로그인 | `POST /api/v1/auth/kakao` | **미정** — ../TICKETS.md Q7 |
| 구글 로그인 | `POST /api/v1/auth/google` | **미정** — ../TICKETS.md Q7 |
| 전화번호 인증 | `POST /api/v1/auth/phone/*` | **미정** — ../TICKETS.md Q7 |

**공개 엔드포인트(인증 불필요)**: `/auth/signup`, `/auth/login`, `/auth/apple`,
`/auth/refresh` (+ `/swagger-ui/**`, `/v3/api-**`, `/actuator/health|info`).
`global/security/SecurityConfig`의 `permitAll` 목록과 일치한다 — 새 공개 엔드포인트를
만들면 여기와 그 목록을 같이 고쳐야 한다.

⚠️ **토큰 응답에 `user` 객체가 없다.** 로그인 직후 온보딩 분기
(`onboardingCompletedAt`)를 판단하려면 클라이언트가 `GET /users/me`를 한 번 더
호출해야 한다 — FOUNDATION-2 라우팅에서 왕복 1회가 추가된다.

### 내 프로필
| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 내 프로필 조회 | `GET /api/v1/users/me` | 응답: `{id, email, role, displayName, profileImageUrl, onboardingCompletedAt}`. 구현 완료 |
| 프로필 수정 | `PATCH /api/v1/users/me` | body: `{displayName?, profileImageUrl?, homeLocation?: {lat, lng}, walkTimeSlots?}` — 전부 선택, 미포함 필드는 "변경하지 않음". 응답은 조회와 같은 형태. 구현 완료 |
| 비밀번호 변경 | `PATCH /api/v1/users/me/password` | body: `{currentPassword, newPassword}` (newPassword 8자 이상). 소셜 전용 계정은 `password_hash`가 없어 항상 400(`M005`). 구현 완료 |
| 온보딩 완료 처리 | `PATCH /api/v1/users/me/onboarding` | `onboarding_completed_at = now()`, 재호출해도 덮어쓰지 않음(멱등). **선행조건 검증 있음 — 아래 참조.** 구현 완료 |
| 계정 삭제 | `DELETE /api/v1/users/me` | 연관 데이터 cascade 삭제. **Apple 심사 가이드라인 5.1.1(v) 필수**. 구현 완료 |

> ✅ **경로 리네임 완료(2026-08-19)**: `/api/v1/members/*` → `/api/v1/users/*`,
> `Member*` 클래스·JWT 클레임(`memberId` → `userId`)까지 전부 `User`로 통일됐다.
>
> ✅ **camelCase 반영 완료(2026-08-27)**: 이 문서와 `openapi.yaml`의 JSON 필드 표기를
> 전부 camelCase로 정정했다. **DB 컬럼/테이블명은 snake_case 유지**다.

**프로필 응답에 없는 것**:

- **위치가 없다** — `home_location`은 본인 조회에도 응답에 싣지 않는다. 타인의 위치는
  어떤 경로로도 좌표 그대로 노출되지 않는다
- **`walkTimeSlots`가 없다** — PATCH로 저장은 되지만 응답에 실리지 않아, 클라이언트가
  방금 저장한 값을 되받아 확인할 수 없다. 온보딩 UI가 선택 상태를 복원하려면 응답에
  추가해야 한다([미해결](#미해결-구현이-명세와-어긋나는-항목) 3번)
- `subscriptionTier`(PASS-1) / `phoneVerifiedAt`(Q7) / `createdAt` — DB에는 있으나
  아직 어떤 응답에도 실리지 않는다

**계정 삭제 주의**: Redis의 Refresh Token은 key가 토큰 문자열이라 userId로 일괄
삭제되지 않는다 — **탈퇴 후에도 남은 RT가 자연 TTL(7일)까지 살아 있다.** 즉시
무효화가 필요하면 `RefreshToken`에 userId 보조 인덱스를 추가해야 한다.

**온보딩 완료 선행조건** — 서버(`UserService.onboarding`)는 아래가 모두 채워져야
완료 처리하고, 하나라도 비면 400(`M006`)이다:

| # | 조건 | 상태 |
|---|---|---|
| 1 | `displayName` | 계약대로 |
| 2 | `homeLocation` | 계약대로 |
| 3 | 등록된 반려견 1마리 이상 | 계약대로 |
| 4 | `walkTimeSlots`가 비어있지 않음 | 🔴 **계약 위반 — 서버 수정 필요** |

**계약상 필수는 1~3번뿐이다.** 산책 시간대는 "건너뛰기 허용, 미입력은 추천에서
중립(0.5)"이 확정 설계인데(ONBOARD-3, `RECOMMENDATION.md` 2절), 현재
구현은 시간대를 건너뛴 유저가 **온보딩을 영원히 완료하지 못하게** 만든다 —
`onboarding_completed_at`이 FOUNDATION-2 라우팅 분기 기준이므로 앱 진입 자체가
막힌다. 4번 조건과 `M006` 메시지를 서버에서 걷어내야 한다
([미해결](#미해결-구현이-명세와-어긋나는-항목) 1번).

**`walkTimeSlots`** (2026-08-27 신설 — FRIEND-1 추천 신호):

```json
{ "walkTimeSlots": ["morning", "evening"] }
```

| 값 | 표시 |
|---|---|
| `dawn` / `morning` / `noon` / `afternoon` / `evening` / `night` | 새벽(05~08) / 아침(08~11) / 낮(11~15) / 오후(15~18) / 저녁(18~21) / 밤(21~24) |

- **최대 3개**. 빈 배열 허용 — 미입력은 추천에서 중립(0.5) 처리
- 값 유효성은 서버 설정값(`kkodong.user.walk-time-slot.slots`, 위반 시 400 `M007`),
  개수 제한은 DTO `@Size` + DB CHECK(`chk_users_walk_time_slots`) — 성향 태그와 같은 분담
- 슬롯 **키**는 API·DB 계약이라 바꿀 수 없다. 설정에서 자유롭게 바꿀 수 있는 것은
  표시 이름뿐이며, 키가 6개와 다르면 서버가 기동을 거부한다(`UserProperties.WalkTimeSlot`)
- 상세: [`RECOMMENDATION.md`](RECOMMENDATION.md) 2절

---

## 1. 반려견 프로필 (PET-1)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 반려견 등록 | `POST /api/v1/dogs` | body: `{name, breed?, birthDate?, gender?, size?, weightKg?, energyLevel?, personalityTraits?, neutered?, animalRegistrationNumber?}` — **필수는 `name`뿐**. `owner_id`는 서버가 JWT에서 추출(클라이언트가 안 보냄 — 위변조 방지). 201. **패스권 한도 초과 시 403 — 미구현**(아래 참조). 구현 완료 |
| 내 반려견 목록 | `GET /api/v1/dogs` | 항상 JWT 주체의 반려견만 반환. ⚠️ **`?owner=me` 파라미터는 서버가 읽지 않는다** — 타인의 목록을 조회하는 경로는 없다. 구현 완료 |
| 반려견 상세 | `GET /api/v1/dogs/{dogId}` | ⚠️ **본인 소유만** — 요청자 != `owner_id`면 403(`A005`). 타인 강아지 조회는 아래 참조. 구현 완료 |
| 반려견 수정 | `PATCH /api/v1/dogs/{dogId}` | 부분 수정 — 미포함 필드는 "변경하지 않음"(`name`도 선택). 서버가 요청자 == `owner_id` 검증(RLS 대신 Service 계층, ../archive/DEV_STACK_PIPELINE.md 2절). ⚠️ **응답에 `id`/`ownerId`가 없다**(`DogResponse.patch`). 구현 완료 |
| 반려견 삭제 | `DELETE /api/v1/dogs/{dogId}` | 구현 완료 |
| 프로필 사진 업로드 | `POST /api/v1/dogs/{dogId}/photo` (multipart) | Cloudflare R2 저장. 반환 URL을 `dogs.profile_image_url`에 저장. 구현 완료 |
| **누끼 이미지 업로드** | `POST /api/v1/dogs/{dogId}/cutout` (multipart) | **v3 신설(ONBOARD-3).** 배경 제거는 **클라이언트 온디바이스(Vision)에서 수행**하고 서버는 결과 PNG(투명 배경)를 저장만 한다 — 서버 이미지 처리 비용 0. 반환 URL을 `dogs.cutout_image_url`에 저장 |

**타인 강아지 조회**: `GET /dogs/{dogId}`는 소유자 전용이고 이 제약은 완화하지
않는다 — 응답에 `animalRegistrationNumber` 같은 소유자 전용 필드가 섞여 있기
때문이다. FRIEND-1(추천 피드)·FRIEND-3(친구 강아지 프로필 상세)이 필요로 하는
타인 공개 정보는 `DogPublic` 형태이며, **FRIEND-3 착수 시
`GET /api/v1/dogs/{dogId}/public`을 별도로 신설**한다.

**`personalityTraits`** (2026-08-15 확정 — ../TICKETS.md Q10): 아래 **8개 중 최대 3개**
문자열 배열. 빈 배열 허용.

```
활발함 / 차분함 / 사교적 / 낯가림 / 겁많음 / 장난꾸러기 / 독립적 / 애교많음
```

- 개수 제한(≤3)은 **DTO `@Size` + DB CHECK 제약**, 값 유효성은 **서버 설정값**
  (`kkodong.dog.personality.tags`, 위반 시 400 `D004`) — 태그 세트는 실사용 데이터로
  바뀔 수 있어 DB/코드에 문자열을 박아두지 않는다
- 빈 배열은 추천 점수에서 **0점이 아니라 중립(0.5)** — 상세: `RECOMMENDATION.md`
- 미입력(`null`)으로 등록하면 서버가 빈 배열로 채운다(`dogs.personality_traits`는 NOT NULL)

**enum 값 표기**: `gender`/`size`/`energyLevel`은 **대소문자를 가리지 않는다** —
서버가 `valueOf(toUpperCase())`로 변환하므로 `male`과 `MALE`이 모두 통과하고,
응답은 항상 소문자다. ⚠️ 정의되지 않은 값은 현재 400이 아니라 **500**으로 나간다
([미해결](#미해결-구현이-명세와-어긋나는-항목) 4번).

**패스권 한도 검증**: 반려견 등록 마릿수 제한은 **반드시 서버 Service 계층에서**
검증한다(`users.subscription_tier` 기준). 클라이언트 검증만으로는 우회 가능.
⚠️ **아직 미구현이다** — `users.subscription_tier`가 엔티티에 매핑돼 있지 않아
현재 서버는 마릿수를 제한하지 않는다(403이 나가지 않는다). PASS-1 착수 시 구현한다.

---

## 2. ★ 강아지 친구 (FRIEND-1/2/3) — v3 핵심

> v2의 `/walk-posts`(모집글) 계열 API는 **전부 폐기**됐다. 모집글 개념 자체가 v3에 없다.

### 2.1 추천 (FRIEND-1)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 주변 강아지 추천 | `GET /api/v1/friends/recommendations?dogId={myDogId}&limit=20&cursor=` | 내 강아지 기준 추천 목록 |

**응답 항목(요소당)**:
```json
{
  "dog": {
    "id": "...", "name": "몽이", "breed": "포메라니안",
    "ageMonths": 26, "size": "small", "energyLevel": "high",
    "personalityTraits": ["활발함", "사교적"],
    "cutoutImageUrl": "https://...", "profileImageUrl": "https://..."
  },
  "owner": { "id": "...", "displayName": "..." },
  "distanceKm": 2,
  "reason": "저녁에 산책하는 것도 같아요, 둘 다 활발한 성격이에요",
  "requestStatus": "none"
}
```

**점수 공식** (2026-08-15 확정 / **2026-08-27 개정 — 산책 시간대 신설**. 전체 명세는
[`RECOMMENDATION.md`](RECOMMENDATION.md)):

```
score = 30 × distance_score
      + 15 × time_slot_score      ★ 2026-08-27 신설
      + 20 × personality_score
      + 15 × age_score
      + 12 × size_score
      +  8 × breed_score
```
성별·중성화는 초기 가중치 **0(미사용)**. 각 항은 0.0~1.0으로 정규화, 합계 100.

> **시간대 신호를 왜 넣었나**: 거리 논리("만나러 갈 수 없으면 친구가 안 된다")를
> 시간축에 그대로 적용한 것이다 — 같은 동네여도 나는 새벽 6시, 상대는 밤 11시면
> 평생 안 마주친다. 합계 100을 유지하려고 거리 35→30, 성격 25→20, 체급 15→12,
> 견종 10→8로 재배분했다. **실제 값은 `application.yml`의
> `kkodong.recommendation.weights.*`가 기준이다** — 이 문서와 어긋나면 설정값이 맞다.

**서버 구현 규약 (이걸 어기면 v2에서 HOLD됐던 콜드스타트 문제가 재발한다)**:
1. **성격·견종·나이·산책 시간대를 WHERE 조건으로 쓰지 않는다.** 반경만 조건이고
   나머지는 전부 ORDER BY 가중치 점수. 조건이 안 맞아도 후보에서 사라지지 않고
   순위만 내려간다 — 시간대도 예외가 아니다(겹치지 않아도 후보로 남는다)
2. 결과가 `limit` 미만이면 **반경을 `1→2→3→5km`로 자동 확장**해 재조회.
   **반경은 첫 페이지에서 확정해 cursor에 인코딩**한다 — 페이지마다 다시 계산하면
   후보 집합이 바뀌어 중복·누락이 생긴다. 상한 5km는 ../TICKETS.md Q8 잠정값
3. 가중치·반경·태그 세트·시간대 슬롯은 **`application.yml` 설정값으로 분리** —
   재배포 없이 조정 가능해야 함(`kkodong.recommendation.*` / `kkodong.dog.*` /
   `kkodong.user.*`. 소유 도메인 아래 두고 recommendation이 참조한다 —
   반대로 두면 강아지 등록이 추천 설정을 읽게 되어 의존 방향이 뒤집힌다)
4. **점수를 응답에 넣지 않는다.** 대신 `reason` 문장을 서버가 생성해 내려준다.
   문장은 거리를 제외한 신호 중 상위 2개로 조합(거리는 후보 전원에 해당돼 정보가 없다)
5. `distanceKm`는 **정수로 반올림**해서 내려준다. 상대방 좌표(`home_location`)는
   **절대 응답에 포함하지 않는다** — 흐림은 저장이 아니라 직렬화 단계에서
   (V2 마이그레이션 설계 원칙)
6. **데이터 미입력은 0점이 아니라 중립(0.5)** — 성향 태그·생년월일·체급을 건너뛴
   신규 유저가 추천 하위에 영구히 깔리면 스스로 콜드스타트를 만드는 셈이다
7. 점수를 소수점 첫째 자리에서 반올림한 뒤 **동점 그룹 내 무작위 셔플**
   (시드 = 내 `user_id` + 오늘 날짜). 완전 결정적이면 상위 강아지가 응답하지 않아도
   계속 상위에 남아 추천 화면이 정체된다
8. **`home_location` 미설정 유저는 추천을 받을 수도, 후보가 될 수도 없다**
   (거리 계산 기준점이 없다). 온보딩이 이 값을 필수로 받는 이유다
9. 제외 대상: 본인 소유 강아지, **차단 관계(SAFETY-1) 양방향**,
   **이미 친구인 견주의 강아지 전부**(friendship 이 견주 단위이므로 — 이미 채팅방이
   있는 상대를 다시 추천하는 것은 소음이다), pending 신청이 있는 상대
   (`requestStatus`로 구분해 노출할지는 클라이언트 선택)

### 2.2 친구 신청 / 수락 (FRIEND-2)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 친구 신청 | `POST /api/v1/friend-requests` | body: `{requesterDogId, receiverDogId, message?}`. 서버가 `requesterDogId`의 소유자 == 요청자인지 검증. **차단 관계면 403**. pending 중복이면 409 |
| 보낸 신청 목록 | `GET /api/v1/friend-requests?direction=sent&status=pending` | |
| 받은 신청 목록 | `GET /api/v1/friend-requests?direction=received&status=pending` | |
| 신청 취소 | `DELETE /api/v1/friend-requests/{requestId}` | 신청자 본인, `pending`일 때만. `status='cancelled'` |
| 신청 수락/거절 | `POST /api/v1/friend-requests/{requestId}/respond` | body: `{accept: boolean}`. **수락 시 `@Transactional`**: `friend_requests.status='accepted'` + `friendships` upsert(**`user_a_id < user_b_id` 정렬 후 저장**) + 채팅 개설. 거절 시 상태만 변경 |

> ★ **friendship 은 견주 쌍에 유일하다 (2026-08-27 결정).** 신청은 강아지 단위지만
> 관계는 견주 단위다. 이미 그 견주와 친구인 상태에서 다른 강아지로 신청이 수락되면
> **새 friendship 을 만들지 않고 기존 것을 재사용**한다(채팅방도 그대로).
> 강아지 쌍을 유일 단위로 두면 다견 견주에게 같은 사람과의 채팅방이 2개 생긴다.
> 근거: `DB_SCHEMA.sql` friendships 주석.

**푸시**: 신청 발생·수락 시 상대에게 APNs 발송.

**지표 기록(중요)**: 신청 수 / 수락 수 / 수락까지 걸린 시간을 **Phase 1부터 로깅**한다.
**친구 신청 수락률이 이 제품의 1순위 지표**이자 추천 가중치 조정의 유일한 근거다.

### 2.3 친구 목록 (FRIEND-3)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 내 친구 목록 | `GET /api/v1/friendships` | 응답: 상대 강아지·견주 요약 + 최근 메시지 + 함께한 산책 횟수 |
| 친구 상세 | `GET /api/v1/friendships/{friendshipId}` | |
| 친구 끊기 | `DELETE /api/v1/friendships/{friendshipId}` | 양쪽 모두 가능. 채팅·약속 비활성화 |

---

## 3. 채팅 & 산책 약속 (CHAT-1, CHAT-2) — Spring WebSocket(STOMP)

### 3.1 채팅

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 메시지 목록(초기 로드) | `GET /api/v1/friendships/{friendshipId}/messages?limit=50&cursor=` | 최신순 |
| 메시지 전송 | STOMP SEND `/app/chat.{friendshipId}` | body: `{content}`. 서버가 `sender_id`를 JWT에서 추출해 `chat_messages` INSERT 후 `/topic/chat.{friendshipId}` 브로드캐스트 + 상대 오프라인이면 APNs 푸시 |
| 실시간 수신 | STOMP SUBSCRIBE `/topic/chat.{friendshipId}` | |

- **연결**: `wss://{도메인}/ws` (STOMP handshake), 연결 시 JWT 전달해 인증
- **권한 검증(백엔드)**: SUBSCRIBE/SEND 시 요청자가 해당 `friendship`의 당사자
  (`user_a_id`/`user_b_id`)인지, **차단 관계가 아닌지**(SAFETY-1) 확인 후 거부/허용.
  RLS가 없으므로 직접 작성해야 한다

### 3.2 산책 약속 (v3 신설)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 약속 제안 | `POST /api/v1/walk-appointments` | body: `{friendshipId, scheduledAt, placeName?, lat?, lng?}`. `proposed_by_user_id`는 JWT에서 추출 |
| 약속 응답 | `POST /api/v1/walk-appointments/{id}/respond` | body: `{accept: boolean}`. 제안자 본인은 응답 불가(400) |
| 약속 취소 | `POST /api/v1/walk-appointments/{id}/cancel` | 양쪽 모두 가능 |
| 약속 목록 | `GET /api/v1/walk-appointments?friendshipId={id}&status=accepted` | 채팅 상단 고정 배너용 |
| 다가오는 약속 | `GET /api/v1/walk-appointments?upcoming=true` | 전체 친구 대상 |

**푸시**: 제안·수락·취소 시 상대에게, 약속 시각 전(예: 1시간 전) 양쪽에 리마인더.

**안전 넛지**: 해당 친구와의 **첫 약속이 수락된 직후** 클라이언트가 안전 수칙 문구를
노출한다 — "처음 만나는 친구라면 안전한 장소에서, 목줄을 유지한 채 짧게 인사부터
시작해보세요"(../TICKETS.md Q3). 서버는 첫 약속 여부 판단용으로 해당 friendship의
과거 `completed` 약속 존재 여부를 응답에 포함한다.

---

## 4. 푸시 알림 디바이스 토큰

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 디바이스 토큰 등록/갱신 | `POST /api/v1/device-tokens` | body: `{apnsToken, platform: 'ios'}`. upsert. 앱 실행 시마다 호출 |
| 디바이스 토큰 삭제 | `DELETE /api/v1/device-tokens/{token}` | 로그아웃 시 |

**푸시 발송(백엔드 내부 로직, 별도 API 아님)**: 친구 신청/수락, 채팅 메시지, 산책
약속 제안/수락/리마인더, 커뮤니티 댓글 발생 시 Spring Boot가 **직접 APNs 호출**
(`pushy` 등 Java APNs 클라이언트 라이브러리 후보).

---

## 5. 산책 기록 (WALK-1/2/3)

### 5.1 세션

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 산책 시작 | `POST /api/v1/walk-sessions` | body: `{dogId, walkAppointmentId?, startedAt}` |
| 산책 종료 | `POST /api/v1/walk-sessions/{id}/complete` | body: `{endedAt, distanceMeters, route, weatherCondition?, weatherTempCelsius?}`. `route`는 `[{lat, lng, ts}, ...]`. 날씨 값은 클라이언트가 조회해 전달(날씨 API 미정) |
| 산책 기록 목록 | `GET /api/v1/walk-sessions?dogId={id}&cursor=` | 지난 산책 다시보기용. 응답에 `route` 포함 |
| 산책 기록 상세 | `GET /api/v1/walk-sessions/{id}` | |

### 5.2 배변 기록 (WALK-2, v3 신설)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 배변 기록 추가 | `POST /api/v1/walk-sessions/{id}/bathroom-logs` | body: `{type: 'poop'\|'pee', loggedAt, lat?, lng?}`. **`@Transactional`로 `walk_sessions.poop_count`/`pee_count` 함께 증가** |
| 배변 기록 삭제 | `DELETE /api/v1/walk-bathroom-logs/{logId}` | 오탭 취소용. 집계 컬럼 함께 감소 |
| 배변 기록 목록 | `GET /api/v1/walk-sessions/{id}/bathroom-logs` | |

**배치 전송 지원(권장)**: 산책 중 네트워크는 불안정한 게 정상이므로, 클라이언트는
로컬 큐에 쌓아 두고 일괄 전송할 수 있어야 한다. 서버는 `POST .../bathroom-logs`가
**배열 body도 받도록** 설계한다(멱등성을 위해 클라이언트 생성 `clientLogId`를
받아 중복 INSERT를 막을 것 — 재전송 시 카운트가 중복 증가하면 안 된다).

### 5.3 저장된 경로 (WALK-3, v3 신설)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 경로 저장 | `POST /api/v1/saved-routes` | body: `{name, sourceSessionId?, dogId?, path, distanceMeters?}`. `path` 미제공 시 `sourceSessionId`의 route를 서버가 복사 |
| 내 경로 목록 | `GET /api/v1/saved-routes` | |
| 경로 상세 | `GET /api/v1/saved-routes/{id}` | |
| 경로 이름 변경 | `PATCH /api/v1/saved-routes/{id}` | body: `{name}` |
| 경로 삭제 | `DELETE /api/v1/saved-routes/{id}` | |

> **동네 인기 산책로 추천은 Phase 2다** — 이 절에 API 없음. 밀도가 필요한 기능이고
> 활성화 임계치·궤적 프라이버시 처리가 미정(../TICKETS.md Q9).

---

## 6. 산책 완료 카드 (CARD-1, CARD-2)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 완료 카드 생성 | **클라이언트 렌더링** | 서버 아님 — SwiftUI 뷰를 이미지로 렌더링 후 업로드 |
| 카드 이미지 업로드 + 메타 저장 | `POST /api/v1/walk-cards` (multipart) | fields: `{walkSessionId, templateStyle}` + 이미지 파일. 응답: `{id, imageUrl, ...}` |
| 카드 공유 기록 | `PATCH /api/v1/walk-cards/{id}` | body: `{sharedAt}` (CARD-2 바이럴 측정) |
| 내 카드 목록 | `GET /api/v1/walk-cards?cursor=` | |

**`templateStyle` 서버 검증**: `users.subscription_tier`에 따라 서버가 검증한다
(Free는 `'default'`만 허용, 그 외 403). **클라이언트 검증만으로 두지 않는다** —
카드 커스터마이징이 패스권의 핵심 전환 포인트라 우회되면 수익 축이 무너진다.

**카드 내용물**: 거리·시간·날씨·**배변 횟수(poop/pee)**·**강아지 누끼 이미지** +
서비스 아이덴티티("꼬동"). 9:16.

---

## 7. 커뮤니티 (COMMUNITY-1/2, v3 신설)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 글 목록 | `GET /api/v1/community/posts?scope=nearby\|all&cursor=` | `scope=nearby`면 `users.home_location` 기준 동네 글 우선. 차단 유저 글 제외 |
| 글 상세 | `GET /api/v1/community/posts/{postId}` | |
| 글 작성 | `POST /api/v1/community/posts` | body: `{title?, content, imageUrls?}`. `service_area_id`는 서버가 작성자 위치로 판정해 채움 |
| 글 수정 | `PATCH /api/v1/community/posts/{postId}` | 작성자 본인만 |
| 글 삭제 | `DELETE /api/v1/community/posts/{postId}` | soft delete(`deleted_at`) — 신고 처리 이력 보존 |
| 이미지 업로드 | `POST /api/v1/community/images` (multipart) | 업로드 후 URL을 글 작성 body에 전달 |
| 댓글 목록 | `GET /api/v1/community/posts/{postId}/comments` | |
| 댓글 작성 | `POST /api/v1/community/posts/{postId}/comments` | body: `{content, parentCommentId?}`. `comment_count` 증가 |
| 댓글 삭제 | `DELETE /api/v1/community/comments/{commentId}` | soft delete |
| 좋아요 | `POST /api/v1/community/posts/{postId}/like` | 멱등. `like_count` 증가 |
| 좋아요 취소 | `DELETE /api/v1/community/posts/{postId}/like` | |

**노출 우선순위(차별화 지점)**: 단순 최신순이 아니라 **동네(`service_area_id`) 일치
+ 친구 관계**를 정렬에 반영한다. 이게 없으면 기존 대형 반려동물 커뮤니티와 구분되지
않는다(../product/CONCEPT.md 3.4).

**집계 컬럼**: `like_count`/`comment_count`는 비정규화 — 목록 조회 시 COUNT 서브쿼리를
돌리지 않는다. 증감은 좋아요/댓글 API와 같은 트랜잭션에서.

---

## 8. 반려견 케어 (PET-2)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 케어 기록 등록 | `POST /api/v1/dogs/{dogId}/care-records` | body: `{careType, careName, doseSequence?, administeredDate, nextDueDate?, repeatIntervalDays?, memo?}` |
| 케어 기록 목록 | `GET /api/v1/dogs/{dogId}/care-records?type=` | |
| 케어 기록 수정 | `PATCH /api/v1/care-records/{id}` | |
| 케어 기록 삭제 | `DELETE /api/v1/care-records/{id}` | |
| 다가오는 일정 | `GET /api/v1/care-records/upcoming?days=30` | 내 모든 반려견 대상, D-day 순 |
| 기본 주기 템플릿 | `GET /api/v1/care-templates?breed=&birthDate=` | 견종·나이 기반 권장 스케줄 제안(앱 내장 + 서버 오버라이드 하이브리드) |

**`careType`**: `vaccination`(차수는 `doseSequence`) / `heartworm`(월 1회,
`repeatIntervalDays=30`) / `deworming` / `checkup` / `other`.

**D-day 알림**: 기본은 클라이언트 로컬 알림(`UNUserNotificationCenter`) 예약.
장기 미접속 유저 대비 서버 배치 + APNs 병행은 검토 항목.

**입력은 전부 유저 수동** — 동물등록번호 API 연동 폐기(../TICKETS.md Q1).

---

## 9. 서비스 지역 (ONBOARD-2) 🟠 Q8 결정 대기

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 지역 목록 | `GET /api/v1/service-areas` | 공개 |
| 위치 판정 | `POST /api/v1/service-areas/check` | body: `{lat, lng}` / 응답: `{inService, serviceAreaId?, serviceAreaName?}`. PostGIS `ST_DWithin` 판정. 공개 |
| 대기 등록 | `POST /api/v1/waitlist-signups` | body: `{email, lat, lng, nearestServiceAreaId}`. 공개 |

> ⚠️ **하드 게이팅 유지 여부는 미결정(../TICKETS.md Q8).** v3의 반경 자동 확장이
> "매칭 후보 0" 문제를 흡수하므로 게이팅 명분이 약해졌다. 엔드포인트는 남겨두되
> **친구 추천(2절)에 지역 제한을 거는 로직은 Q8 결정 전까지 구현하지 않는다.**

---

## 10. 패스권 / 구독 (PASS-1)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 구독 검증/시작 | `POST /api/v1/subscriptions/verify` | body: `{signedTransaction}` — StoreKit 2 JWS. 서버가 Apple App Store Server API로 재검증 후 `subscriptions` upsert + `users.subscription_tier` 갱신. **클라이언트 신고값 불신 원칙** |
| 구독 상태 조회 | `GET /api/v1/subscriptions/me` | 응답에 해제된 권한(등록 가능 마릿수, 사용 가능 템플릿) 포함 권장 |
| 재검증 | 앱 실행 시 `/subscriptions/verify` 재호출 | Phase 1 간소화: 웹훅(App Store Server Notifications) 대신 클라이언트가 `Transaction.currentEntitlements` 확인 후 재호출 |

**해제 권한**: ① 반려견 추가 등록, ② 산책 카드 커스터마이징. 티어별 한도·가격은
../TICKETS.md Q13.

---

## 11. 안전 — 차단 & 신고 (SAFETY-1, SAFETY-2)

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 유저 차단 | `POST /api/v1/blocks` | body: `{blockedId}`. 응답: `{blockedId}`. **200(201 아님) — 멱등**. 실패: 자기 자신 400(`B001`), 없는 유저 404(`M004`). 구현 완료 |
| 차단 해제 | `DELETE /api/v1/blocks/{blockedId}` | **200 — 멱등**(차단하지 않은 상대여도 200, 404 아님). 구현 완료 |
| 차단 목록 조회 | `GET /api/v1/blocks` | 최신순. 항목: `{blockedId, displayName, profileImageUrl, createdAt}`. 페이징 없음. 구현 완료 |
| 신고 | `POST /api/v1/reports` | body: `{targetType: 'user'\|'community_post'\|'community_comment', targetId, reason, details?}` (details ≤1000자). 응답: `{reportId, status}`. 201. Phase 1은 관리자 UI 없이 테이블 적재만 — Supabase Table Editor에서 개발자가 직접 조회/상태 변경. 구현 완료 |

**차단·신고가 멱등성에서 갈리는 이유**: 차단은 **멱등(200)**이다 — 클라이언트에게
중요한 건 "새로 만들어졌는가"가 아니라 "차단된 상태인가"이고, 재시도가 409로 실패하면
UI가 불필요하게 에러를 띄워야 한다. 신고는 **접수될 때마다 새 기록이 남아야 하므로
201**이고, 처리 대기 중 중복 신고는 409(`R003`)로 거부한다(처리 큐 부풀리기 방지).
처리 완료 후 재신고는 허용된다(부분 유니크 인덱스 `uq_reports_pending`).

⚠️ **Phase 1의 신고 대상은 `user`뿐이다.** `community_post`/`community_comment`는
참조할 테이블이 아직 없어(COMMUNITY-1 미착수) 400(`R006`)으로 거부된다.
`reports.target_id`는 다형 참조라 FK를 걸 수 없어(V4 마이그레이션 주석) 대상 존재
검증은 Service가 유일한 방어선이고, 지금 통과시키면 존재하지 않는 UUID가 그대로
적재된다. CHECK 제약에는 세 값이 미리 들어가 있다.

**신고 관련 에러**: `R001`(자기 자신 신고) / `R002`(대상 없음, 404) /
`R003`(중복 대기, 409) / `R004`(잘못된 사유) / `R005`(잘못된 대상 유형) /
`R006`(아직 지원하지 않는 대상 유형).

**차단이 다른 기능에 미치는 영향(전부 Service 계층에서 직접 검증)**:
- 2.1 추천 목록에서 **양방향 제외**
- 2.2 친구 신청 **403**
- 2.3 기존 friendship이 있으면 채팅·약속 비활성화
- 3절 채팅 SUBSCRIBE/SEND 거부
- 7절 커뮤니티 글/댓글 목록에서 제외

⚠️ **이 중 구현된 것은 차단 레코드 적재뿐이다** — 추천·신청·채팅·커뮤니티가 아직
없기 때문이다. **각 도메인을 착수할 때 차단 검증을 함께 넣어야 한다.** SAFETY를
FRIEND/CHAT보다 먼저 만든 이유가 이것이므로, 빠뜨리면 순서를 지킨 의미가 없어진다.

---

## 12. 만남 진입로 (MEET-2) — v3.1 신설

> 설계 근거: [`../product/MARKET.md`](../product/MARKET.md) 4절 ②,
> [`../TICKETS.md`](../TICKETS.md) MEET-2.
> **MEET-1(운영자 산책 모임)은 API가 없다** — 앱 공지 한 줄 외에 구현물이 없는 운영 항목이다.
>
> ⚠️ 이 절부터 JSON 필드는 **camelCase**로 표기한다(2026-08-19 결정, 0절 참조).

| 동작 | 메서드/경로 | 상세 |
|---|---|---|
| 스침 후보 조회 | `GET /api/v1/walk-sessions/{walkSessionId}/encounters` | 이 산책에서 시간·구간이 겹친 강아지 후보. 산책 종료 직후 완료 카드와 함께 노출 |
| 만난 강아지 확인 | `POST /api/v1/walk-sessions/{walkSessionId}/encounters/confirm` | body: `{dogIds: [...]}`. **상호 확인 시에만** 친구 연결 |

### 12.1 스침 후보 조회

**응답**:

```json
{
  "candidates": [
    {
      "dogId": "...",
      "name": "몽이",
      "breed": "포메라니안",
      "profileImageUrl": "https://...",
      "cutoutImageUrl": "https://..."
    }
  ]
}
```

⚠️ **스친 시각·좌표를 절대 응답에 포함하지 않는다.** 강아지 식별 정보만 내려간다.
"몇 시에 어디서 스쳤다"는 이동 이력이고, 노출되는 순간 `home_location` 흐림이
무의미해진다(2.1절 위치 처리 원칙과 동일).

**후보 판정 규칙**:

```
동일 타일(25m) 또는 인접 타일에
시간 창 ±5분 이내로 양쪽 궤적이 존재  →  '스침' 후보
```

- GPS 오차(도심 10~50m)를 감안해 **시간은 좁게, 거리는 넓게** 잡는다
- 임계치는 **`application.yml` 설정값**으로 분리 — 재배포 없이 조정 가능해야 한다
- **제외 대상**: 본인 소유 강아지, 차단 관계(11절, **양방향**), 이미 친구인 상대
- 후보가 없으면 빈 배열. 클라이언트는 질문 자체를 노출하지 않는다

### 12.2 만난 강아지 확인

**요청**: `{"dogIds": ["...", "..."]}` (빈 배열 = "없었어요")

**응답**:

```json
{ "confirmed": 2, "matched": [ { "dogId": "...", "friendshipId": "..." } ] }
```

**★ 상호 확인 규칙 — 이 절의 핵심**

| 상황 | 결과 |
|---|---|
| 양쪽이 서로를 선택 | ✅ `friendships` INSERT + 채팅 개설 (`@Transactional`) |
| 한쪽만 선택 | 아무 일도 일어나지 않음. **상대에게 알리지 않는다** |

⚠️ **한쪽만 선택한 사실을 어떤 경로로도 노출하지 않는다** — 푸시·뱃지·목록 어디에도
남기지 않는다. "누군가 나를 골랐다"가 새어나가는 순간 거절이라는 개념이 생기고,
이 설계의 유일한 존재 이유(**거절 없는 진입로**)가 사라진다.

- 확인은 산책 종료 후 **24시간 이내**만 허용(그 이후엔 기억이 흐려져 오확인이 는다)
- 상호 확인으로 생긴 `friendships`는 **`source_request_id`가 NULL**이다 — 스키마에
  `source` 컬럼은 없다(`DB_SCHEMA.sql`·`V5__add_friend_domain.sql`). NULL 여부로
  FRIEND-2 경유분과 **수락률을 분리 측정**한다. 경로가 셋 이상으로 늘어나면 그때
  구분 컬럼을 추가한다
- 이미 친구인 상대를 고른 경우는 무시(에러 아님)

**지표**: 후보 노출 수 / 선택 수 / **상호 일치율**.
상호 일치율이 친구 신청 수락률(FRIEND-2)보다 유의하게 높은지가 이 설계의 성패 판정 기준이다.

---

## 폐기된 엔드포인트 (v2 → v3)

클라이언트 코드나 문서에 남아 있으면 제거할 것.

| 폐기 | 대체 |
|---|---|
| `POST /walk-posts`, `GET /walk-posts`, `/walk-posts/{id}/cancel` | 없음 — 모집글 개념 폐기 |
| `/walk-posts/{id}/requests`, `/walk-post-requests/*` | `/friend-requests/*` (2.2) |
| `GET /connections`, `/connections/{id}/messages` | `/friendships`, `/friendships/{id}/messages` |
| STOMP `/topic/location.*`, `/app/location.update` | 없음 — 실시간 위치 공유는 v3 Phase 1 범위 밖 |
| `/dogs/{dogId}/vaccinations` | `/dogs/{dogId}/care-records` (8절) |

---

## 에러 코드

`global/error/ErrorCode`가 유일한 출처다. 실패 응답의 `code` 필드에 그대로 실린다.

| 코드 | HTTP | 의미 |
|---|---|---|
| `CM001` | 400 | 올바르지 않은 입력값 (Bean Validation 실패, 역직렬화 실패) |
| `CM002` | 405 | 잘못된 HTTP 메서드 |
| `CM003` | 500 | 서버 내부 오류 |
| `CM004` | 409 | 이미 존재하는 데이터 (`DataIntegrityViolationException`) |
| `M001` | 400 | 이미 존재하는 이메일 |
| `M003` | 400 | 이메일/비밀번호 불일치 (**401이 아니다**) |
| `M004` | 404 | 존재하지 않는 회원 |
| `M005` | 400 | 비밀번호가 올바르지 않음 (소셜 전용 계정의 비밀번호 변경 포함) |
| `M006` | 400 | 온보딩 선행조건 미충족 |
| `M007` | 400 | 유효하지 않은 산책 시간대 값 |
| `A001` | 401 | 인증 실패 |
| `A002` | 401 | 토큰 만료 (로그아웃 블랙리스트 포함) |
| `A003` | 401 | 유효하지 않은 토큰 |
| `A004` | 401 | 존재하지 않거나 만료된 refresh token |
| `A005` | 403 | 접근 권한 없음 (본인 소유 아님 등) |
| `A006` | 401 | 유효하지 않은 Apple 로그인 토큰 |
| `D001` | 400 | 이미지 파일(jpg/png/webp)이 아님 |
| `D002` | 500 | 이미지 업로드 실패 |
| `D003` | 404 | 존재하지 않는 반려견 |
| `D004` | 400 | 유효하지 않은 성향 태그 |
| `B001` | 400 | 자기 자신 차단 불가 |
| `R001` | 400 | 자기 자신 신고 불가 |
| `R002` | 404 | 신고 대상 없음 |
| `R003` | 409 | 이미 처리 대기 중인 중복 신고 |
| `R004` | 400 | 유효하지 않은 신고 사유 |
| `R005` | 400 | 유효하지 않은 신고 대상 유형 |
| `R006` | 400 | 아직 지원하지 않는 신고 대상 유형 |

성공 시 `code`는 `"SUCCESS"` 고정이다. 새 도메인을 추가할 때는 그 도메인 접두사로
코드를 이어서 붙인다(예: 친구 도메인이면 `F001`~).

---

## 미해결: 구현이 명세와 어긋나는 항목

2026-08-27 대조 결과. **명세를 코드에 맞추지 않고 남겨둔 것들**이므로 해당 코드를
고쳐야 한다. 나머지 절은 전부 실제 코드 기준으로 갱신됐다.

| # | 항목 | 현재 구현 | 계약 | 영향 |
|---|---|---|---|---|
| 1 | 🔴 온보딩 선행조건 | `walkTimeSlots`가 비어 있으면 400(`M006`) | 산책 시간대는 건너뛰기 허용 | **시간대를 건너뛴 유저는 앱에 진입할 수 없다.** `UserService.onboarding`의 조건과 `M006` 메시지에서 시간대를 제거할 것 |
| 2 | 탈퇴 시 RT 잔존 | Redis RT가 자연 TTL(7일)까지 살아 있음 | 탈퇴 즉시 폐기 | 탈퇴한 계정의 토큰으로 최대 7일간 갱신이 가능하다. `RefreshToken`에 userId 보조 인덱스 필요 |
| 3 | 프로필 응답 누락 | `walkTimeSlots`가 응답에 없음 | 저장한 값은 되받을 수 있어야 함 | 온보딩 UI가 선택 상태를 복원하지 못한다. `UserResponse`에 추가 |
| 4 | enum 파싱 500 | 정의되지 않은 `gender`/`size`/`energyLevel`이 `IllegalArgumentException` → 500 | 400(`CM001`) | Report가 쓰는 방식(`parseXxx` + `BusinessException`)을 `DogService`에도 적용 |
| 5 | 404가 500으로 | 존재하지 않는 경로가 `NoResourceFoundException` → 500 | 404 | `GlobalExceptionHandler`에 핸들러 추가 |
| 6 | 패스권 마릿수 제한 | 검증 없음(무제한 등록) | 한도 초과 시 403 | 수익 축 우회. `users.subscription_tier` 매핑 + PASS-1 착수 시 |

## 미확정 항목 (백엔드 작업 착수 전 확인 필요)

1. **날씨 API 선정** — CARD-1 의존성. 미조사
2. **App Store Server API 연동 세부** — 영수증 검증 흐름 실제 구현 시 확정
3. **도메인/HTTPS** — ../TICKETS.md Q6, 지인과 협의 필요
4. **카카오/구글 OAuth + 전화번호 인증 명세** — ../TICKETS.md Q7
5. ~~**성향 태그 세트 + 추천 가중치 초기값**~~ — **확정(2026-08-15, 2026-08-27 개정)**,
   `RECOMMENDATION.md` 참조
6. **반경 자동 확장 상한** — ../TICKETS.md Q8. 잠정 5km로 추천 공식에 반영돼 있으므로,
   다르게 결정되면 `max-radius-km`·`radius-steps-km` 설정값을 함께 갱신할 것
7. ~~**이미지 저장소**~~ — Cloudflare R2로 확정(PET-1 구현 완료 시점)
8. ~~**Sign in with Apple 토큰 검증 라이브러리**~~ — 기존 템플릿 jjwt 생태계로 해결
9. ~~**PostGIS 도입 여부**~~ — 도입 확정(V1 마이그레이션 반영 완료)
10. **타인 강아지 공개 조회 경로** — FRIEND-3 착수 시
    `GET /dogs/{dogId}/public`(`DogPublic`)으로 신설 예정. 응답 필드 범위 미확정

---

*v3 재작성: 2026-08-15 (../product/CONCEPT.md 기준). 구현 대조 갱신: 2026-08-27.
최초 작성: 2026-07-08.*