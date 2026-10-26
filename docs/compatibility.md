# 兼容性

三个维度：IGV 版本、操作系统、Java 版本。结论先给：

| 维度 | 结论 |
|---|---|
| IGV 版本 | 2.10.3 – 2.19.8 实测可用；词表按字面量匹配，跨版本自动降级 |
| 操作系统 | macOS / Linux / Windows 都用同一条 `-javaagent` 挂载方式，官方启动器均读取 `~/.igv/java_arguments` |
| Java | agent 编译目标 Java 11（class v55），11/17/21 都能加载；工具需要 17+（只在构建时跑） |

---

## 1. IGV 版本

同一份 `locales/zh_CN/strings.tsv`，对不同版本分别跑 `audit` + `dryrun`（全部在本机实测，
jar 从 Broad 官方发布 zip 里取出）：

| IGV 版本 | class 版本 | 禁译规则 | 命中类 | 替换处 | 结构校验 | 失效词条 |
|---|---|---|---|---|---|---|
| 2.19.1 | v65 (21) | 1031 | 110 | 456 | 通过 | 0 |
| 2.19.8 | v65 (21) | 1052 | 110 | 455 | 通过 | 1 |
| 2.18.4 | v61 (17) | 1019 | 109 | 449 | 通过 | 7 |
| 2.17.4 | v61 (17) | 1014 | 109 | 446 | 通过 | 9 |
| 2.16.2 | v55 (11) | 963 | 110 | 436 | 通过 | 22 |
| 2.12.3 | v55 (11) | 936 | 111 | 430 | 通过 | 28 |
| 2.10.3 | v55 (11) | 951 | 110 | 416 | 通过 | 40 |

怎么读这张表：

- **命中和替换处**：360 条词条里，实际生效的有 416–456 处。老版本少了 10% 左右，
  因为 IGV 这几年加了不少新文案（GenArk、htsgt、SRA 等），老版本里没有这些字符串。
- **失效词条**：词表里有、但这个版本的 igv.jar 里找不到的条目。2.12 有 28 条、
  2.10 有 40 条，都是新版本才有的功能文案。它们**不会报错**，只是不生效；
  `bin/igv-l10n check --jar <该版本的 igv.jar>` 会把它们列出来。
- **结构校验**：每个改写后的 class 都被 ASM 重新解析过，全部 OK。
- **禁译规则是每个版本单独生成的**：`bin/igv-l10n audit --jar <jar> --out igv/<version>`。
  不同版本的类结构和常量池不一样，不能混用规则文件。
  仓库里已经内置 2.19.1、2.19.8、2.18.4 三份（`igv/<version>/deny_by_class.tsv`），
  其他版本用上面那条命令生成一次即可。

### 跨版本安全验证

对每个版本都检查了 `session/SessionWriter` 和 `session/IGVSessionReader`：

- 这两个类在演练产物里**从未被改写**（因为 `Regions` / `Preferences` 等标签被按类禁译）；
- 菜单类 `ui/IGVMenuBar` 在**每个版本**里都被改写（"Regions" → "区域"）。

也就是说，"菜单翻、session 标签不翻"这条边界不依赖具体版本。

### 为什么能跨版本

1. 词表按**字符串字面量**匹配，不绑定类名、方法名、行号；
2. agent 同时处理 `org/broad/igv/**` 和 `org/igv/**` 两个包前缀
   （上游 main 分支正在把包名迁到 `org.igv`，v2.19.8 及以前仍是旧包名）；
3. 禁译规则由 `audit` 从**目标 jar 本身**生成，不依赖源码。

### 不支持的范围

- **2.9 及更早**（Java 8 时代）：agent 用了 Java 11 API（`String.isBlank`、`Files.writeString` 等），
  需要改写成 Java 8 才能支持。目前没有这个计划。
- **未来版本**：只要包名仍在 `org/broad/igv` 或 `org/igv` 下，通常直接可用；
  大改版后重跑 `audit` 和 `check` 即可。

## 2. 操作系统

三种启动器都会把 `~/.igv/java_arguments` 当 JVM 参数文件传给 java（源码/实测均可查）：

| 系统 | 启动器 | 传入方式 |
|---|---|---|
| macOS | `IGV_2.19.1.app/Contents/MacOS/IGV`（原生 launcher） | `@/Users/you/.igv/java_arguments`（`ps` 可见） |
| Linux | `igv.sh` | `@"$HOME/.igv/java_arguments"` |
| Windows | `igv.bat` | `@"%USERPROFILE%\.igv\java_arguments"` |

所以安装方式在三个平台是**同一行**：

```
-javaagent:<安装目录>/igv-l10n-agent.jar=<安装目录>
```

agent 本身是纯 Java：没有 JNI、没有平台相关代码，路径全部走 `java.nio.file.Path`，
macOS / Linux / Windows 通用。

### 路径里有空格怎么办

Windows 用户名经常带空格（`C:\Users\John Doe\...`），而 JVM 的 `@argfile` 按空格切分参数。
`install` 写出的行是**带引号**的：

```
"-javaagent:C:\Users\John Doe\.igv\l10n\igv-l10n-agent.jar=C:\Users\John Doe\.igv\l10n"
```

实测：不加引号时 JVM 报 `Error opening zip file ... agent library failed`（IGV 起不来），
加引号后正常加载。这一条已经写进 `bin/igv-l10n install` 和发布包的 `install.sh`。

### 不生效的情况

- 直接 `java -jar igv.jar` 或双击 `igv.jar` 启动：绕过了启动器，`java_arguments` 不会被读取。
- 用 IDE / 自己写的脚本启动 IGV：需要自己加 `-javaagent:`。
- 某些第三方封装（如 conda 包、Docker 镜像）如果改写了启动命令，也可能绕过这个入口；
  此时把 `-javaagent` 加到它自己的启动参数里即可。

## 3. Java 版本

| 组件 | 目标 | 说明 |
|---|---|---|
| agent | **Java 11**（class v55） | IGV 2.16 及更早自带 JRE 11；v61 的 agent 在那些 JVM 上会 `UnsupportedClassVersionError` |
| 工具（audit/extract/check…） | Java 17 | 只在构建/翻译时运行，不在 IGV 进程里 |
| 构建脚本 | JRE 17+ 即可 | 没有 JDK 时会自动下载 ecj 编译器（`--release 11` / `17`） |

agent 用到的 API 都在 Java 11 以内（`List.of` / `var` / `isBlank` / `Files.writeString` / `Path.of`），
没有 Java 17+ 的语法。

## 4. 快速自检

```bash
# 你的 IGV jar 属于哪一代
bin/igv-l10n audit --jar /path/to/igv.jar --out igv/<version>
bin/igv-l10n dryrun zh_CN --jar /path/to/igv.jar --deny igv/<version>/deny_by_class.tsv
bin/igv-l10n check --jar /path/to/igv.jar --deny igv/<version>/deny_by_class.tsv
```

三行分别回答：规则生成对不对、能翻多少、哪些词条在这个版本里失效。
