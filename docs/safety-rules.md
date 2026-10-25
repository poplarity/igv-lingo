# 安全规则：哪些字符串不能翻

汉化 IGV 最大的风险不是"翻得不好"，而是"翻坏了功能"。这篇文档解释判定规则，
以及 `check` 报出提示时该怎么办。

## 三类字符串

`bin/igv-l10n audit` 对每个字面量做数据流分析，结论只有三类。

### DISPLAY —— 放心翻

字符串最终流向 Swing 组件的文字属性：

```java
new JMenuItem("File")
new JLabel("Start")
label.setText("Coverage")
frame.setTitle("Preferences")
tabbedPane.addTab("Alignments", panel)
```

判定：调用方是 `javax.swing.*` / `java.awt.*` 的构造器、`setText`、`setTitle`、
`setToolTipText`、`addTab` 等，且该参数是 `String`。

### COMPARE —— 看情况

字符串参与判断：

```java
if (nodeName.equals("Regions")) ...
switch (type) { case "Panel": ... }
map.get("chr1")
list.contains("DATA FILE")
```

单独看不能决定。规则是：

- 如果这个字面量**在别处有 STRUCT 用途**（比如它同时是 XML 标签名），
  那么所有拿它做比较的类都禁译——读端写端必须一致；
- 否则可以翻。典型例子是菜单项：`new LoadFromURLMenuAction("Load Genome from URL...", ...)`，
  `actionCommand` 就是同一个常量，标签和命令一起变成中文反而一致。

这类条目会出现在 `igv/<version>/review.tsv`，供人工复核。

### STRUCT —— 不能翻

字符串进入文件格式、协议、反射或配置：

| 调用 | 例子 | 翻了会怎样 |
|---|---|---|
| `Document.createElement` / `createAttribute` | `<Regions>` | 旧 session 打不开，新 session 别的 IGV 读不了 |
| `Element.setAttribute` / `getAttribute` | `id="chr1"` | 属性读不到 |
| `Class.forName` / `getResource` | 类名、资源路径 | ClassNotFound |
| `Pattern.compile` | 正则 | 匹配失效或抛异常 |
| `Preferences.get/put` | `IGV.Bounds` | 配置读不到 |
| `System.getProperty` | `user.home` | 行为异常 |
| `Map.get/put`（当 key 来自文件时） | 列名 | 数据解析错位 |

处理方式：写进 `igv/<version>/deny_by_class.tsv`，agent 在**这些类里**跳过该字符串；
其他类里照常翻译。

## 为什么是"按类"而不是"全局"

因为同一段英文在不同类里身份不同：

```java
// IGVMenuBar：菜单
new MenuAction("Regions", ...);

// SessionWriter：XML
document.createElement("Regions");
```

全局禁译会让菜单留着英文；全局翻译会让 session 坏掉。按类禁译是唯一能两全的方案。

## 自动规则之外的三个特例

1. **常量定义类**：`public static final String REGIONS = "Regions";`
   它的 `ConstantValue` 也属于结构用途，定义类一并禁译。
2. **只出现在读端的标签**：`<Preferences>` 只被读、不被写，没有写端可以参照。
   规则是：只要这个类在用 `org.w3c.dom`，它比较的字符串就当作来自文件。
3. **DOM 类里的比较**：见上一条，等价于把该类所有 COMPARE 升级为 STRUCT。

## `check` 报警时怎么办

| 输出 | 含义 | 处理 |
|---|---|---|
| `占位符不一致（必须修）` | `%s` 数量或类型对不上 | 按原文补齐，`check` 不通过不能合 |
| `重复键` | 同一英文写了两行 | 删掉一行 |
| `本版 IGV 里已不存在（可删）` | IGV 升级后文案变了 | 删掉或按新文案改写 |
| `被禁译规则覆盖（部分类里不会生效）` | 该字符串在某处是结构 key | 正常现象；确认那处不是你想翻的界面文字即可 |

## 怎么验证"没翻坏"

1. `bin/igv-l10n dryrun <locale>`：全量改写一遍，最后一行必须是 `结构校验通过`；
2. 启动 IGV，打开一个旧 session（`File > Open Session`）；
3. 改几个偏好设置，重启 IGV 看是否保留；
4. 导出一次 session，确认 XML 里标签仍是英文：
   ```bash
   grep -o "<[A-Za-z]*" mysession.xml | sort | uniq -c
   ```

## 已知的取舍

- **宁可少翻**：判定不确定时归为 STRUCT。代价是某个菜单项留着英文，收益是不会坏。
- **不追求 100%**：数据格式类字符串（track 类型、列名、session 标签）本来就不该翻；
  `coverage` 的分母包含它们，所以覆盖率天然到不了 100%。
- **跨版本**：规则跟着 IGV 版本走。升级 IGV 后先重跑 `audit`，再 `check`。
