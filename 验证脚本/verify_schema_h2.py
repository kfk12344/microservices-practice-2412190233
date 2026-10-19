# -*- coding: utf-8 -*-
"""
在没有 MySQL 的机器上，用 H2 的 SQL 工具验证数据库设计并生成截图用的输出。

为什么需要这个脚本：
    第 04 周"数据库持久化"小题要求提供能证明表结构、约束与数据落库的截图。
    如果本机装不了 MySQL（例如无法访问 Docker 镜像仓库），就用 H2 完成同样的验证——
    关键在于：建表用的仍然是仓库里那份 schema.sql、灌数据用的仍然是 data.sql，
    所以表结构、约束与示例数据与 MySQL 环境完全一致，只是执行引擎换成了 H2。

它会依次做四件事：
    1. 建一个空的 H2 数据库文件；
    2. 执行 schema.sql 建表；
    3. 执行 data.sql 灌入示例数据；
    4. 查询并打印表清单、meal_item 的字段与外键约束、各表行数。

再执行一次 2、3 两步即可验证脚本的幂等性（行数不应该增加）。

用法（在仓库根目录执行）：
    python 验证脚本/verify_schema_h2.py
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MONOLITH = ROOT / "monolith"
RESOURCES = MONOLITH / "src" / "main" / "resources"
SCHEMA = RESOURCES / "schema.sql"
DATA = RESOURCES / "data.sql"
SCRATCH = ROOT / ".scratch"

# H2 数据库文件路径（不要放在 target 下，避免被 mvn clean 清掉）
DB_BASE = SCRATCH / "h2verify" / "health_diet"
JDBC_URL = f"jdbc:h2:{DB_BASE.as_posix()};MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"


def find_h2_jar() -> Path:
    """在本地 Maven 仓库里找 h2 的 jar。"""
    candidates = []
    for base in (
        Path.home() / ".m2" / "repository",
        Path(r"C:\Users\31238\Documents\deepseek-harness\default-workspace\_m2repo"),
    ):
        candidates.extend(base.glob("com/h2database/h2/*/h2-*.jar"))
    if not candidates:
        raise SystemExit(
            "找不到 h2 jar。请先在 monolith/ 下执行过一次 ./mvnw test，让 Maven 下载依赖。"
        )
    return sorted(candidates)[-1]


def find_java() -> str:
    """优先用 JAVA_HOME 下的 java，否则用 PATH 里的 java。"""
    import os

    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        exe = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if exe.exists():
            return str(exe)
    found = shutil.which("java")
    if not found:
        raise SystemExit("找不到 java。请确认已安装 JDK 25 并配置好 JAVA_HOME。")
    return found


def run_tool(java: str, jar: Path, main_class: str, *args: str) -> tuple[int, str]:
    """
    调用 h2 jar 里的命令行工具，返回 (退出码, 输出)。

    关于编码：Windows 控制台默认是 GBK(CP936)，而 Java 18+ 的 System.out
    默认按 UTF-8 输出，两者不一致会让中文变成乱码。因此显式把 JVM 的输出
    编码设成控制台实际使用的编码（GBK），中文才能正常显示。
    若在 UTF-8 终端（如 Windows Terminal + chcp 65001）下运行，
    把下面两个属性改成 UTF-8 即可。
    """
    console_encoding = "GBK" if sys.platform == "win32" else "UTF-8"
    proc = subprocess.run(
        [
            java,
            f"-Dfile.encoding={console_encoding}",
            f"-Dstdout.encoding={console_encoding}",
            f"-Dstderr.encoding={console_encoding}",
            "-cp", str(jar), main_class, *args,
        ],
        capture_output=True,
        text=True,
        encoding=console_encoding,
        errors="replace",
        timeout=180,
    )
    return proc.returncode, (proc.stdout or "") + (proc.stderr or "")


def run_script(java: str, jar: Path, sql_file: Path, label: str) -> None:
    """
    执行一个 SQL 文件。

    实现方式说明：不用 org.h2.tools.RunScript 的 -options 参数
    （H2 2.4.240 的 RunScript 不认该参数，会报 “Syntax error ... charset=UTF-8”），
    而是走 Shell 执行 SQL 层的 `RUNSCRIPT FROM ... CHARSET 'UTF-8'` 语句。
    这样能显式指定文件编码，避免按 Windows 默认编码（GBK）读取 UTF-8 的 SQL
    文件而读出乱码——我们所有脚本都含中文注释与中文示例数据。
    """
    sql = f"RUNSCRIPT FROM '{sql_file.as_posix()}' CHARSET 'UTF-8';"
    code, out = run_tool(
        java, jar, "org.h2.tools.Shell",
        "-url", JDBC_URL, "-user", "sa", "-password", "", "-sql", sql,
    )
    errors = [ln for ln in out.splitlines()
              if "Exception" in ln or "Syntax error" in ln or "error" in ln.lower()]
    status = "OK" if code == 0 and not errors else "FAIL"
    print(f"  [{status}] 执行 {label}（{sql_file.name}）")
    if status == "FAIL":
        print(out)
        raise SystemExit(f"执行 {sql_file.name} 失败")


def query(java: str, jar: Path, sql: str) -> str:
    """用 Shell 执行一段查询，返回原始输出。"""
    code, out = run_tool(
        java, jar, "org.h2.tools.Shell",
        "-url", JDBC_URL, "-user", "sa", "-password", "",
        "-sql", sql,
    )
    if code != 0:
        raise SystemExit(f"查询失败：\n{out}")
    return out.rstrip()


def main() -> int:
    print("=" * 78)
    print("数据库设计验证（使用 H2，建表脚本与生产环境共用 schema.sql）")
    print("=" * 78)

    for f in (SCHEMA, DATA):
        if not f.exists():
            raise SystemExit(f"缺少文件：{f}")

    java = find_java()
    jar = find_h2_jar()
    print(f"java : {java}")
    print(f"h2   : {jar.name}")
    print(f"库   : {DB_BASE.as_posix()}")
    print()

    # 每次从干净的库开始，保证结果可复现
    if SCRATCH.exists():
        db_dir = SCRATCH / "h2verify"
        if db_dir.exists():
            shutil.rmtree(db_dir)
    (SCRATCH / "h2verify").mkdir(parents=True, exist_ok=True)

    print("【1】schema.sql 建表 + data.sql 灌数据")
    run_script(java, jar, SCHEMA, "建表脚本")
    run_script(java, jar, DATA, "示例数据")

    print()
    print("【2】表清单")
    print(query(java, jar, "SHOW TABLES;"))

    print()
    print("【3】meal_item 表结构（字段名与类型）")
    # 注意：H2 在该连接串下以 DATABASE_TO_LOWER=TRUE 建库，表名/列名一律小写，
    # 所以这里用 'meal_item' 而不是 'MEAL_ITEM'，否则查不到任何行。
    print(query(java, jar,
                "SELECT column_name, data_type, is_nullable "
                "FROM INFORMATION_SCHEMA.COLUMNS "
                "WHERE table_name = 'meal_item' ORDER BY ordinal_position;"))

    print()
    print("【4】约束清单（主键 / 外键 / 唯一 / 检查）")
    # 这里不过滤 constraint_schema：H2 的约束视图不需要该过滤条件，
    # 加上反而会把全部约束都过滤掉（实测返回 0 行）。
    print(query(java, jar,
                "SELECT constraint_name, constraint_type, table_name "
                "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                "ORDER BY table_name, constraint_type;"))

    print()
    print("【5】各表行数")
    print(query(java, jar,
                "SELECT 'food' AS 表, COUNT(*) AS 行数 FROM food "
                "UNION ALL SELECT 'meal_record', COUNT(*) FROM meal_record "
                "UNION ALL SELECT 'meal_item', COUNT(*) FROM meal_item;"))

    print()
    print("【6】幂等性验证：再执行一次建表与灌数据")
    run_script(java, jar, SCHEMA, "建表脚本（第二次）")
    run_script(java, jar, DATA, "示例数据（第二次）")
    print()
    print("  第二次执行后的行数（应与【5】完全一致）：")
    print(query(java, jar,
                "SELECT 'food' AS 表, COUNT(*) AS 行数 FROM food "
                "UNION ALL SELECT 'meal_record', COUNT(*) FROM meal_record "
                "UNION ALL SELECT 'meal_item', COUNT(*) FROM meal_item;"))

    print()
    print("【7】示例数据抽样（证明 data.sql 真的落库）")
    print(query(java, jar,
                "SELECT f.name AS 食物, i.portion_grams AS 克数, r.meal_type AS 餐次 "
                "FROM meal_item i JOIN food f ON f.id = i.food_id "
                "JOIN meal_record r ON r.id = i.meal_record_id "
                "WHERE r.eater = 'demo-user' ORDER BY r.eaten_at, f.id;"))

    print()
    print("=" * 78)
    print("验证完成。以上输出即为『数据库持久化』小题可用的截图内容。")
    return 0


if __name__ == "__main__":
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[union-attr]
        except (AttributeError, ValueError):
            pass
    raise SystemExit(main())
