# Changelog

## 0.3.0 — 2026-10-26

**给不写代码的人做的版本。**

- 安装：发布包内新增双击安装——macOS `install-mac.command`、Windows `install-windows.bat`，
  以及对应的卸载脚本；脚本会自检能否加载，失败时弹窗提示
- 安装：`install.sh` 重写为「检查 → 复制 → 写加载行 → 自检 → 弹窗」的完整流程，
  无论成功失败都给出下一步指引
- 文档：新增 [安装指南（零基础版）](docs/安装指南.md) 与英文 [installation guide](docs/installation.md)
- 文档：README 重写，中文为默认语言，顶部加 `简体中文 · English` 切换
- 文档：补充徽章（CI / 版本 / 许可证 / IGV / Java）、常见问题、项目结构
- 修复：`package` 未包含安装脚本；安装脚本里的卸载提示写错文件名


## 0.2.0 — 2026-10-26

**改名**：`igv-l10n` → **`igv-lingo`**。

`l10n` 是 localization 的“数字缩写”（l + 10 个字母 + n），跟 `i18n` 一样是开发者黑话，
对做基因组学的用户几乎没有识别度。新名字取 "lingo"（语言/行话），好读好记，
也和“给 IGV 换一种语言”这件事直接相关。

- 仓库地址：`github.com/poplarity/igv-lingo`（旧地址自动跳转）
- 命令：`bin/igv-lingo`
- 安装目录：`~/.igv/lingo`
- agent jar：`igv-lingo-agent.jar`
- Java 包：`org.igvlingo`
- 安装脚本会自动清掉 0.1.x 的旧加载行，无需手工处理

词表格式、禁译规则、工具行为完全不变。

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
- `bin/igv-lingo`：setup / install / uninstall / status / new / package / test
- 发布包：独立 `install.sh`，解压即用
- CI：构建 + 词表校验 + 假 igv.jar 自检
