# -*- coding: utf-8 -*-
"""提取仓库中所有 Markdown 里的 mermaid 代码块，落盘到 .scratch/mermaid/ 以便逐个校验。

用法：
    python 验证脚本/extract_mermaid.py

随后可用 mermaid CLI 逐个渲染检查语法：
    npx -y @mermaid-js/mermaid-cli -i <file>.mmd -o <file>.png
本脚本仅做提取，不做渲染，避免引入额外依赖。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / ".scratch" / "mermaid"

# 记录每张图的类型，便于对照 GitHub 支持的图表类型
SUPPORTED = {"flowchart", "graph", "sequencediagram", "erdiagram", "classdiagram",
             "statediagram", "journey", "gantt", "pie", "gitgraph", "quadrantchart",
             "requirementdiagram", "c4context", "timeline"}
# 已知 GitHub 不渲染的类型（出现即提示替换）
UNSUPPORTED = {"mindmap", "block-beta", "packet-beta", "architecture-beta", "sankey-beta", "xychart-beta"}


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    pattern = re.compile(r"```mermaid\s*\n(.*?)```", re.DOTALL)

    total = 0
    problems: list[str] = []
    for md in sorted(ROOT.rglob("*.md")):
        if ".scratch" in md.parts or "target" in md.parts:
            continue
        text = md.read_text(encoding="utf-8")
        for index, block in enumerate(pattern.findall(text), start=1):
            total += 1
            first_line = next((ln.strip() for ln in block.splitlines() if ln.strip()), "")
            kind = first_line.split()[0].lower() if first_line else "(空)"
            rel = md.relative_to(ROOT)
            name = f"{rel.as_posix().replace('/', '_')}__{index}.mmd"
            (OUT / name).write_text(block.rstrip() + "\n", encoding="utf-8")

            label = f"{rel}#{index} [{kind}]"
            if kind in UNSUPPORTED:
                problems.append(f"GitHub 不支持该类型：{label}")
                print(f"  [警告] {label} —— GitHub 不渲染此类型")
            elif kind in SUPPORTED:
                print(f"  [OK]   {label}")
            else:
                problems.append(f"未知图表类型：{label}")
                print(f"  [未知] {label}")

    print()
    print(f"共提取 {total} 张 mermaid 图，输出目录：{OUT}")
    if problems:
        print("需要处理：")
        for problem in problems:
            print("  - " + problem)
        return 1
    print("全部图表类型均为 GitHub 支持的语法。")
    return 0


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[union-attr]
        except (AttributeError, ValueError):
            pass
    raise SystemExit(main())
