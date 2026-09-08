#!/usr/bin/env python3
"""GitHub 이슈 → docs/STATUS.md 생성.

상태 표를 TICKETS.md 안에 두면 3인이 같은 파일을 동시에 고쳐 충돌한다.
그래서 생성물만 이 파일로 분리했다. 손으로 고치지 말 것.
"""
import json, re, subprocess, sys
from collections import OrderedDict
from datetime import date

REPO = "proteinJ/kkodong-server"
BOARD = "https://github.com/users/proteinJ/projects/3"

# 에픽 표시 순서. 없는 접두어는 뒤에 알파벳순으로 붙는다.
ORDER = ["FOUNDATION","ONBOARD","FRIEND","CHAT","WALK","CARD","COMMUNITY",
         "PET","SAFETY","MEET","PASS","MAP","KG","PN"]


def fetch():
    out = subprocess.run(
        ["gh","issue","list","--repo",REPO,"--state","all","--limit","300",
         "--json","number,title,state,labels,milestone,assignees"],
        capture_output=True, text=True, check=True).stdout
    rows = []
    for i in json.loads(out):
        m = re.match(r"\[([A-Z]+)-(\d+)\]\s*(.+)", i["title"])
        if not m:
            continue
        epic, num, title = m.group(1), m.group(2), m.group(3)
        labels = {l["name"] for l in i["labels"]}
        if i["state"] == "CLOSED":            status = "🟢 완료"
        elif "blocked" in labels:             status = "🟠 보류"
        elif "server:done" in labels:         status = "🟡 서버 완료·프론트 남음"
        elif "status:in-progress" in labels:  status = "🟡 진행 중"
        else:                                 status = "⬜ 미착수"
        rows.append({
            "n": i["number"], "epic": epic, "id": f"{epic}-{num}", "num": int(num),
            "title": title, "status": status,
            "who": ", ".join(a["login"] for a in i["assignees"]) or "—",
            "ms": (i["milestone"] or {}).get("title", "—"),
        })
    return rows


def render(rows):
    groups = OrderedDict()
    for r in sorted(rows, key=lambda r: (ORDER.index(r["epic"]) if r["epic"] in ORDER else 99,
                                         r["epic"], r["num"])):
        groups.setdefault(r["epic"], []).append(r)

    done = sum(1 for r in rows if r["status"].startswith("🟢"))
    out = [
        "# 진행 현황",
        "",
        "<!-- 이 파일은 `/tickets-sync` 가 GitHub 이슈에서 생성한다. 손으로 고치지 말 것. -->",
        "<!-- 직접 편집한 PR은 반려한다. 상태를 바꾸려면 이슈를 닫거나 라벨을 고쳐라. -->",
        "",
        f"생성일 {date.today().isoformat()} · 전체 {len(rows)}건 중 완료 {done}건",
        "",
        f"정본은 [GitHub 이슈](https://github.com/{REPO}/issues)이고, 보드는 [여기]({BOARD})다.",
        "작업 규칙은 [`CONTRIBUTING.md`](CONTRIBUTING.md), 티켓 정의는 [`TICKETS.md`](TICKETS.md).",
        "",
    ]
    for epic, items in groups.items():
        n_done = sum(1 for r in items if r["status"].startswith("🟢"))
        out += [f"## {epic}  ({n_done}/{len(items)})", "",
                "| 이슈 | 티켓 | 상태 | 담당 | 마일스톤 |", "|---|---|---|---|---|"]
        for r in items:
            out.append(f"| [#{r['n']}](https://github.com/{REPO}/issues/{r['n']}) "
                       f"| **{r['id']}** {r['title']} | {r['status']} | {r['who']} | {r['ms']} |")
        out.append("")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "docs/STATUS.md"
    rows = fetch()
    open(path, "w", encoding="utf-8").write(render(rows))
    print(f"{path} — {len(rows)}건 기록")
