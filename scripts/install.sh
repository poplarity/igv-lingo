#!/usr/bin/env bash
# IGV 中文汉化 · 安装脚本（命令行版）
#
# 这个脚本会做三件事：
#   1. 把汉化文件复制到 ~/.igv/lingo
#   2. 在 ~/.igv/java_arguments 里加一行，让 IGV 启动时加载汉化
#   3. 自检一次，确认汉化能正常加载
#
# 不想用命令行？macOS 双击 install-mac.command，Windows 双击 install-windows.bat。

set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="${IGV_LINGO_HOME:-$HOME/.igv/lingo}"
ARGS="${IGV_JAVA_ARGS:-$HOME/.igv/java_arguments}"
TITLE="IGV 中文汉化"

blue() { printf "\033[34m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
red() { printf "\033[31m%s\033[0m\n" "$*"; }

die() {
    red "❌ $*"
    if [[ "${IGV_LINGO_NO_DIALOG:-0}" != "1" ]] && command -v osascript >/dev/null 2>&1; then
        osascript -e "display dialog \"$*\" buttons {\"好\"} default button 1 with title \"$TITLE\"" >/dev/null 2>&1
    fi
    exit 1
}

say() {
    printf "%s\n" "$*"
}

blue "======================================"
blue "  IGV 中文汉化 · 安装"
blue "======================================"
echo

# ---------- 检查安装包完整性 ----------
[[ -f "$HERE/igv-lingo-agent.jar" ]] || die "安装包不完整：缺少 igv-lingo-agent.jar"
[[ -f "$HERE/deny_by_class.tsv" ]]    || die "安装包不完整：缺少 deny_by_class.tsv"
LANG_DIR=""
for d in "$HERE"/locales/*/; do
    [[ -f "$d/strings.tsv" ]] && LANG_DIR="$d" && break
done
[[ -n "$LANG_DIR" ]] || die "安装包不完整：缺少 locales/ 词表"
say "找到语言包：$(basename "$LANG_DIR")"
say "安装到：    $DEST"
echo

# ---------- 找 IGV ----------
IGV_APP=""
for p in /Applications/IGV_*.app "$HOME"/Applications/IGV_*.app; do
    [[ -d "$p" ]] && IGV_APP="$p"
done
if [[ -n "$IGV_APP" ]]; then
    say "找到 IGV：   $IGV_APP"
else
    say "⚠️  没在「应用程序」里找到 IGV。"
    say "    汉化仍会装好，等你把 IGV 放进「应用程序」后重启它即可。"
fi
echo

# ---------- 复制文件 ----------
mkdir -p "$DEST/locales" || die "无法创建目录 $DEST"
cp "$HERE/igv-lingo-agent.jar" "$DEST/" || die "复制 agent 失败"
cp "$HERE/deny_by_class.tsv"   "$DEST/" || die "复制禁译规则失败"
for d in "$HERE"/locales/*/; do
    name="$(basename "$d")"
    mkdir -p "$DEST/locales/$name"
    cp "$d/strings.tsv" "$DEST/locales/$name/" || die "复制词表失败"
done
green "✅ 汉化文件已复制"

# ---------- 写入加载行 ----------
mkdir -p "$(dirname "$ARGS")"
if [[ -f "$ARGS" ]]; then
    cp "$ARGS" "$ARGS.bak.$(date +%Y%m%d-%H%M%S)"
fi
touch "$ARGS"
# 清掉旧版本（含 0.1.x 的 igv-l10n）留下的加载行
grep -v -E "igv-(lingo|l10n)-agent\.jar|^# IGV 多语言（igv-(lingo|l10n)）" "$ARGS" > "$ARGS.tmp" 2>/dev/null || true
mv "$ARGS.tmp" "$ARGS"
{
    echo ""
    echo "# IGV 多语言（igv-lingo）：删掉这一行即可关闭"
    echo "\"-javaagent:${DEST}/igv-lingo-agent.jar=${DEST}\""
} >> "$ARGS"
green "✅ 已让 IGV 启动时加载汉化"

# ---------- 自检 ----------
JAVA=""
for c in "${JAVA:-}" /Applications/IGV_*.app/Contents/jdk-*/bin/java "$(command -v java || true)"; do
    [[ -n "$c" && -x "$c" ]] || continue
    "$c" -version >/dev/null 2>&1 && { JAVA="$c"; break; }
done

if [[ -n "$JAVA" ]]; then
    if "$JAVA" "-javaagent:${DEST}/igv-lingo-agent.jar=${DEST}" -version >/dev/null 2>&1; then
        green "✅ 自检通过：汉化可以正常加载"
    else
        red "⚠️  自检没通过，但文件已经装好。"
        red "    请把 ~/.igv/lingo/igv-lingo.log 发给作者。"
    fi
else
    say "（没找到 java，跳过自检；IGV 自带 java，不影响使用）"
fi

echo
green "======================================"
green "  安装完成 🎉"
green "======================================"
echo
say "最后一步：完全退出 IGV（Command+Q / 右键退出），再重新打开。"
say "中文界面就出现了。"
say ""
say "想恢复英文：双击同目录下的 uninstall-mac.command（Windows 用 uninstall-windows.bat），"
say "或者删掉 ~/.igv/java_arguments 里那行 -javaagent。"
echo
say "日志：$DEST/igv-lingo.log"

if [[ "${IGV_LINGO_NO_DIALOG:-0}" != "1" ]] && command -v osascript >/dev/null 2>&1; then
    osascript -e "display dialog \"安装完成 ✅

现在请完全退出 IGV（Command+Q），再重新打开。

菜单栏应该会变成：文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助\" buttons {\"好\"} default button 1 with title \"$TITLE\"" >/dev/null 2>&1 || true
fi

exit 0
