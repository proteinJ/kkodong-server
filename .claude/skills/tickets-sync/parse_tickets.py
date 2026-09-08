#!/usr/bin/env python3
"""docs/TICKETS.md 에서 티켓을 구조화해 뽑는다.

LLM이 문서를 눈으로 읽고 목록을 만들면 티켓을 빠뜨리거나 지어낸다.
목록 추출만은 결정적으로 처리하고, 판단이 필요한 부분만 사람/모델에게 넘긴다.

출력: JSON 배열 (stdout)
"""
import json
import re
import sys
from pathlib import Path

TICKET_RE = re.compile(r"^### (?P<id>[A-Z]+-\d+)\.\s*(?P<title>.+?)\s*$")
EPIC_RE = re.compile(r"^## (?P<epic>[A-Z]+)\s+—\s*(?P<desc>.+?)\s*$")
# 구현 현황 표: | FOUNDATION-1 (회원가입/로그인) | 🟡 거의 완료 | 비고 |
ROW_RE = re.compile(r"^\|\s*\**(?P<ids>[A-Z]+-[\d/]+)[^|]*\|\s*(?P<status>[^|]+?)\s*\|\s*(?P<note>.*?)\s*\|\s*$")

STATUS_BY_EMOJI = {"🟢": "done", "🟡": "partial", "⬜": "todo", "🟠": "blocked"}


def status_of(cell: str) -> str:
    for emoji, name in STATUS_BY_EMOJI.items():
        if emoji in cell:
            return name
    return "unknown"


def parse(text: str) -> list[dict]:
    lines = text.splitlines()

    # 1) 구현 현황 표 → 티켓 ID별 상태. "FRIEND-2/3" 처럼 묶인 행은 펼친다.
    status: dict[str, dict] = {}
    for line in lines:
        m = ROW_RE.match(line)
        if not m:
            continue
        ids, rest = m["ids"].split("-", 1)
        for num in rest.split("/"):
            status[f"{ids}-{num}"] = {
                "status": status_of(m["status"]),
                "status_raw": re.sub(r"\*+", "", m["status"]).strip(),
                "note": re.sub(r"\*+", "", m["note"]).strip(),
            }

    # 2) ### 헤딩 → 티켓 본문. 표가 아니라 이쪽이 티켓의 정본이다
    #    (표는 FRIEND-2/3 처럼 묶어놔서 개수가 안 맞는다).
    tickets: list[dict] = []
    epic = None
    for i, line in enumerate(lines):
        if em := EPIC_RE.match(line):
            epic = em["epic"]
            continue
        tm = TICKET_RE.match(line)
        if not tm:
            continue

        end = len(lines)
        for j in range(i + 1, len(lines)):
            if lines[j].startswith(("## ", "### ", "---")):
                end = j
                break
        body = "\n".join(lines[i + 1 : end]).strip()

        title = re.sub(r"\s*(🟢|🟡|⬜|🟠|★).*$", "", tm["title"]).strip()
        info = status.get(tm["id"], {})
        tickets.append(
            {
                "id": tm["id"],
                "epic": epic,
                "title": title,
                "status": info.get("status", "unknown"),
                "status_raw": info.get("status_raw", ""),
                "note": info.get("note", ""),
                "body": body,
                "line": i + 1,
            }
        )
    return tickets


if __name__ == "__main__":
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/TICKETS.md")
    print(json.dumps(parse(path.read_text(encoding="utf-8")), ensure_ascii=False, indent=2))
