package org.igvl10n.tools;

import org.igvl10n.ClassPatcher;
import org.igvl10n.StringsTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线演练：不起 GUI，把整个 igv.jar 按词表改写一遍并报告命中。
 * 用来在没有 IGV 的机器上（CI）验证规则和词表。
 *
 *   dryrun --jar igv.jar --locale locales/zh_CN --deny igv/2.19.1/deny_by_class.tsv [--out DIR]
 */
public final class DryRun {

    private DryRun() {
    }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException ignored) {
                    // 删不掉就算了
                }
            });
        }
    }

    public static void run(String[] args) throws IOException {
        Path jar = null, locale = null, deny = null, out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jar" -> jar = Path.of(args[++i]);
                case "--locale" -> locale = Path.of(args[++i]);
                case "--deny" -> deny = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (jar == null || locale == null) {
            throw new IllegalArgumentException("需要 --jar 和 --locale");
        }
        StringsTable table = StringsTable.load(locale, deny);
        if (out != null) {
            // 清掉上一次的产物，避免"上次没禁译"的旧 class 留在目录里造成误判
            deleteRecursively(out.resolve("classes"));
        }

        int nClasses = 0, nStrings = 0, bad = 0;
        List<String> rows = new ArrayList<>();
        rows.add("class\tcount\thits");
        for (JarScan.Cls c : JarScan.classes(jar)) {
            if (!JarScan.isIgv(c.name())) continue;
            ClassPatcher.Result r = ClassPatcher.patch(c.bytes(), table, c.name());
            if (r == null) continue;
            nClasses++;
            nStrings += r.hits.size();
            try {   // 结构校验：改写后的字节必须还能被完整解析
                new ClassReader(r.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                }, 0);
            } catch (Throwable t) {
                bad++;
                System.out.println("结构损坏: " + c.name() + " : " + t);
            }
            rows.add(c.name() + "\t" + r.hits.size() + "\t" + String.join(" | ", r.hits));
            if (out != null) {
                Path dst = out.resolve("classes").resolve(c.name() + ".class");
                Files.createDirectories(dst.getParent());
                Files.write(dst, r.bytes);
            }
        }
        if (out != null) {
            Files.createDirectories(out);
            Files.write(out.resolve("report.tsv"), rows, StandardCharsets.UTF_8);
        }
        System.out.println("语言: " + table.locale() + "，词条 " + table.size()
                + "，禁译规则 " + table.denyRules()
                + (table.rejected().isEmpty() ? "" : "，占位符不匹配 " + table.rejected().size()));
        System.out.println("命中: " + nClasses + " 个类 / " + nStrings + " 处字符串"
                + (bad == 0 ? "，结构校验通过" : "，结构损坏 " + bad + " 个"));
        if (out != null) System.out.println("报告: " + out.resolve("report.tsv"));
        if (bad > 0) System.exit(1);
    }
}
