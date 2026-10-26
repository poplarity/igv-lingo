# 新增一个语言

以日语为例。整个过程不需要写 Java。

## 1. 生成骨架

```bash
bin/igv-lingo new ja_JP
```

它会：

- 找到本机 `igv.jar`（`/Applications/IGV_*.app/Contents/Java/lib/igv.jar` 等，也可用 `IGV_JAR=` 指定）；
- 扫出约 2400 条界面文字候选；
- 写入 `locales/ja_JP/strings.tsv`，按区域分组、按字母排序，译文列留空；
- 写入 `out/candidates.tsv`（带出现次数和所属类，供判断优先级）。

如果 IGV 装在其他位置：

```bash
IGV_JAR=/opt/igv/lib/igv.jar bin/igv-lingo new ja_JP
```

## 2. 填词表

`strings.tsv` 是 TSV，Excel / Numbers / VS Code 都能编辑：

```tsv
# ---- ui/IGVMenuBar ----
File	ファイル
Genomes	ゲノム
Load Genome from File...	ファイルからゲノムを読み込む...
```

- 第 1 列是英文原文，**不要改**（末尾空格、`...` 都要保留）；
- 第 2 列是译文，留空表示"还没翻"；
- 第 3 列可选，写备注（不会参与匹配）。

优先级建议：`ui/IGVMenuBar` → `track/TrackMenuUtils` → `ui/panel` → `ui/util` →
`ui/action` → `sam/AlignmentTrackMenu` → 其余。先把菜单翻完，观感立刻不同。

## 3. 校验

```bash
bin/igv-lingo check
```

会检查：

| 检查 | 级别 |
|---|---|
| `%s/%d` 占位符数量不一致 | 错误（CI 失败） |
| `\u0001` 占位符数量不一致 | 错误 |
| 同一个英文原文出现两次 | 错误 |
| 本版 IGV 里已不存在的老词条 | 提示 |
| 被禁译规则覆盖的条目 | 提示（那部分不会生效） |
| 术语与 `glossary.tsv` 不一致 | 提示 |

## 4. 看覆盖率

```bash
bin/igv-lingo coverage ja_JP
```

输出按区域的 候选/已译/覆盖率 表格，以及未翻译示例，方便决定下一步翻哪里。

## 5. 装到本机试用

```bash
bin/igv-lingo setup ja_JP
```

会依次：构建 agent → 对当前 igv.jar 跑审计（生成禁译规则）→ 离线演练 →
装到 `~/.igv/lingo` → 往 `~/.igv/java_arguments` 加一行。

重启 IGV。语言选择顺序：

1. `-Digv.lingo.locale=ja_JP`（可写进 `java_arguments`）
2. 系统语言（`ja_JP` → `ja` 回退）
3. `locales/` 下第一个可用语言

日志：`~/.igv/lingo/igv-lingo.log`。

## 6. 打发布包

```bash
bin/igv-lingo package ja_JP
# dist/igv-lingo-ja_JP-2.19.1.zip
```

zip 里包含 agent、该语言词表、禁译规则和一个独立 `install.sh`，
别人解压后 `./install.sh` 即可，不需要整个仓库。

## 7. 提交

```bash
git checkout -b i18n/ja_JP
git add locales/ja_JP
git commit -m "i18n(ja_JP): 初始词表"
```

PR 里附上 `bin/igv-lingo check` 的输出即可。

## 常见问题

**能不能只翻一部分就发布？**
可以。词表里没翻的条目保持英文，不会报错。建议在 `locales/ja_JP/meta.tsv` 里把
`status` 写成 `beta`。

**同一个英文在不同上下文要不同译法怎么办？**
当前版本不支持。IGV 里这种歧义很少；遇到时优先保证"菜单/对话框"的译法，
把歧义条目写进 `glossary.tsv` 的备注列。

**IGV 升级后词表会失效吗？**
只有文案变了的条目会失效。`bin/igv-lingo check`（带 `--jar`）会列出
"本版 IGV 里已不存在"的条目；删掉或改写它们即可。
