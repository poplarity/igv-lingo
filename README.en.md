# IGV Lingo

[简体中文](README.md) · **English**

[![ci](https://github.com/poplarity/igv-lingo/actions/workflows/ci.yml/badge.svg)](https://github.com/poplarity/igv-lingo/actions/workflows/ci.yml)
[![release](https://img.shields.io/github/v/release/poplarity/igv-lingo?color=blue)](https://github.com/poplarity/igv-lingo/releases/latest)
[![license](https://img.shields.io/github/license/poplarity/igv-lingo)](LICENSE)
![IGV](https://img.shields.io/badge/IGV-2.10%E2%80%932.19-2ea44f)
![Java](https://img.shields.io/badge/Java-11%2B-orange)

Localize the [IGV Desktop](https://igv.org) (Integrative Genomics Viewer) UI without forking it.
A dependency-free Java agent plus a TSV translation table: **no changes to the IGV app bundle,
no re-signing, and you can switch it off at any time**.

![IGV menu bar in Chinese](docs/screenshot-menubar.png)

> Chinese (Simplified) is the first and currently only shipped language. The toolchain itself is
> language-agnostic — see [Adding a language](#adding-a-language).

---

## Install in 30 seconds (no coding)

1. Open [Releases](https://github.com/poplarity/igv-lingo/releases/latest) and download
   `igv-lingo-zh_CN-<your-IGV-version>.zip` (if unsure, take a 2.19 one — it usually works).
2. Unzip, then **double-click** `install-mac.command` (macOS) or `install-windows.bat` (Windows).
3. When it says "安装完成" (done), **quit IGV completely** (`Cmd+Q` on macOS) and reopen it.

The menu bar should now read `文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助`.

If macOS refuses to open the script the first time: **right-click → Open**. On Windows:
"More info → Run anyway".

<details>
<summary>I prefer the command line</summary>

```bash
git clone https://github.com/poplarity/igv-lingo.git
cd igv-lingo
bin/igv-lingo setup zh_CN
```

Uninstall: `bin/igv-lingo uninstall`

</details>

---

## What it changes

| Before | After |
|---|---|
| File / Genomes / View / Tracks / Regions / Tools / Help | 文件 / 基因组 / 视图 / 轨道 / 区域 / 工具 / 帮助 |
| Load Genome from File... | 从文件载入基因组... |
| Fit Data to Window | 数据适应窗口 |
| Group alignments by | 按…分组比对 |
| Sort By Depth | 按深度排序 |
| Save PNG Screenshot ... | 保存 PNG 截图... |

zh_CN today: **360 entries**, **110 classes / 456 UI strings**.
Menu bar 72%, alignment context menu 58%, file/view actions 52%.

## How it works

IGV has no i18n layer (2 `.properties` files in the whole jar, zero `ResourceBundle` usages).
Its UI text is compiled into class files as string constants, so localizing means rewriting them.

IGV Lingo does that **at class-load time** via `java.lang.instrument`: only `CONSTANT_String`
entries are touched, translations are appended as new constants, and the originals stay intact —
the IGV installation, its signature and its updater are untouched.

```
igv.jar ──► ClassFileTransformer ──► "File" → "文件" in the constant pool ──► localized UI
             (java agent)             (original constant preserved)
```

The hard part is not the bytecode. It is that **one English word can have two identities**:
`"Regions"` is both a menu label (translate it) and a session-XML tag (translating it breaks
existing sessions). The tool runs an ASM data-flow pass, classifies every literal as
display / comparison / structural, and denylists structural ones per class.

Details: [how it works](docs/how-it-works.md) · [safety rules](docs/safety-rules.md)

## Compatibility

| | Supported |
|---|---|
| IGV | **2.10.3 – 2.19.8 tested**; rules shipped for 2.19.1 / 2.19.8 / 2.18.4 |
| OS | macOS / Linux / Windows |
| Java | 11+ (IGV bundles one) |

Measured with the same translation table:

| IGV | replacements | structure check | stale entries |
|---|---|---|---|
| 2.19.8 | 455 | pass | 1 |
| 2.18.4 | 449 | pass | 7 |
| 2.16.2 | 436 | pass | 22 |
| 2.12.3 | 430 | pass | 28 |
| 2.10.3 | 416 | pass | 40 |

After upgrading IGV, re-run `bin/igv-lingo audit` and `check`.
See [compatibility](docs/compatibility.md).

## CLI

For developers and translators; end users do not need it.

| Command | Purpose |
|---|---|
| `bin/igv-lingo setup [locale]` | build + audit + dry-run + install |
| `bin/igv-lingo install / uninstall` | toggle the agent |
| `bin/igv-lingo status` | install state and recent log |
| `bin/igv-lingo new <locale>` | generate a translation skeleton |
| `bin/igv-lingo check` | validate tables (same as CI) |
| `bin/igv-lingo coverage <locale>` | translation progress |
| `bin/igv-lingo dryrun <locale>` | rewrite the whole jar offline |
| `bin/igv-lingo audit` | regenerate deny rules for the current jar |
| `bin/igv-lingo package <locale>` | build a release zip |

## Adding a language

```bash
bin/igv-lingo new ja_JP        # ~2400 strings, grouped by area
# edit locales/ja_JP/strings.tsv
bin/igv-lingo check
bin/igv-lingo setup ja_JP
```

Tables are plain TSV (Excel-friendly). Only touch the second column:

```tsv
File	ファイル
Load Genome from File...	ファイルからゲノムを読み込む...
```

See [adding a language](docs/adding-a-language.md).

## Project layout

```
igv-lingo/
├── bin/igv-lingo            CLI entry point
├── src/agent/               runtime agent (no dependencies, Java 11)
├── src/tools/               audit / extract / check / coverage / dryrun
├── locales/zh_CN/           translation table + glossary
├── igv/<version>/           deny rules per IGV version
├── scripts/                 double-click installers (macOS / Windows)
└── docs/                    internals, install guide, safety rules
```

## Contributing

No Java needed to help with translations: edit the TSV, run `check`, open a PR.

- [Contributing guide](CONTRIBUTING.md)
- Open tasks: [panels & common widgets](https://github.com/poplarity/igv-lingo/issues/1), [Japanese](https://github.com/poplarity/igv-lingo/issues/2)

## FAQ

**Does it modify IGV?**
No. Nothing inside the app bundle changes. We copy a table to `~/.igv/lingo` and add one
`-javaagent` line to `~/.igv/java_arguments` (backed up first). Delete both to revert.

**Why are some strings still English?**
Either they are not translated yet (`coverage`), or they are file-format keys that must stay
English (`igv/<version>/deny_by_class.tsv`).

**Does it survive IGV upgrades?**
Yes. Re-run `audit` to regenerate rules for the new version, then `check`.

**Why not an IGV plugin?**
IGV's plugin API cannot replace UI strings; forking the source means re-merging every release.
The agent keeps translations decoupled from IGV's code.

## License

[MIT](LICENSE). IGV is © Broad Institute. This project ships only the diff (agent + tables),
never IGV binaries.
