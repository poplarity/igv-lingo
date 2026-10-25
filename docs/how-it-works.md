# 原理：为什么不改 IGV 也能汉化

## 1. IGV 的字符串放在哪

IGV Desktop 是 Java/Swing 程序。界面上每一个 "File"、"Cancel"、"Fit Data to Window"
都来自 Java 源码里的字符串字面量，例如：

```java
MenuAction fileMenuAction = new MenuAction("File", null, KeyEvent.VK_F);
JButton cancel = new JButton("Cancel");
```

编译之后，这些字面量进入 class 文件的**常量池**，形态是：

```
CONSTANT_String  #42 -> CONSTANT_Utf8 #17 ("File")
```

IGV 没有任何 `ResourceBundle` / `messages_zh_CN.properties`（全 jar 只有 2 个 properties），
所以不存在"换一个语言文件"的可能。要改文字，只能改这些常量。

## 2. 什么时候改：类加载期

Java 提供 `java.lang.instrument`：用 `-javaagent:xxx.jar` 启动时，JVM 会调用
`premain(String, Instrumentation)`，之后每个类被加载前都会经过
`ClassFileTransformer.transform(...)`，拿到原始字节、可以返回修改后的字节。

```
igv.jar（未被修改）           JVM                    界面
     │                         │
     │  class bytes            │
     ├───────► transform() ────┤
     │         常量池改写        │
     │                         ├──► 中文菜单
```

好处：

- IGV 安装目录、签名、更新机制完全不动；
- 想关掉只要删掉 javaagent 那一行；
- 同一份词表可以配任何 IGV 版本（字面量匹配，不绑定类名）。

## 3. 怎么改：三条纪律

改写常量池最直接的做法是把 `CONSTANT_Utf8` 的内容换掉。**这是错的**，因为同一个 Utf8 项
可能同时被字段名、方法名、类名引用：

```
CONSTANT_Utf8 "Name"  ←  String 字面量 "Name"
                      ←  Fieldref   someClass.Name:Ljava/lang/String;
```

改了它，反序列化、反射、JVM 链接都可能出问题。所以 igv-l10n 的做法是：

1. **只动 `CONSTANT_String`**，从不修改已有 `CONSTANT_Utf8`；
2. 把译文作为一个**新的 Utf8 项追加到常量池末尾**，把 `CONSTANT_String` 的索引改指过去：

   ```
   cp_count += 1
   CONSTANT_String #42 -> #N   (N = 新追加的 Utf8)
   CONSTANT_Utf8  #N  = "文件"
   ```

3. class 文件其余部分**原样拷贝**。

第 3 条能成立，是因为 class 文件里所有引用都是常量池下标（u2），
没有"常量池在文件中的绝对偏移"这种东西；方法体、异常表、`StackMapTable`、
注解属性都不受常量池长度变化影响。唯一要小心的是 8 字节常量（long/double）占两个槽位，
追加项时要正确计算 `cp_count`。

## 4. 占位符：宁可少翻，不能翻坏

IGV 大量使用 `String.format` / `MessageFormat`：

```java
String.format("Error loading %s", path)
```

如果把 `%s` 翻丢，运行时会抛 `MissingFormatArgumentException`，界面直接报错。
另外 IGV 自己用 `\u0001` 作为私有占位符（`"File not found: \u0001"`），
翻译时必须保留同样数量。

`StringsTable.specsMatch()` 在**载入词表时**逐条校验：`%` 说明符的多重集合 +
`\u0001` 的数量必须与原文一致，不一致的条目**直接跳过并记入日志**，绝不让它进 IGV。

`bin/igv-l10n check` 用同一个函数做 CI 门禁：不一致就是构建失败。

## 5. 最难的部分：一个字符串，两个身份

`"Regions"` 在 IGV 里出现两次，含义完全不同：

```java
// ui/IGVMenuBar.java —— 菜单名，应该翻成"区域"
new MenuAction("Regions", null, KeyEvent.VK_V);

// session/SessionWriter.java —— session XML 的标签名，翻了旧会话就打不开
document.createElement(SessionElement.REGIONS);   // REGIONS = "Regions"
```

而且 `javac` 会把 `static final String` 常量**内联**到使用处，所以
`SessionElement.REGIONS` 在 `SessionWriter` 的 class 里就是普通字面量，
"只翻 ui 包"这种过滤方式完全无效。

### 解决：按类禁译 + 字节码审计

`Audit` 用 ASM 的 `Analyzer` + `SourceInterpreter` 做数据流分析：
对每个方法调用点，找出真正流入字符串参数位置的常量，再按被调方法签名分类：

| 类别 | 判定依据 | 结论 |
|---|---|---|
| STRUCT | `Document.createElement`、`Element.setAttribute`、`Class.forName`、`Pattern.compile`、`Preferences.get`、`System.getProperty` … | 文件格式/协议的一部分，**按类禁译** |
| COMPARE | `String.equals`、`startsWith`、`Map.get/put`、`Collection.contains` … | 参与判断，见下 |
| DISPLAY | Swing 构造器、`setText`、`setTitle`、`addTab` … | 纯展示，放心翻译 |

两条额外规则：

1. **结构字面量在比较类里也禁译**。`Regions` 在 `SessionWriter` 是结构用途，
   那么 `IGVSessionReader` 里 `nodeName.equals("Regions")` 也不能翻——读端和写端必须一致。
2. **DOM 解析类里的比较一律按结构处理**。有些标签只出现在读端（如 `<Preferences>`），
   没有写端可以参照；只要这个类在用 `org.w3c.dom`，它比较的字符串就来自文件。

结果写进 `igv/<version>/deny_by_class.tsv`：

```
org/broad/igv/session/SessionWriter      Regions
org/broad/igv/session/IGVSessionReader   Regions
org/broad/igv/session/SessionElement     Regions
```

`SessionElement` 那条来自"常量定义类"检查：`static final String` 的值如果是结构 key，
定义类里的 `ConstantValue` 也不改。

`ClassPatcher` 在改写时对每个 `(class, literal)` 查这张表：`IGVMenuBar` 没有规则，
"Regions" → "区域"；`SessionWriter` 命中规则，保持 "Regions"。

## 6. 为什么不用 igv.js

Web 版（igv.js）同样没有 i18n，但它的字符串在打包后的 JS 里，做法会变成"改 JS bundle"，
而且它只是 IGV 的一个子集（没有 session、没有完整菜单）。本项目专注桌面版；
如果以后要做 Web 版，工具链里 `Extract`/`Check` 的词表格式可以直接复用。

## 7. 边界

- 只处理 `org/broad/igv/**` 与 `org/igv/**`（后者是上游 main 分支的新包名）。
- 不处理 IGV 通过 JIDE / Swing LAF 自带的文字（那些跟随系统语言，本来就已是中文）。
- 词表按字面量精确匹配：IGV 改了文案，对应词条失效但不报错，`check --jar` 能发现。
