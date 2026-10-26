#!/usr/bin/env bash
# 构建 agent jar 与工具类。优先用 JDK 的 javac；没有 JDK 就退回 vendor/ 里的 ecj。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(cat VERSION 2>/dev/null || echo dev)"
JAVA="${JAVA:-}"
ASM_JARS=("vendor/asm-9.7.1.jar" "vendor/asm-tree-9.7.1.jar" "vendor/asm-analysis-9.7.1.jar")
ASM_CP="$(IFS=:; echo "${ASM_JARS[*]}")"

if [[ -z "$JAVA" ]]; then
    if [[ -x /Applications/IGV_2.19.1.app/Contents/jdk-21/bin/java ]]; then
        JAVA=/Applications/IGV_2.19.1.app/Contents/jdk-21/bin/java
    else
        JAVA=java
    fi
fi

if command -v javac >/dev/null 2>&1 && javac -version >/dev/null 2>&1; then
    COMPILER=javac
    echo "编译器: javac ($(javac -version 2>&1))"
else
    ECJ="vendor/ecj-3.38.0.jar"
    if [[ ! -f "$ECJ" ]]; then
        echo "没有 javac，下载 ecj 编译器…"
        mkdir -p vendor
        curl -fsSL --retry 3 -o "$ECJ" https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar
    fi
    COMPILER="ecj"
    echo "编译器: ecj (无 JDK 模式)"
fi

for j in "${ASM_JARS[@]}"; do
    [[ -f "$j" ]] || { echo "缺少依赖 ${j}，先运行 bin/igv-l10n deps" >&2; exit 1; }
done

rm -rf build/agent build/tools build/agent.jar build/tools.jar
mkdir -p build/agent/classes build/tools/classes build/stage-agent/META-INF

# $1=outdir $2=classpath $3=release $4...=sources
# agent 用 Java 11 目标：IGV 2.16 及更早版本自带的是 JRE 11，
# 高版本 class（v61+）会在那些 JVM 上直接 UnsupportedClassVersionError。
compile() {
    local out="$1" cp="$2" rel="$3"; shift 3
    if [[ "$COMPILER" == javac ]]; then
        javac -encoding UTF-8 --release "$rel" -nowarn -cp "$cp" -d "$out" "$@"
    else
        "$JAVA" -jar vendor/ecj-3.38.0.jar -source "$rel" -target "$rel" -nowarn -proceedOnError \
            -encoding UTF-8 -cp "$cp" -d "$out" "$@"
    fi
}

echo "[1/3] 编译 agent"
compile build/agent/classes "" 11 $(find src/agent/java -name '*.java')

echo "[2/3] 打包 agent"
cp src/agent/MANIFEST.MF build/stage-agent/META-INF/MANIFEST.MF
cp -R build/agent/classes/org build/stage-agent/
sed -i.bak "s/^Implementation-Version:.*/Implementation-Version: $VERSION/" \
    build/stage-agent/META-INF/MANIFEST.MF && rm -f build/stage-agent/META-INF/MANIFEST.MF.bak
if command -v jar >/dev/null 2>&1 && jar --version >/dev/null 2>&1; then
    ( cd build/stage-agent && jar cfm "$ROOT/build/agent.jar" META-INF/MANIFEST.MF org )
else
    ( cd build/stage-agent && zip -q -X -r "$ROOT/build/agent.jar" META-INF org )
fi

echo "[3/3] 编译工具"
compile build/tools/classes "build/agent/classes:$ASM_CP" 17 \
    $(find src/tools/java -name '*.java')

echo "完成:"
ls -lh build/agent.jar | awk '{print "  " $9, $5}'
echo "  build/tools/classes"
