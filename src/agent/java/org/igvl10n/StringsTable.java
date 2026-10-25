package org.igvl10n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 一个语言的词表 + 禁译规则。
 *
 * strings.tsv 每行：英文原文 &lt;TAB&gt; 译文 [&lt;TAB&gt; 备注]，# 开头为注释。
 * deny_by_class.tsv 每行：类名 &lt;TAB&gt; 字面量，类名为 * 表示所有类。
 *
 * 载入时会校验 %s/%d 占位符，不一致的条目直接跳过（宁可少翻一句，不能让 IGV 运行时抛
 * MissingFormatArgumentException）。
 */
public final class StringsTable {

    private final String locale;
    private final Map<String, String> map = new LinkedHashMap<>();
    private final Map<String, List<String>> rejected = new LinkedHashMap<>();
    private final Map<String, Set<String>> denyByClass = new HashMap<>();
    private final Set<String> denyAll = new HashSet<>();

    private StringsTable(String locale) {
        this.locale = locale;
    }

    public static StringsTable load(Path localeDir, Path denyFile) throws IOException {
        StringsTable t = new StringsTable(localeDir.getFileName().toString());
        Path strings = localeDir.resolve("strings.tsv");
        if (!Files.isReadable(strings)) {
            throw new IOException("找不到词表: " + strings);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Files.newInputStream(strings), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                first = false;
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cols = line.split("\t", 3);
                if (cols.length < 2) continue;
                String en = cols[0];
                String zh = cols[1].strip();
                if (zh.isEmpty() || zh.equals(en)) continue;
                if (!specsMatch(en, zh)) {
                    t.rejected.computeIfAbsent(en, k -> new ArrayList<>()).add(zh);
                    continue;
                }
                t.map.put(en, zh);
            }
        }
        if (denyFile != null && Files.isReadable(denyFile)) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(denyFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#") || line.startsWith("class\t")) continue;
                    int tab = line.indexOf('\t');
                    if (tab <= 0) continue;
                    String cls = line.substring(0, tab).trim();
                    String lit = line.substring(tab + 1);
                    if (cls.isEmpty() || lit.isEmpty()) continue;
                    if ("*".equals(cls)) {
                        t.denyAll.add(lit);
                    } else {
                        t.denyByClass.computeIfAbsent(cls, k -> new HashSet<>()).add(lit);
                    }
                }
            }
        }
        return t;
    }

    /** 该字面量在此类（含内部类）里是否禁译。 */
    public boolean denied(String className, String literal) {
        if (denyAll.contains(literal)) return true;
        if (denyByClass.isEmpty()) return false;
        Set<String> exact = denyByClass.get(className);
        if (exact != null && exact.contains(literal)) return true;
        int dollar = className.indexOf('$');
        if (dollar > 0) {
            Set<String> outer = denyByClass.get(className.substring(0, dollar));
            return outer != null && outer.contains(literal);
        }
        return false;
    }

    public String translate(String en) {
        return map.get(en);
    }

    public int size() {
        return map.size();
    }

    public String locale() {
        return locale;
    }

    public int denyRules() {
        int n = denyAll.size();
        for (Set<String> s : denyByClass.values()) n += s.size();
        return n;
    }

    public Map<String, List<String>> rejected() {
        return rejected;
    }

    /**
     * 占位符一致性：%s/%d/%f 等格式符 + IGV 私有的 \u0001 占位符，
     * 数量与类型必须两边一致，否则运行时会抛格式异常或丢参数。
     */
    public static boolean specsMatch(String a, String b) {
        if (count(a, '\u0001') != count(b, '\u0001')) return false;
        return specList(a).equals(specList(b));
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static Map<Character, Integer> specList(String s) {
        Map<Character, Integer> m = new TreeMap<>();
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '%' && s.charAt(i + 1) != '%') {
                m.merge(s.charAt(i + 1), 1, Integer::sum);
            }
        }
        return m;
    }

    /** 语言回退链：zh_CN -> zh -> en（en 即原文，不需要词表）。 */
    public static List<String> fallbacks(String locale) {
        List<String> out = new ArrayList<>();
        out.add(locale);
        int us = locale.indexOf('_');
        if (us > 0) out.add(locale.substring(0, us));
        return out;
    }

    /** 在 locales/ 下挑一个可用语言：显式指定 > 系统语言 > 第一个可用。 */
    public static String resolveLocale(Path localesDir, String explicit) {
        List<String> available = new ArrayList<>();
        if (Files.isDirectory(localesDir)) {
            try (var s = Files.list(localesDir)) {
                s.filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .sorted(Comparator.naturalOrder())
                        .forEach(available::add);
            } catch (IOException ignored) {
                // 读不到就当没有
            }
        }
        if (explicit != null && !explicit.isBlank()) {
            for (String c : fallbacks(explicit.trim())) {
                if (available.contains(c)) return c;
            }
            return null;
        }
        String sys = java.util.Locale.getDefault().toString();
        for (String c : fallbacks(sys)) {
            if (available.contains(c)) return c;
        }
        String lang = java.util.Locale.getDefault().getLanguage();
        for (String c : available) {
            if (c.equals(lang) || c.startsWith(lang + "_")) return c;
        }
        return available.isEmpty() ? null : available.get(0);
    }
}
