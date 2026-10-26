# 参与贡献

最需要的贡献只有一个：**把词表翻完**。不需要懂 Java。

> 只想用、不想改代码？看 [安装指南](docs/安装指南.md)。

## 翻译

```bash
git clone https://github.com/<you>/igv-lingo.git
cd igv-lingo
bin/igv-lingo check                     # 先确认现状
bin/igv-lingo coverage zh_CN            # 看哪些区域还没翻
```

编辑 `locales/zh_CN/strings.tsv`：

```tsv
# 英文原文 <TAB> 译文 [<TAB> 备注]
File	文件
Load Genome from File...	从文件载入基因组...
```

规则：

1. **左侧不要改**。它是 class 常量池里的字面量，改一个字符就匹配不上。末尾空格、`...` 都不能动。
2. **占位符必须原样保留**：`%s`、`%d`、`%1$s`，以及 IGV 私有的 `\u0001`（在编辑器里看起来是一个小方块/控制符）。
   数量不一致 `check` 会报错并拒绝合入。
3. **只翻纯展示文字**。遇到"这条到底能不能翻"的疑问，先跑 `check`——如果它出现在
   `被禁译规则覆盖` 里，说明这个字符串在某处是文件格式的一部分，agent 会自动跳过那部分。
4. **术语统一**：先看 `locales/zh_CN/glossary.tsv`。新增术语请同时加到术语表。

提交前：

```bash
bin/igv-lingo check          # 必须通过
bin/igv-lingo dryrun zh_CN   # 可选：看命中数量和结构校验
```

PR 标题建议：`i18n(zh_CN): 翻译轨道右键菜单`。

## 新增语言

```bash
bin/igv-lingo new ja_JP
```

会生成 `locales/ja_JP/strings.tsv` 骨架（约 2400 行，按区域分组）和
`locales/ja_JP/meta.tsv`。填完跑 `bin/igv-lingo check` 和 `bin/igv-lingo setup ja_JP` 验证，
再提 PR。新语言不需要改任何 Java 代码。

## 改代码

```bash
bin/igv-lingo build
bin/igv-lingo test
```

- `src/agent/` 必须保持**零依赖**（只能用 JDK 自带类），因为它要在 IGV 的 JVM 里运行。
- `src/tools/` 可以用 ASM。
- 任何改动 `ClassPatcher` 的 PR，必须保证 `dryrun` 的 `结构校验通过`。
- 改 `Audit` 的规则时，请附上"为什么这条规则是安全的"说明，并确认
  `igv/2.19.1/deny_by_class.tsv` 的 diff 里没有把菜单文字误禁。

## 支持新的 IGV 版本

```bash
bin/igv-lingo audit --jar /path/to/igv.jar     # 生成 igv/<version>/deny_by_class.tsv
bin/igv-lingo check                            # 看哪些词条在新版里失效
```

把新的 `igv/<version>/` 一起提交。

## 提交信息

用祈使句、说明"改了什么"：

- `i18n(zh_CN): 补全 View 菜单`
- `audit: 把 DOM 解析类里的字符串比较按结构用途处理`
- `fix(agent): 修正 zh_CN 回退到 zh 的逻辑`
