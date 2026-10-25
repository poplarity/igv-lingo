#!/usr/bin/env bash
# 独立安装脚本（随发布 zip 提供，不需要整个仓库）
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="${IGV_L10N_HOME:-$HOME/.igv/l10n}"
ARGS="${IGV_JAVA_ARGS:-$HOME/.igv/java_arguments}"

mkdir -p "$DEST/locales"
cp "$HERE/igv-l10n-agent.jar" "$DEST/"
cp "$HERE/deny_by_class.tsv" "$DEST/"
for d in "$HERE"/locales/*/; do
    name="$(basename "$d")"
    mkdir -p "$DEST/locales/$name"
    cp "$d/strings.tsv" "$DEST/locales/$name/"
done

mkdir -p "$(dirname "$ARGS")"
[[ -f "$ARGS" ]] && cp "$ARGS" "$ARGS.bak.$(date +%Y%m%d-%H%M%S)"
touch "$ARGS"
grep -v "igv-l10n-agent.jar" "$ARGS" > "$ARGS.tmp" 2>/dev/null || true
mv "$ARGS.tmp" "$ARGS"
{
    echo ""
    echo "# IGV 多语言（igv-l10n）：删掉这一行即可关闭"
    echo "-javaagent:${DEST}/igv-l10n-agent.jar=${DEST}"
} >> "$ARGS"

echo "已安装到 $DEST"
echo "已写入 $ARGS"
echo "重启 IGV 生效；日志：$DEST/igv-l10n.log"
