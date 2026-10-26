package org.igvlingo.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 字节码级安全审计：判断每个字符串字面量在 IGV 里是「展示」还是「结构 key」。
 *
 * 做法：ASM 数据流分析（SourceInterpreter），把每个方法调用点上真正流入
 * 参数位置的常量找出来，再按被调方法的签名分类：
 *
 *   STRUCT  写文件 / 读属性 / 反射 / 正则 —— 翻译会破坏数据兼容性，按类禁译
 *   COMPARE equals / switch / map key    —— 参与判断，进入人工复核清单
 *   DISPLAY Swing 构造器 / setText       —— 纯展示，可以放心翻译
 *
 * 好处：不需要 IGV 源码，也不需要联网，直接对已安装的 igv.jar 跑，
 * 因此任何 IGV 版本、任何语言都能用同一套规则。
 *
 *   audit --jar igv.jar --out igv/2.19.1
 */
public final class Audit {

    public enum Kind {STRUCT, COMPARE, DISPLAY}

    private Audit() {
    }

    public static void run(String[] args) throws IOException {
        Path jar = null, out = Path.of("igv", "current");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jar" -> jar = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (jar == null) throw new IllegalArgumentException("需要 --jar <igv.jar>");

        // literal -> class -> kinds
        Map<String, Map<String, Set<Kind>>> hits = new TreeMap<>();
        Set<String> domClasses = new TreeSet<>();
        int classes = 0, methods = 0, analyzed = 0, failed = 0;

        for (JarScan.Cls c : JarScan.classes(jar)) {
            if (!JarScan.isIgv(c.name())) continue;
            classes++;
            ClassNode node = new ClassNode();
            try {
                new ClassReader(c.bytes()).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (RuntimeException e) {
                failed++;
                continue;
            }
            for (MethodNode m : node.methods) {
                methods++;
                if (m.instructions.size() == 0) continue;
                Frame<SourceValue>[] frames;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(c.name(), m);
                    analyzed++;
                } catch (AnalyzerException | RuntimeException e) {
                    failed++;
                    continue;
                }
                for (int i = 0; i < m.instructions.size(); i++) {
                    AbstractInsnNode insn = m.instructions.get(i);
                    if (!(insn instanceof MethodInsnNode call)) continue;
                    Frame<SourceValue> f = frames[i];
                    if (f == null) continue;
                    Kind kind = classify(call.owner, call.name, call.desc);
                    if (kind == null) continue;
                    if (call.owner.startsWith("org/w3c/dom/")) domClasses.add(c.name());

                    Type[] argTypes = Type.getArgumentTypes(call.desc);
                    int slots = 0;
                    for (Type t : argTypes) slots += t.getSize();
                    if (f.getStackSize() < slots) continue;
                    int idx = f.getStackSize() - slots;
                    for (Type t : argTypes) {
                        if (t.getSort() == Type.OBJECT && "java/lang/String".equals(t.getInternalName())) {
                            SourceValue v = f.getStack(idx);
                            if (v != null) {
                                for (AbstractInsnNode src : v.insns) {
                                    if (src instanceof LdcInsnNode ldc && ldc.cst instanceof String s
                                            && worthRecording(s)) {
                                        Kind k = kind;
                                        if (kind == Kind.COMPARE && isExternalString(f, idx)) {
                                            k = Kind.STRUCT;      // 和 XML 里的节点名/属性值比较
                                        }
                                        hits.computeIfAbsent(s, x -> new LinkedHashMap<>())
                                                .computeIfAbsent(c.name(), x -> new LinkedHashSet<>())
                                                .add(k);
                                    }
                                }
                            }
                        }
                        idx += t.getSize();
                    }
                }
            }
        }

        // 解析/序列化 XML 的类里，任何「和字面量比较」都是在认文件里的名字，
        // 不能只看这个字面量有没有别处的结构用途（例如 <Preferences> 只在读端出现）。
        List<String> upgrades = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<Kind>>> e : hits.entrySet()) {
            for (Map.Entry<String, Set<Kind>> c : e.getValue().entrySet()) {
                if (domClasses.contains(c.getKey()) && c.getValue().contains(Kind.COMPARE)) {
                    upgrades.add(e.getKey() + "\u0000" + c.getKey());
                }
            }
        }
        for (String up : upgrades) {
            String[] p = up.split("\u0000", 2);
            hits.get(p[0]).get(p[1]).add(Kind.STRUCT);
        }

        Set<String> structLits = new TreeSet<>();
        for (Map.Entry<String, Map<String, Set<Kind>>> e : hits.entrySet()) {
            if (e.getValue().values().stream().anyMatch(s -> s.contains(Kind.STRUCT))) {
                structLits.add(e.getKey());
            }
        }

        // static final String 常量：值本身是结构 key 时，定义类也禁译
        if (!structLits.isEmpty()) {
            for (JarScan.Cls c : JarScan.classes(jar)) {
                if (!JarScan.isIgv(c.name())) continue;
                ClassNode node = new ClassNode();
                try {
                    new ClassReader(c.bytes()).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                } catch (RuntimeException e) {
                    continue;
                }
                for (org.objectweb.asm.tree.FieldNode f : node.fields) {
                    if (f.value instanceof String sv && structLits.contains(sv)) {
                        hits.get(sv).computeIfAbsent(c.name(), k -> new LinkedHashSet<>()).add(Kind.STRUCT);
                    }
                }
            }
        }

        // 有结构用途的字面量：在「结构使用类」和「比较使用类」里都要禁译
        List<String> deny = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<Kind>>> e : hits.entrySet()) {
            boolean structural = structLits.contains(e.getKey());
            for (Map.Entry<String, Set<Kind>> c : e.getValue().entrySet()) {
                if (c.getValue().contains(Kind.STRUCT) || (structural && c.getValue().contains(Kind.COMPARE))) {
                    deny.add(c.getKey() + "\t" + e.getKey());
                }
            }
        }

        Files.createDirectories(out);
        List<String> denyLines = new ArrayList<>();
        denyLines.add("# 由 igv-lingo audit 自动生成，请勿手改");
        denyLines.add("class\tliteral");
        denyLines.addAll(deny);
        Files.write(out.resolve("deny_by_class.tsv"), denyLines, StandardCharsets.UTF_8);

        List<String> review = new ArrayList<>();
        review.add("literal\tkinds\tclasses\tdisplay");
        for (Map.Entry<String, Map<String, Set<Kind>>> e : hits.entrySet()) {
            Set<Kind> all = new LinkedHashSet<>();
            e.getValue().values().forEach(all::addAll);
            if (all.contains(Kind.COMPARE)) {
                review.add(e.getKey() + "\t" + names(all) + "\t" + e.getValue().size()
                        + "\t" + (all.contains(Kind.DISPLAY) ? "yes" : "no"));
            }
        }
        Files.write(out.resolve("review.tsv"), review, StandardCharsets.UTF_8);

        long displayOnly = hits.values().stream()
                .filter(v -> v.values().stream().allMatch(s -> s.contains(Kind.DISPLAY)))
                .count();
        System.out.println("扫描类 " + classes + "，方法 " + methods + "（分析 " + analyzed
                + "，跳过 " + failed + "）");
        System.out.println("有用途的字面量 " + hits.size()
                + "：纯展示 " + displayOnly
                + "，结构 key " + structLits.size()
                + "，参与比较 " + (review.size() - 1));
        System.out.println("禁译规则 " + deny.size() + " 条 -> " + out.resolve("deny_by_class.tsv"));
        System.out.println("复核清单 " + (review.size() - 1) + " 条 -> " + out.resolve("review.tsv"));
        if (!structLits.isEmpty()) {
            System.out.println("结构 key 示例: " + String.join(", ", first(structLits, 12)));
        }
    }

    /** 空的、纯标点的、超长的字面量不需要进规则表：不会有人去翻译它们。 */
    static boolean worthRecording(String s) {
        if (s == null) return true;
        if (s.isBlank() || s.length() > 120) return false;
        boolean hasLetter = s.chars().anyMatch(Character::isLetter);
        return hasLetter || s.indexOf('%') >= 0;
    }

    /** 比较里的接收者是 getNodeName()/getAttribute() 之类，说明另一边来自文件。 */
    private static boolean isExternalString(Frame<SourceValue> f, int argSlot) {
        int recv = argSlot - 1;
        if (recv < 0 || recv >= f.getStackSize()) return false;
        SourceValue v = f.getStack(recv);
        if (v == null) return false;
        for (AbstractInsnNode src : v.insns) {
            if (src instanceof MethodInsnNode m) {
                switch (m.name) {
                    case "getNodeName":
                    case "getTagName":
                    case "getLocalName":
                    case "getAttribute":
                    case "getAttributeNS":
                    case "getName":
                    case "getValue":
                        return true;
                    default:
                        break;
                }
            }
        }
        return false;
    }

    private static List<String> first(Set<String> s, int n) {
        List<String> out = new ArrayList<>();
        for (String x : s) {
            if (out.size() >= n) break;
            out.add("'" + x + "'");
        }
        return out;
    }

    private static String names(Set<Kind> kinds) {
        List<String> out = new ArrayList<>();
        for (Kind k : kinds) out.add(k.name().toLowerCase());
        return String.join("+", out);
    }

    /** 按被调方法签名判断这次传参的用途。 */
    static Kind classify(String owner, String name, String desc) {
        boolean takesString = desc.contains("Ljava/lang/String;");
        if (!takesString) return null;

        switch (owner) {
            case "org/w3c/dom/Document":
                if (name.equals("createElement") || name.equals("createAttribute")
                        || name.equals("getElementsByTagName")) return Kind.STRUCT;
                break;
            case "org/w3c/dom/Element":
            case "org/w3c/dom/Attr":
                if (name.equals("setAttribute") || name.equals("getAttribute")
                        || name.equals("hasAttribute") || name.equals("removeAttribute")) return Kind.STRUCT;
                break;
            case "java/lang/Class":
                if (name.equals("forName") || name.equals("getResource")
                        || name.equals("getResourceAsStream")) return Kind.STRUCT;
                break;
            case "java/lang/System":
                if (name.equals("getProperty") || name.equals("setProperty") || name.equals("getenv")) {
                    return Kind.STRUCT;
                }
                break;
            case "java/util/regex/Pattern":
                if (name.equals("compile") || name.equals("matches")) return Kind.STRUCT;
                break;
            case "java/util/prefs/Preferences":
                if (name.equals("get") || name.equals("put") || name.equals("remove")) return Kind.STRUCT;
                break;
            case "java/util/Properties":
                if (name.equals("getProperty") || name.equals("setProperty")) return Kind.STRUCT;
                break;
            case "java/lang/Integer":
            case "java/lang/Boolean":
                if (name.equals("getInteger") || name.equals("getBoolean")) return Kind.STRUCT;
                break;
            case "java/lang/String":
                if (name.equals("equals") || name.equals("equalsIgnoreCase") || name.equals("startsWith")
                        || name.equals("endsWith") || name.equals("contains") || name.equals("compareTo")
                        || name.equals("compareToIgnoreCase") || name.equals("matches") || name.equals("split")
                        || name.equals("indexOf") || name.equals("lastIndexOf") || name.equals("replace")
                        || name.equals("replaceAll")) return Kind.COMPARE;
                break;
            case "java/util/Objects":
                if (name.equals("equals")) return Kind.COMPARE;
                break;
            default:
                break;
        }

        if (owner.startsWith("org/broad/igv/prefs/") || owner.startsWith("org/igv/prefs/")) {
            if (name.startsWith("get") || name.equals("put") || name.equals("remove")) return Kind.STRUCT;
        }

        if (isMap(owner)) {
            if (name.equals("get") || name.equals("put") || name.equals("putIfAbsent")
                    || name.equals("containsKey") || name.equals("remove") || name.equals("getOrDefault")
                    || name.equals("computeIfAbsent") || name.equals("merge")) return Kind.COMPARE;
        }
        if (isCollection(owner)) {
            if (name.equals("contains") || name.equals("indexOf") || name.equals("lastIndexOf")
                    || name.equals("remove") || name.equals("add")) return Kind.COMPARE;
        }

        if (owner.startsWith("javax/swing/") || owner.startsWith("java/awt/")) {
            if (name.equals("<init>") || name.equals("setText") || name.equals("setTitle")
                    || name.equals("setToolTipText") || name.equals("addTab") || name.equals("setLabel")
                    || name.startsWith("show")) return Kind.DISPLAY;
        }
        return null;
    }

    private static boolean isMap(String owner) {
        return owner.equals("java/util/Map") || owner.equals("java/util/HashMap")
                || owner.equals("java/util/LinkedHashMap") || owner.equals("java/util/TreeMap")
                || owner.equals("java/util/concurrent/ConcurrentHashMap") || owner.equals("java/util/Hashtable")
                || owner.equals("java/util/WeakHashMap") || owner.equals("java/util/EnumMap");
    }

    private static boolean isCollection(String owner) {
        return owner.equals("java/util/Set") || owner.equals("java/util/List")
                || owner.equals("java/util/Collection") || owner.equals("java/util/HashSet")
                || owner.equals("java/util/LinkedHashSet") || owner.equals("java/util/ArrayList")
                || owner.equals("java/util/LinkedList") || owner.equals("java/util/TreeSet");
    }
}
