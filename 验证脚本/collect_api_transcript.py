# -*- coding: utf-8 -*-
"""采集应用真实运行时的接口响应，写入 验证输出-接口响应.txt。

用法（先按 README 把应用跑起来，默认 8080 端口）：
    python 验证脚本/collect_api_transcript.py

产物用途：
  * 作为"接口确实能跑通"的可复现证据并入作业文档；
  * README 里的命令与输出可以和这份记录逐条对照。
"""

from __future__ import annotations

import datetime
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "验证脚本" / "验证输出-接口响应.txt"
BASE = "http://localhost:8080"

# (小标题, HTTP 方法, 路径, 请求体)
CALLS: list[tuple[str, str, str, str | None]] = [
    ("Week 03 · 运行状态验证接口", "GET", "/api/hello", None),
    ("Week 03 · 健康检查", "GET", "/actuator/health", None),
    ("Week 04 · 食物库（前 3 条由 script 截取）", "GET", "/api/foods", None),
    ("Week 04 · 食物分类", "GET", "/api/foods/categories", None),
    ("Week 04 · 某人的全部饮食记录", "GET", "/api/meals?eater=demo-user", None),
    ("Week 04 · 单条记录明细", "GET", "/api/meals/2", None),
    ("Week 04 · 某日热量汇总", "GET", "/api/meals/daily-summary?eater=demo-user&date=2026-09-28", None),
    ("Week 04 · 业务规则清单", "GET", "/api/meals/rules", None),
    (
        "Week 04 · 新增一条记录（成功 201）",
        "POST",
        "/api/meals",
        json.dumps(
            {
                "eater": "transcript-user",
                "mealType": "LUNCH",
                "eatenAt": "2026-12-01T12:30:00",
                "note": "接口记录采集",
                "items": [
                    {"foodId": 1, "portionGrams": 200},
                    {"foodId": 3, "portionGrams": 150},
                ],
            },
            ensure_ascii=False,
        ),
    ),
    (
        "Week 04 · 违反 R3 单餐热量上限（422）",
        "POST",
        "/api/meals",
        json.dumps(
            {
                "eater": "transcript-user",
                "mealType": "LUNCH",
                "eatenAt": "2026-12-02T12:30:00",
                "items": [{"foodId": 1, "portionGrams": 2000}],
            },
            ensure_ascii=False,
        ),
    ),
    (
        "Week 04 · 违反 R2 餐次时间窗（422）",
        "POST",
        "/api/meals",
        json.dumps(
            {
                "eater": "transcript-user",
                "mealType": "BREAKFAST",
                "eatenAt": "2026-12-03T20:00:00",
                "items": [{"foodId": 1, "portionGrams": 100}],
            },
            ensure_ascii=False,
        ),
    ),
    (
        "Week 04 · 违反 R1 食物不存在（422）",
        "POST",
        "/api/meals",
        json.dumps(
            {
                "eater": "transcript-user",
                "mealType": "LUNCH",
                "eatenAt": "2026-12-04T12:30:00",
                "items": [{"foodId": 99999, "portionGrams": 100}],
            },
            ensure_ascii=False,
        ),
    ),
    (
        "Week 04 · 字段校验失败（400）",
        "POST",
        "/api/meals",
        json.dumps({"eater": "", "mealType": "LUNCH", "eatenAt": "2026-12-05T12:30:00", "items": []},
                   ensure_ascii=False),
    ),
    ("Week 04 · 资源不存在（404）", "GET", "/api/meals/999999", None),
    ("Week 04 · 路径变量类型错误（400）", "GET", "/api/meals/abc", None),
]


def curl(method: str, path: str, body: str | None) -> tuple[int, str]:
    """用 curl.exe 发请求，返回 (HTTP 状态码, 响应体)。"""
    cmd = [
        "curl.exe", "-s", "-o", "-", "-w", "\n__STATUS__%{http_code}",
        "-X", method, BASE + path,
    ]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json; charset=utf-8", "--data-binary", "@-"]

    proc = subprocess.run(
        cmd,
        input=body.encode("utf-8") if body is not None else None,
        capture_output=True,
    )
    raw = proc.stdout.decode("utf-8", "replace")
    if "__STATUS__" in raw:
        payload, _, status = raw.rpartition("__STATUS__")
        return int(status.strip()), payload.strip()
    return 0, raw.strip() or proc.stderr.decode("utf-8", "replace").strip()


def pretty(text: str, max_items: int | None = None) -> str:
    """尽量把 JSON 格式化；列表可按需截断，避免整份记录过长。"""
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return text
    if max_items is not None and isinstance(data, list):
        data = data[:max_items]
    return json.dumps(data, ensure_ascii=False, indent=2)


def main() -> int:
    lines: list[str] = []
    lines.append("个人健康饮食记录管理系统 —— 接口响应采集记录")
    lines.append(f"采集时间：{datetime.datetime.now():%Y-%m-%d %H:%M:%S}")
    lines.append(f"目标地址：{BASE}")
    lines.append("采集方式：curl.exe 真实发起 HTTP 请求；响应体为原始返回内容")
    lines.append("=" * 78)

    failures = 0
    for title, method, path, body in CALLS:
        status, response = curl(method, path, body)
        if status == 0:
            print(f"[FAIL] {title} —— 请求失败，应用是否已启动？", file=sys.stderr)
            failures += 1
        lines.append("")
        lines.append(f"### {title}")
        lines.append(f"$ curl -X {method} {BASE}{path}")
        if body:
            lines.append(f"# 请求体：{body}")
        lines.append(f"HTTP {status}")
        shown = pretty(response, max_items=3) if path == "/api/foods" else pretty(response)
        lines.append(shown)
        print(f"[{'OK' if status else 'FAIL'}] {title} -> HTTP {status}")

    lines.append("")
    lines.append("=" * 78)
    lines.append(f"共 {len(CALLS)} 个请求，失败 {failures} 个。")

    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\n已写入：{OUTPUT}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
