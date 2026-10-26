# IGV Lingo

**简体中文** · [English](README.en.md)

[![ci](https://github.com/poplarity/igv-lingo/actions/workflows/ci.yml/badge.svg)](https://github.com/poplarity/igv-lingo/actions/workflows/ci.yml)
[![release](https://img.shields.io/github/v/release/poplarity/igv-lingo?color=blue)](https://github.com/poplarity/igv-lingo/releases/latest)
[![license](https://img.shields.io/github/license/poplarity/igv-lingo)](LICENSE)
![IGV](https://img.shields.io/badge/IGV-2.10%E2%80%932.19-2ea44f)
![Java](https://img.shields.io/badge/Java-11%2B-orange)

给 [IGV Desktop](https://igv.org)（Integrative Genomics Viewer）换上中文界面的开源工具。
一个零依赖的 java agent + 一份 TSV 词表：**不改 IGV 本体、不破坏签名、随时可以关掉**。

![IGV 中文菜单栏](docs/screenshot-menubar.png)

---

## 30 秒安装（不需要懂代码）

1. 打开 [Releases](https://github.com/poplarity/igv-lingo/releases/latest)，下载 `igv-lingo-zh_CN-<你的IGV版本>.zip`
   （不确定版本就下载 2.19 的，通常也能用）
2. 解压，**双击** `install-mac.command`（Mac）或 `install-windows.bat`（Windows）
3. 看到「安装完成」后，**完全退出 IGV**（Mac 按 `Command + Q`），再重新打开

菜单栏变成 `文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助` 就成功了。

> 第一次双击如果提示"无法打开"：Mac 上**右键 → 打开**，Windows 上点"更多信息 → 仍要运行"。
> 遇到问题看 [安装指南（图文步骤）](docs/安装指南.md) 或 [常见问题](#常见问题)。

<details>
<summary>我更习惯命令行</summary>

```bash
git clone https://github.com/poplarity/igv-lingo.git
cd igv-lingo
bin/igv-lingo setup zh_CN
```

卸载：`bin/igv-lingo uninstall`

</details>

---

## 效果

| 原文 | 汉化后 |
|---|---|
| File / Genomes / View / Tracks / Regions / Tools / Help | 文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助 |
| Load Genome from File... | 从文件载入基因组... |
| Fit Data to Window | 数据适应窗口 |
| Group alignments by | 按…分组比对 |
| Sort By Depth | 按深度排序 |
| Save PNG Screenshot ... | 保存 PNG 截图... |

当前 zh_CN：**788 条词条**，覆盖 **198 个类 / 959 处**界面字符串。
菜单栏 90%、比对右键 98%、命令栏 96%、轨道菜单 94%、图例 96%、动作 87%、通用控件 86%、数据面板 81%。
按"可见界面文字"算约 **81%**；剔除命令行帮助、偏好键、SVG 导出等本就不该翻的部分，约 **89%**。

## 它是怎么做到的

IGV 没有 i18n 框架（整个 jar 只有 2 个 `.properties`，源码里 `ResourceBundle` 出现 0 次），
界面文字全是编译进 class 的字符串常量。所以汉化只能"改程序"。

IGV Lingo 在**类加载时**改写 class 常量池：只动字符串字面量，译文作为新常量追加，
原有常量原样保留——IGV 安装目录、签名、更新机制完全不受影响。

```
igv.jar ──► ClassFileTransformer ──► 常量池里 "File" → "文件" ──► 中文界面
             (java agent)             (原 "File" 保留)
```

最麻烦的不是改字节，而是**同一段英文有两个身份**：`"Regions"` 既是菜单名（该翻成"区域"），
又是 session XML 的标签名（翻了旧会话就打不开）。工具用 ASM 做数据流分析，
把字面量分成「展示 / 比较 / 结构」三类，对结构类按类禁译。

细节：[原理](docs/how-it-works.md) · [安全规则](docs/safety-rules.md)

## 兼容性

| 维度 | 支持范围 |
|---|---|
| IGV | **2.10.3 – 2.19.8 实测可用**；内置 2.19.1 / 2.19.8 / 2.18.4 规则 |
| 系统 | macOS / Linux / Windows |
| Java | 11 及以上（IGV 自带即可） |

同一份词表在不同版本上的实测：

| IGV | 替换处 | 结构校验 | 该版本失效词条 |
|---|---|---|---|
| 2.19.8 | 957 | 通过 | 5 |
| 2.18.4 | 944 | 通过 | 17 |
| 2.16.2 | 899 | 通过 | 70 |
| 2.12.3 | 883 | 通过 | 86 |
| 2.10.3 | 857 | 通过 | 108 |

升级 IGV 后重跑 `bin/igv-lingo audit` + `check` 即可。详见 [兼容性说明](docs/compatibility.md)。

## 命令行

给开发者和译者用，普通用户不需要。

| 命令 | 作用 |
|---|---|
| `bin/igv-lingo setup [locale]` | 构建 + 审计 + 演练 + 安装 |
| `bin/igv-lingo install / uninstall` | 只安装 / 只移除加载行 |
| `bin/igv-lingo status` | 安装状态与最近日志 |
| `bin/igv-lingo new <locale>` | 为新语言生成词表骨架 |
| `bin/igv-lingo check` | 校验词表（CI 同款） |
| `bin/igv-lingo coverage <locale>` | 翻译覆盖率 |
| `bin/igv-lingo dryrun <locale>` | 离线演练，不启动 IGV |
| `bin/igv-lingo audit` | 为当前 IGV 版本生成禁译规则 |
| `bin/igv-lingo package <locale>` | 打发布 zip |
| `bin/igv-lingo test` | 构建 + 校验 + 演练 |

## 添加一种语言

```bash
bin/igv-lingo new ja_JP        # 从本机 igv.jar 生成约 2400 行骨架，按区域分组
# 编辑 locales/ja_JP/strings.tsv
bin/igv-lingo check            # 占位符 / 重复键 / 失效词条
bin/igv-lingo setup ja_JP      # 装到本机试用
```

词表就是 TSV，Excel / Numbers 都能编辑：

```tsv
File	ファイル
Load Genome from File...	ファイルからゲノムを読み込む...
```

只改第二列，第一列一个字符都不要动。详见 [新增语言](docs/adding-a-language.md)。

## 项目结构

```
igv-lingo/
├── bin/igv-lingo            命令行入口
├── src/agent/               运行时 agent（零依赖，Java 11）
├── src/tools/               audit / extract / check / coverage / dryrun
├── locales/zh_CN/           词表、术语表
├── igv/<version>/           每个 IGV 版本一份禁译规则
├── scripts/                 双击安装脚本（Mac / Windows）
└── docs/                    原理、安装、安全规则、兼容性
```

## 参与

翻译不需要懂 Java：改 TSV、跑 `check`、提 PR 即可。

- [贡献指南](CONTRIBUTING.md)
- 想认领的活：[补全面板与通用控件](https://github.com/poplarity/igv-lingo/issues/1)、[日本語](https://github.com/poplarity/igv-lingo/issues/2)
- 术语表：`locales/zh_CN/glossary.tsv`

## 常见问题

**会不会把我的 IGV 改坏？**
不会。IGV 本体一个文件都没动，只在 `~/.igv/lingo` 放词表，并在 `~/.igv/java_arguments` 加一行（修改前自动备份）。
删掉 `~/.igv/lingo` 和那一行就完全恢复。

**为什么有的地方还是英文？**
两种可能：词表还没翻到（看 `coverage`），或那条字符串是文件格式的一部分（看 `igv/<version>/deny_by_class.tsv`），翻了会出问题。

**IGV 升级后还能用吗？**
能。重跑 `bin/igv-lingo audit` 生成新版本规则，再 `check` 看哪些词条失效。

**为什么不做成 IGV 插件？**
IGV 的插件接口不提供界面文字替换能力；而改源码再分发要跟着 IGV 版本反复合并。agent 方案把"翻译"和"IGV 代码"解耦了。

**支持 IGV 2.9 吗？**
不支持。2.9 及更早是 Java 8 时代，agent 用了 Java 11 API。

## 许可证

[MIT](LICENSE)。IGV 归 Broad Institute 所有；本项目只分发差异部分（agent + 词表），不含 IGV 任何二进制。
