# Installation guide (for non-programmers)

No coding, no extra software. About five minutes.

> macOS and Windows. Linux users: see the CLI in the [README](../README.en.md).

---

## Before you start

1. **IGV is installed.** macOS: it should be in `Applications`. Windows: it opens from the Start menu.
2. **Know your IGV version.** Open IGV and check the title bar or `Help → About IGV`,
   e.g. `2.19.1`. If unsure, a 2.19 package usually works.

---

## Step 1 — Download

Open **https://github.com/poplarity/igv-lingo/releases/latest**

Under **Assets**, pick a file named like:

```
igv-lingo-zh_CN-2.19.1.zip
```

(The number matches your IGV version; choose the closest one.)

## Step 2 — Unzip

- macOS: double-click the zip.
- Windows: right-click → *Extract All*.

You should see:

```
install-mac.command        ← macOS
install-windows.bat        ← Windows
使用说明.txt (instructions)
igv-lingo-agent.jar
deny_by_class.tsv
locales/
```

## Step 3 — Run the installer

**macOS:** double-click `install-mac.command`. A Terminal window appears and finishes with
`安装完成 🎉` (done).

If macOS says *"cannot be opened because it is from an unidentified developer"*:
close the dialog, **right-click** the file → **Open** → **Open** again. Only needed once.

**Windows:** double-click `install-windows.bat`. If SmartScreen appears, choose
*More info → Run anyway*.

## Step 4 — Restart IGV

This is the step people forget. Quit IGV completely:

- macOS: **`Cmd + Q`** (closing the window is not enough)
- Windows: `File → Exit`

Then reopen IGV.

## Step 5 — Check

The menu bar should now read:

```
文件   基因组   视图   轨道   区域   工具   帮助
```

![Chinese menu bar](screenshot-menubar.png)

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Menu still English | IGV was not fully restarted | Quit with `Cmd+Q` / `File → Exit`, reopen |
| Double-click does nothing | Blocked by the OS | macOS: right-click → Open. Windows: run as administrator |
| "Could not find IGV" | IGV is not in Applications | Move IGV into Applications, run the installer again |
| Only partly translated | Expected | The table is not complete yet |
| Did it install? | — | Log: macOS `~/.igv/lingo/igv-lingo.log`, Windows `%USERPROFILE%\.igv\lingo\igv-lingo.log` |

A successful log line looks like:

```
[igv-lingo] 语言 zh_CN，词条 360，禁译规则 1031
```

Still stuck? Open an [issue](https://github.com/poplarity/igv-lingo/issues) with the log.

---

## Uninstall

Run `uninstall-mac.command` (macOS) or `uninstall-windows.bat` (Windows), then restart IGV.

To remove everything, delete `~/.igv/lingo` (Windows: `%USERPROFILE%\.igv\lingo`).

---

## FAQ

**Will this break my IGV?**
No. Nothing inside the IGV app is modified. We copy a table into your user folder and add one
line to `~/.igv/java_arguments` (backed up first). Remove both to revert.

**Do I need Java?**
No, IGV bundles its own.

**Is Chinese the default?**
Yes — once installed it is Chinese without any configuration. If you have several language
packs installed it follows your system language.

**Does it slow IGV down?**
No. Strings are rewritten once, while classes load.
