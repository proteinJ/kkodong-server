# 문서

꼬동의 기획·설계 문서입니다. 서버 실행 방법과 기술적 의사결정 요약은
[루트 README](../README.md)에 있습니다.

## 무엇부터 읽을까

| 알고 싶은 것 | 문서 |
|---|---|
| 이 서비스가 뭘 하는 건지 | [`product/CONCEPT.md`](product/CONCEPT.md) |
| 왜 이 시장에 들어가는지, 경쟁 지형은 어떤지 | [`product/MARKET.md`](product/MARKET.md) |
| 어떤 결정을 왜 그렇게 내렸는지 | [`product/DECISIONS.md`](product/DECISIONS.md) |
| API 계약 | [`design/API_SPEC.md`](design/API_SPEC.md) · [`openapi.yaml`](design/openapi.yaml) |
| 테이블 구조와 그 근거 | [`design/DB_SCHEMA.sql`](design/DB_SCHEMA.sql) |
| 추천 알고리즘이 어떻게 동작하는지 | [`design/RECOMMENDATION.md`](design/RECOMMENDATION.md) |
| 지금 뭐가 됐고 뭐가 남았는지 | [`TICKETS.md`](TICKETS.md) |

## 구조

```
docs/
├── TICKETS.md          티켓별 진행 현황 · 착수 순서 · 미결정 사항(O1~O7)
├── product/
│   ├── CONCEPT.md      제품 기획서 (v3, 현행 기준)
│   ├── MARKET.md       시장조사 · 경쟁 분석 (+ 부록: 똑독 기능 분해)
│   └── DECISIONS.md    결론이 난 항목과 그 근거 (D1~D6)
├── design/
│   ├── API_SPEC.md     엔드포인트별 계약 · 서버 구현 규약
│   ├── openapi.yaml    같은 내용의 기계 판독용
│   ├── DB_SCHEMA.sql   전체 스키마 최종형. 각 결정의 근거가 주석에 있다
│   ├── RECOMMENDATION.md  추천 점수 공식 · 신호별 계산식 · 이유 문장 템플릿
│   └── MARKING_SPOT.md    마킹 스팟(WALK-4) 설계
├── archive/            대체된 문서. 히스토리 보존용이며 현행과 충돌하면 현행이 우선
└── seed/               지역 조사 원자료
```

## 규칙

- **현행 기준은 `product/CONCEPT.md`(v3) 하나다.** `archive/`의 v1·v2와 충돌하면
  언제나 v3가 우선한다
- **결정된 것은 `DECISIONS.md`, 안 된 것은 `TICKETS.md`.** 한 항목이 양쪽에 동시에
  있으면 안 된다 — 결론이 나면 `TICKETS.md`에서 지우고 `DECISIONS.md`로 옮긴다
- **구현이 명세와 어긋나면 `API_SPEC.md`의 "미해결" 절에 적는다.** 명세를 코드에
  맞추지 않는다 — 코드를 고칠 대상으로 남긴다
- **적용된 Flyway 마이그레이션은 주석 한 글자도 고치지 않는다.** 체크섬 검증이 깨진다
