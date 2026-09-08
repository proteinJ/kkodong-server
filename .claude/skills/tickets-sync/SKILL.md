---
name: tickets-sync
description: docs/TICKETS.md 의 티켓을 GitHub 이슈·마일스톤과 맞춘다. 새 티켓을 이슈로 만들고, 문서의 구현 현황 표를 이슈 상태로부터 다시 생성한다. "티켓 동기화", "이슈 만들어줘", "진행상황 갱신" 요청 시 사용.
---

# 티켓 ↔ GitHub 이슈 동기화

## 역할 분담 (이걸 어기면 금방 썩는다)

| 무엇 | 정본 | 누가 고치나 |
|---|---|---|
| 티켓의 정의 · 근거 · 주의사항 | `docs/TICKETS.md` | 사람이 손으로 |
| **진행 상태** | **GitHub 이슈** (open/closed, 라벨) | 사람 또는 Claude가 이슈에서 |
| `docs/STATUS.md` | 없음 — **이슈에서 생성** | 이 스킬이 덮어씀 |

즉 **상태를 문서에서 손으로 고치지 않는다.** 표는 이슈의 거울일 뿐이다.
2026-09-08 이전에 이 표를 손으로 관리하다가 두 건이 어긋났다(iOS 진행 상황, FRIEND-1 범위).

## 절차

### 1. 티켓 목록 뽑기 (결정적)

```bash
python3 .claude/skills/tickets-sync/parse_tickets.py docs/TICKETS.md
```

문서를 눈으로 읽어 목록을 만들지 말 것 — 빠뜨리거나 없는 티켓을 지어낸다.
이 스크립트가 `### <ID>. <제목>` 헤딩에서 24개 안팎을 뽑아준다.

### 2. 현재 이슈 읽기

```bash
gh issue list --state all --limit 200 --json number,title,state,labels,milestone
```

티켓 ID는 **제목 맨 앞 대괄호**로 매칭한다: `[FRIEND-2] 친구 신청 / 수락`.
ID가 이미 있으면 새로 만들지 않는다. 제목·라벨·마일스톤만 필요 시 고친다.

### 3. 없는 것만 만들기

```bash
gh issue create --title "[FRIEND-2] 친구 신청 / 수락" \
  --body-file <임시파일> --label "epic:FRIEND,area:server" --milestone "2. 친구 ★핵심"
```

본문에는 **TICKETS.md 해당 절 링크와 요약만** 넣는다. 본문 전체를 복사하면
두 곳이 갈라진다. 형식:

```markdown
> 정의: [`docs/TICKETS.md`](../blob/main/docs/TICKETS.md) — `### FRIEND-2.` 절

<티켓 본문의 **내용** 항목 한두 줄>

**의존성**: ...
**주의**: ...
```

### 4. 라벨 · 마일스톤 규칙

- 라벨: `epic:<EPIC>` — Phase 1(FOUNDATION, ONBOARD, FRIEND, CHAT, WALK, CARD,
  COMMUNITY, PET, SAFETY, MEET, PASS) + Phase 3(PN 점주앱, KG 견주 유치원, MAP 지도)
- 라벨: `area:server` / `area:ios` — 티켓 본문에 "클라이언트 작업"이면 ios, 아니면 server. 둘 다면 둘 다.
- 라벨: `blocked` — 문서 상태가 🟠 보류이거나 선행 결정(O1~O7) 대기인 것
- 마일스톤은 TICKETS.md "권장 착수 순서"를 따른다:

  | 마일스톤 | 티켓 |
  |---|---|
  | `1. 기반` | FOUNDATION-1, PET-1, SAFETY-1, SAFETY-2 |
  | `2. 친구 ★핵심` | FRIEND-1, FRIEND-2, FRIEND-3 |
  | `3. 대화` | CHAT-1, CHAT-2 |
  | `4. 산책·카드` | WALK-1/2/3, CARD-1, CARD-2, MEET-2 |
  | `5. 확장` | COMMUNITY-1, COMMUNITY-2, PET-2, PASS-1 |
  | `iOS 클라이언트` | FOUNDATION-2, ONBOARD-1, ONBOARD-3 |
  | (없음) | ONBOARD-2 — 보류, MEET-1 — 코드 0줄이라 이슈만 |

### 5. 드리프트 보고 — 자동으로 닫지 말 것

문서 상태(🟢/🟡/⬜)와 이슈 상태(open/closed)가 어긋나면 **표로 보고만 하고 멈춘다.**
이슈를 닫는 건 "이 일이 끝났다"는 사람의 판단이다. 모델이 문서 이모지를 보고
닫으면, 정본을 이슈로 옮긴 의미가 사라진다.

사용자가 명시적으로 지시할 때만 닫는다:
```bash
gh issue close <번호> --comment "<무엇이 끝났는지>"
```

### 6. STATUS.md 다시 쓰기

```bash
python3 .claude/skills/tickets-sync/gen_status.py docs/STATUS.md
```

**`docs/TICKETS.md` 를 건드리지 않는다.** 3인이 동시에 만지는 파일이라 생성물을
그 안에 두면 머지 충돌이 난다. 상태는 `docs/STATUS.md` 한 곳에만 쓴다.

상태 표기는 스크립트가 정한다: closed → 🟢 / `blocked` → 🟠 /
`server:done` → 🟡 서버 완료·프론트 남음 / `status:in-progress` → 🟡 진행 중 / 그 외 → ⬜.

## 하지 말 것

- 이슈를 지우지 않는다. 잘못 만들었으면 닫고 이유를 댓글로 남긴다
- `docs/TICKETS.md` 를 고치지 않는다. 상태는 `docs/STATUS.md` 로만 나간다
- 담당자(assignee)를 임의로 바꾸지 않는다. 영역 분담은 `docs/CONTRIBUTING.md` 1절이 정본이다
- 만들기 전에 **무엇을 만들지 먼저 보여주고 확인받는다.** 공개 레포에 20개가
  한 번에 생기는 건 되돌리기 번거롭다
- Phase 1 제외 항목(매장·예약·커머스)과 O1~O7(미해결 질문)은 티켓이 아니다.
  이슈로 만들지 않는다
