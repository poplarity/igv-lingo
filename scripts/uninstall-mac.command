#!/bin/bash
# 双击我：卸载 IGV 中文汉化（macOS）
cd "$(dirname "$0")" || exit 1
clear
bash ./uninstall.sh
echo
echo "按任意键关闭窗口…"
read -n 1 -s
