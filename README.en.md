# igv-lingo — localization toolkit for IGV

Localize the **IGV Desktop** UI (Integrative Genomics Viewer) without forking it:
a tiny Java agent rewrites string constants in the class constant pool at load time,
driven by a plain-text translation table.

- No modification of the IGV app bundle, no re-signing, no sudo
- One command to install, delete one line to disable
- Translation tables are TSV — translators do not need to write Java
- Bytecode-level safety audit keeps file-format keys (session XML tags, preference
  keys, regexes) untranslated, so sessions and settings keep working
- Language-agnostic: `locales/<lang>/strings.tsv`; Simplified Chinese ships first

![IGV menu bar in Chinese](docs/screenshot-menubar.png)

## Quick start

Requires IGV 2.x installed and a JRE 17+ (IGV bundles one).

```bash
git clone https://github.com/poplarity/igv-lingo.git
cd igv-lingo
bin/igv-lingo setup zh_CN     # build + audit + dry-run + install
```

Restart IGV. Check with `bin/igv-lingo status`. Uninstall with `bin/igv-lingo uninstall`.

## How it works

IGV has **no i18n layer** (2 `.properties` files in the whole jar, zero `ResourceBundle`
usages). Its UI strings are compile-time constants. igv-lingo therefore rewrites the
constant pool of IGV classes while they are being loaded:

1. only `CONSTANT_String` entries are touched, never `CONSTANT_Utf8` (which may also be
   a field/method/class name);
2. translations are appended as new `Utf8` entries and the `String` entry is re-pointed;
3. the rest of the class file is copied verbatim — there are no absolute offsets into the
   constant pool, so a longer pool is safe.

`bin/igv-lingo audit` classifies every literal with an ASM data-flow pass:

| kind | example | action |
|---|---|---|
| STRUCT | `createElement("Regions")`, `prefs.get("IGV.Bounds")` | denylisted per class |
| COMPARE | `nodeName.equals("Regions")`, `map.get("chr1")` | denylisted if the literal is structural elsewhere |
| DISPLAY | `new JMenuItem("File")` | translated |

This is why the menu label "Regions" becomes 区域 while the session XML tag stays
`Regions`.

## Adding a language

```bash
bin/igv-lingo new ja_JP        # skeleton with ~2400 strings, grouped by area
# edit locales/ja_JP/strings.tsv
bin/igv-lingo check            # placeholders, duplicates, stale entries
bin/igv-lingo setup ja_JP
```

See [docs/adding-a-language.md](docs/adding-a-language.md) (Chinese).

## Commands

`setup`, `install`, `uninstall`, `status`, `new`, `check`, `coverage`, `dryrun`,
`audit`, `extract`, `package`, `test`, `build` — see `bin/igv-lingo --help`.

## License

MIT. IGV itself belongs to the Broad Institute; this project ships only the diff
(agent + translation tables), never IGV binaries.
