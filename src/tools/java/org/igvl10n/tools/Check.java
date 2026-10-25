package org.igvl10n.tools;

import org.igvl10n.StringsTable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 校验词表：占位符、重复键、空译文、已失效的老词条、被禁译规则吃掉的词条。
 *
 *   check --locales locales [--jar igv.jar] [--deny igv/2.19.1/deny_by_class.tsv]
 *
 * 退出码 1 表示有硬错误（占位符不一致、重复键），CI 直接失败。
 */
public final class Check {

    private Check() {
    }

    public static void run(String[] args) throws IOException {
        Path locales = Path.of("locales");
        Path jar = null, deny = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--locales" -> locales = Path.of(args[++i]);
                case "--jar" -> jar = Path.of(args[++i]);
                case "--deny" -> deny = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (!Files.isDirectory(locales)) throw new IllegalArgumentException("找不到 locales 目录: " + locales);

        Set<String> jarLiterals = null;
        if (jar != null) {
            jarLiterals = new HashSet<>();
            for (JarScan.Cls c : JarScan.classes(jar)) {
                if (JarScan.isIgv(c.name())) jarLiterals.addAll(JarScan.literals(c.bytes()));
            }
        }
        Map<String, Set<String>> denyByClass = loadDeny(deny);

        boolean failed = false;
        List<Path> dirs = new ArrayList<>();
        try (Stream<Path> s = Files.list(locales)) {
            s.filter(Files::isDirectory).sorted().forEach(dirs::add);
        }
        for (Path dir : dirs) {
            System.out.println("== " + dir.getFileName());
            Path tsv = dir.resolve("strings.tsv");
            if (!Files.isReadable(tsv)) {
                System.out.println("  缺少 strings.tsv");
                failed = true;
                continue;
            }
            List<String> lines = Files.readAllLines(tsv, StandardCharsets.UTF_8);
            Map<String, Integer> seen = new LinkedHashMap<>();
            List<String> badSpecs = new ArrayList<>();
            List<String> dups = new ArrayList<>();
            List<String> empty = new ArrayList<>();
            List<String> stale = new ArrayList<>();
            List<String> deniedSomewhere = new ArrayList<>();
            Map<String, List<String>> glossary = loadGlossary(dir.resolve("glossary.tsv"));
            List<String> termIssues = new ArrayList<>();
            int translated = 0;
            int lineNo = 0;
            for (String line : lines) {
                lineNo++;
                if (lineNo == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cols = line.split("\t", 3);
                if (cols.length < 2) {
                    System.out.println("  第 " + lineNo + " 行没有 TAB");
                    failed = true;
                    continue;
                }
                String en = cols[0], tr = cols[1].strip();
                if (seen.containsKey(en)) dups.add(en);
                seen.put(en, lineNo);
                if (tr.isEmpty()) {
                    empty.add(en);
                    continue;
                }
                translated++;
                if (!StringsTable.specsMatch(en, tr)) badSpecs.add(en + "  ->  " + tr);
                boolean matched = false, satisfied = false;
                for (Map.Entry<String, List<String>> g : glossary.entrySet()) {
                    if (!en.contains(g.getKey())) continue;
                    matched = true;
                    if (g.getValue().stream().anyMatch(tr::contains)) {
                        satisfied = true;
                        break;
                    }
                }
                if (matched && !satisfied) termIssues.add(en + "  ->  " + tr);
                if (jarLiterals != null && !jarLiterals.contains(en)) stale.add(en);
                for (Map.Entry<String, Set<String>> e : denyByClass.entrySet()) {
                    if (e.getValue().contains(en)) {
                        deniedSomewhere.add(en);
                        break;
                    }
                }
            }
            System.out.println("  条目 " + seen.size() + "，已翻译 " + translated + "，待填 " + empty.size());
            if (!badSpecs.isEmpty()) {
                failed = true;
                System.out.println("  占位符不一致（必须修）: " + badSpecs.size());
                badSpecs.stream().limit(5).forEach(x -> System.out.println("    " + x));
            }
            if (!dups.isEmpty()) {
                failed = true;
                System.out.println("  重复键: " + dups.size());
                dups.stream().limit(5).forEach(x -> System.out.println("    " + x));
            }
            if (jarLiterals != null && !stale.isEmpty()) {
                System.out.println("  本版 IGV 里已不存在（可删）: " + stale.size());
                stale.stream().limit(5).forEach(x -> System.out.println("    " + x));
            }
            if (!deniedSomewhere.isEmpty()) {
                System.out.println("  被禁译规则覆盖（部分类里不会生效）: " + deniedSomewhere.size());
            }
            if (!termIssues.isEmpty()) {
                System.out.println("  术语可能与 glossary.tsv 不一致（提示，不阻断）: " + termIssues.size());
                termIssues.stream().limit(5).forEach(x -> System.out.println("    " + x));
            }
        }
        if (failed) {
            System.out.println("\n校验失败");
            System.exit(1);
        }
        System.out.println("\n校验通过");
    }

    /** glossary.tsv：术语 &lt;TAB&gt; 首选译法 [&lt;TAB&gt; 其他可接受译法，用 / 分隔]。 */
    private static Map<String, List<String>> loadGlossary(Path file) throws IOException {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (!Files.isReadable(file)) return out;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", 3);
            if (p.length < 2 || p[0].isBlank() || p[1].isBlank()) continue;
            List<String> allowed = new ArrayList<>();
            allowed.add(p[1]);
            if (p.length >= 3) {
                for (String alt : p[2].split("/")) {
                    if (!alt.isBlank()) allowed.add(alt.strip());
                }
            }
            out.put(p[0], allowed);
        }
        // 长词优先：否则 "Track" 会先命中 "Track Hub"
        return out.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static Map<String, Set<String>> loadDeny(Path deny) throws IOException {
        Map<String, Set<String>> out = new HashMap<>();
        if (deny == null || !Files.isReadable(deny)) return out;
        for (String line : Files.readAllLines(deny, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", 2);
            if (p.length < 2) continue;
            out.computeIfAbsent(p[0], k -> new HashSet<>()).add(p[1]);
        }
        return out;
    }

    /** 供 coverage 复用：按区域统计（同一字符串只算一次，归入首次出现的区域）。 */
    static Map<String, int[]> coverageByArea(Path jar, Set<String> translated) throws IOException {
        Map<String, String> areaOf = new TreeMap<>();
        for (JarScan.Cls c : JarScan.classes(jar)) {
            if (!JarScan.isIgv(c.name())) continue;
            String area = JarScan.area(c.name());
            for (String s : JarScan.literals(c.bytes())) {
                if (Extract.looksLikeUi(s)) areaOf.putIfAbsent(s, area);
            }
        }
        Map<String, int[]> out = new TreeMap<>();
        for (Map.Entry<String, String> e : areaOf.entrySet()) {
            int[] v = out.computeIfAbsent(e.getValue(), k -> new int[2]);
            v[0]++;
            if (translated.contains(e.getKey())) v[1]++;
        }
        return out;
    }
}
