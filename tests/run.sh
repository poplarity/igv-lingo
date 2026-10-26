#!/usr/bin/env bash
# 自检：不需要真的 IGV，用一个假 igv.jar 走完 audit -> dryrun -> 断言
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

pick_java() {
    local c
    for c in "${JAVA:-}" /Applications/IGV_*.app/Contents/jdk-21/bin/java "$(command -v java || true)"; do
        [[ -n "$c" && -x "$c" ]] || continue
        if "$c" -version >/dev/null 2>&1; then echo "$c"; return 0; fi
    done
    return 1
}
JAVA="$(pick_java)" || { echo "找不到可用的 java"; exit 1; }

rm -rf build/fixture
mkdir -p build/fixture/classes

SUCCESS=0
if command -v javac >/dev/null 2>&1 && javac -version >/dev/null 2>&1; then
    javac -encoding UTF-8 --release 17 -d build/fixture/classes tests/fixture/src/org/broad/igv/ui/Demo.java
    SUCCESS=1
elif [[ -f vendor/ecj-3.38.0.jar ]]; then
    "$JAVA" -jar vendor/ecj-3.38.0.jar -source 17 -target 17 -nowarn \
        -d build/fixture/classes tests/fixture/src/org/broad/igv/ui/Demo.java
    SUCCESS=1
fi
[[ $SUCCESS -eq 1 ]] || { echo "没有编译器，跳过自检"; exit 0; }

( cd build/fixture/classes && zip -q -X -r ../igv-fixture.jar org )

echo "== audit"
bin/igv-lingo audit --jar build/fixture/igv-fixture.jar --out build/fixture/audit

echo "== dryrun"
bin/igv-lingo dryrun --jar build/fixture/igv-fixture.jar --locale locales/zh_CN \
    --deny build/fixture/audit/deny_by_class.tsv > build/fixture/dryrun.log 2>&1 || true
cat build/fixture/dryrun.log

fail=0
grep -q "Demo	Regions" build/fixture/audit/deny_by_class.tsv \
    || { echo "FAIL: audit 没有把 Regions 判为结构用途"; fail=1; }
grep -q "结构校验通过" build/fixture/dryrun.log \
    || { echo "FAIL: dryrun 结构校验未通过"; fail=1; }

python3 - <<'PY' || fail=1
import sys
p='build/dryrun/classes/org/broad/igv/ui/Demo.class'
b=open(p,'rb').read()
checks={
 "File 被翻译": "文件".encode() in b,
 "Regions 未翻译": "区域".encode() not in b,
 "Regions 原文保留": b"Regions" in b,
}
for k,v in checks.items(): print(("  OK   " if v else "  FAIL ")+k)
sys.exit(0 if all(checks.values()) else 1)
PY

[[ $fail -eq 0 ]] && echo "自检通过" || { echo "自检失败"; exit 1; }
