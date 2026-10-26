# Changelog

## 0.1.1 — 2026-10-26

- 兼容性：agent 编译目标降到 **Java 11**（class v55），支持 IGV 2.16 及更早自带 JRE 11 的版本
- 兼容性：实测 IGV 2.10.3 / 2.12.3 / 2.16.2 / 2.17.4 / 2.18.4 / 2.19.1 / 2.19.8
- 修复：路径含空格时 `-javaagent` 参数未加引号导致 JVM 拒绝加载
- 修复：`dryrun` 未清理上次产物，可能残留未禁译的旧 class
- 修复：`dryrun --out` 参数解析错误
- CI：新增 Java 11 下加载 agent 的验证步骤
- 文档：新增 docs/compatibility.md（版本 / 系统 / Java 三维兼容性）
- 仓库内附 IGV 2.19.1 / 2.19.8 / 2.18.4 的禁译规则，装这些版本的用户不必自己跑 audit

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
