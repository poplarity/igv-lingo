package org.igvl10n.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 覆盖率报告：告诉译者「翻了多少、还差哪些、哪些最值得先翻」。
 *
 *   coverage --jar igv.jar --locale locales/zh_CN [--top 30]
 */
public final class Coverage {

    private Coverage() {
    }

    public static void run(String[] args) throws IOException {
        Path jar = null, locale = null;
        int top = 30;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jar" -> jar = Path.of(args[++i]);
                case "--locale" -> locale = Path.of(args[++i]);
                case "--top" -> top = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (jar == null || locale == null) throw new IllegalArgumentException("需要 --jar 和 --locale");

        Set<String> translated = readKeys(locale);

        Map<String, int[]> byArea = Check.coverageByArea(jar, translated);
        int total = 0, hit = 0;
        for (int[] v : byArea.values()) {
            total += v[0];
            hit += v[1];
        }
        System.out.printf("%s 覆盖：%d / %d（%.1f%%）%n",
                locale.getFileName(), hit, total, total == 0 ? 0.0 : 100.0 * hit / total);
        System.out.println();
        System.out.printf("%-28s %8s %8s %8s%n", "区域", "候选", "已译", "覆盖率");
        byArea.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> System.out.printf("%-28s %8d %8d %7.1f%%%n",
                        e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[0] == 0 ? 0.0 : 100.0 * e.getValue()[1] / e.getValue()[0]));

        List<String> missing = new ArrayList<>();
        for (JarScan.Cls c : JarScan.classes(jar)) {
            if (!JarScan.isIgv(c.name())) continue;
            for (String s : JarScan.literals(c.bytes())) {
                if (!Extract.looksLikeUi(s) || translated.contains(s)) continue;
                missing.add(s);
            }
        }
        Set<String> uniq = new LinkedHashSet<>(missing);
        System.out.println();
        System.out.println("未翻译示例（前 " + top + " 条）:");
        uniq.stream().limit(top).forEach(s -> System.out.println("  " + s));
    }

    private static Set<String> readKeys(Path localeDir) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String line : Files.readAllLines(localeDir.resolve("strings.tsv"), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] cols = line.split("\t", 3);
            if (cols.length >= 2 && !cols[1].isBlank()) out.add(cols[0]);
        }
        return out;
    }
}
