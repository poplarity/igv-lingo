#!/bin/bash
# 双击我：安装 IGV 中文汉化（macOS）
cd "$(dirname "$0")" || exit 1
clear
echo "正在安装 IGV 中文汉化…"
echo
bash ./install.sh
status=$?
echo
echo "----------------------------------------"
if [[ $status -eq 0 ]]; then
    echo "✅ 安装完成。可以关闭这个窗口了。"
else
    echo "❌ 安装没有完成，请把上面的内容截图发给作者。"
fi
echo "按任意键关闭窗口…"
read -n 1 -s
exit $status
