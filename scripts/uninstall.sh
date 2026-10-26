#!/usr/bin/env bash
# 卸载 IGV 中文汉化：只删加载行，词表保留
set -uo pipefail
ARGS="${IGV_JAVA_ARGS:-$HOME/.igv/java_arguments}"
TITLE="IGV 中文汉化"
echo "正在关闭汉化…"
if [[ -f "$ARGS" ]]; then
    cp "$ARGS" "$ARGS.bak.$(date +%Y%m%d-%H%M%S)"
    grep -v -E "igv-(lingo|l10n)-agent\.jar|^# IGV 多语言（igv-(lingo|l10n)）" "$ARGS" > "$ARGS.tmp" 2>/dev/null || true
    mv "$ARGS.tmp" "$ARGS"
    echo "✅ 已移除加载行：$ARGS"
else
    echo "没有找到 $ARGS，说明本来就没装。"
fi
echo
echo "重启 IGV 后就会恢复英文。"
echo "彻底删除文件：rm -rf \"${HOME}/.igv/lingo\""
if [[ "${IGV_LINGO_NO_DIALOG:-0}" != "1" ]] && command -v osascript >/dev/null 2>&1; then
    osascript -e "display dialog \"汉化已关闭。

请重启 IGV，界面会恢复英文。\" buttons {\"好\"} default button 1 with title \"$TITLE\"" >/dev/null 2>&1 || true
fi
