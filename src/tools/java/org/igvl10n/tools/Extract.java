package org.igvl10n.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 从 igv.jar 导出「可能是界面文字」的字符串，并生成某个新语言的词表骨架。
 *
 *   extract --jar igv.jar --out out/candidates.tsv [--skeleton locales/ja_JP]
 *
 * 没有 igv.jar 也能用：--jar 也可以指向解压后的目录。
 */
public final class Extract {

    private static final List<Pattern> NOT_UI = List.of(
            Pattern.compile("^[\\w.\\-/\\\\:]+\\.(java|class|jar|txt|gz|bw|bed|bam|vcf|xml|json|png|jpg|tsv|csv|html|css|js)$"),
            Pattern.compile("^(https?|ftp)://"),
            Pattern.compile("^[\\w\\-]+(\\.[\\w\\-]+)+$"),
            Pattern.compile("^[A-Z][A-Z0-9_]*$"),
            Pattern.compile("^\\d+([.,]\\d+)?$"),
            Pattern.compile("^[#%&*+/=<>@\\\\^`|~{}\\[\\]]+$"),
            Pattern.compile("^\\s*$")
    );

    private Extract() {
    }

    public static void run(String[] args) throws IOException {
        Path jar = null;
        Path out = null;
        Path skeleton = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--jar" -> jar = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--skeleton" -> skeleton = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (jar == null) throw new IllegalArgumentException("需要 --jar <igv.jar>");

        Map<String, Integer> count = new TreeMap<>();
        Map<String, String> area = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> owners = new LinkedHashMap<>();

        int classes = 0;
        for (JarScan.Cls c : JarScan.classes(jar)) {
            if (!JarScan.isIgv(c.name())) continue;
            classes++;
            for (String s : JarScan.literals(c.bytes())) {
                if (!looksLikeUi(s)) continue;
                count.merge(s, 1, Integer::sum);
                area.putIfAbsent(s, JarScan.area(c.name()));
                owners.computeIfAbsent(s, k -> new java.util.TreeSet<>()).add(c.name());
            }
        }

        System.out.println("扫描 IGV 类: " + classes);
        System.out.println("界面文字候选: " + count.size());

        if (out != null) {
            Files.createDirectories(out.toAbsolutePath().getParent());
            List<String> rows = new ArrayList<>();
            rows.add("literal\tclasses\tarea\towners");
            count.entrySet().stream()
                    .sorted(Comparator.comparing((Map.Entry<String, Integer> e) -> area.get(e.getKey()))
                            .thenComparing(Map.Entry::getKey))
                    .forEach(e -> rows.add(e.getKey() + "\t" + e.getValue() + "\t"
                            + area.get(e.getKey()) + "\t"
                            + String.join(",", owners.get(e.getKey()))));
            Files.write(out, rows, StandardCharsets.UTF_8);
            System.out.println("写出: " + out);
        }

        if (skeleton != null) {
            Files.createDirectories(skeleton);
            Path tsv = skeleton.resolve("strings.tsv");
            if (Files.exists(tsv)) {
                System.out.println("已存在，未覆盖: " + tsv);
            } else {
                List<String> lines = new ArrayList<>();
                lines.add("# " + skeleton.getFileName() + " 词表");
                lines.add("# 格式：英文原文 <TAB> 译文 [<TAB> 备注]；# 开头为注释。");
                lines.add("# 只翻“纯展示”文字；含 %s/%d 的条目必须保留占位符。");
                lines.add("# 校验：bin/igv-l10n check " + skeleton.getFileName());
                lines.add("");
                Map<String, List<String>> byArea = new TreeMap<>();
                for (String s : count.keySet()) {
                    byArea.computeIfAbsent(area.get(s), k -> new ArrayList<>()).add(s);
                }
                for (Map.Entry<String, List<String>> e : byArea.entrySet()) {
                    lines.add("# ---- " + e.getKey() + " ----");
                    e.getValue().stream().sorted().forEach(s -> lines.add(s + "\t"));
                    lines.add("");
                }
                Files.write(tsv, lines, StandardCharsets.UTF_8);
                System.out.println("词表骨架: " + tsv + "（" + count.size() + " 行待填）");
            }
        }
    }

    static boolean looksLikeUi(String s) {
        if (s.length() < 2 || s.length() > 90) return false;
        if (!s.chars().anyMatch(Character::isLetter)) return false;
        for (Pattern p : NOT_UI) {
            if (p.matcher(s).matches()) return false;
        }
        if (s.indexOf(' ') >= 0) return true;
        if (Character.isUpperCase(s.charAt(0))) return true;
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }
}
