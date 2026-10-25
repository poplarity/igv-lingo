# Changelog

## 0.1.0 — 2026-10-26

首个可用版本。

- agent：类加载期常量池改写，零依赖，支持 `locales/<lang>` 多语言与系统语言回退
- 工具链：`audit`（字节码安全审计）、`extract`（候选导出/词表骨架）、
  `check`（占位符/重复/失效词条）、`coverage`（覆盖率）、`dryrun`（离线演练 + 结构校验）
- 简体中文词表：360 条，覆盖 110 个类、456 处界面字符串
- IGV 2.19.1 禁译规则：1031 条（自动生成）
- `bin/igv-l10n`：setup / install / uninstall / status / new / package / test
- 发布包：独立 `install.sh`，解压即用
- CI：构建 + 词表校验 + 假 igv.jar 自检
