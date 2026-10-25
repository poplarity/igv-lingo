package org.igvl10n;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IGV 多语言 agent。
 *
 * 安装后由 ~/.igv/java_arguments 里的一行触发：
 *
 *     -javaagent:~/.igv/l10n/igv-l10n-agent.jar=/Users/you/.igv/l10n
 *
 * 目录结构（~/.igv/l10n）：
 *     locales/zh_CN/strings.tsv   词表
 *     deny_by_class.tsv           禁译规则（按 IGV 版本生成）
 *     igv-l10n.log                运行日志
 *
 * 语言选择：-Digv.l10n.locale=zh_CN > 系统语言 > 第一个可用语言。
 */
public final class Agent {

    private static final String[] PREFIXES = {"org/broad/igv/", "org/igv/"};

    private static final AtomicInteger CLASSES = new AtomicInteger();
    private static final AtomicInteger STRINGS = new AtomicInteger();
    private static Path logFile;
    private static volatile boolean verbose;

    public static void premain(String agentArgs, Instrumentation inst) {
        Path home = resolveHome(agentArgs);
        logFile = Paths.get(System.getProperty("igv.l10n.log",
                home.resolve("igv-l10n.log").toString()));
        verbose = Boolean.getBoolean("igv.l10n.verbose");

        Path locales = home.resolve("locales");
        String requested = System.getProperty("igv.l10n.locale");
        String locale = StringsTable.resolveLocale(locales, requested);
        if (locale == null) {
            log("[igv-l10n] 没有可用语言包（" + locales + "）"
                    + (requested == null ? "" : "，指定语言 " + requested + " 不可用")
                    + "，不汉化");
            return;
        }
        if (requested != null && !requested.isBlank() && !requested.equals(locale)) {
            log("[igv-l10n] 指定语言 " + requested + " 不可用，改用 " + locale);
        }

        StringsTable table;
        try {
            table = StringsTable.load(locales.resolve(locale), home.resolve("deny_by_class.tsv"));
        } catch (IOException e) {
            log("[igv-l10n] 词表读取失败: " + e);
            return;
        }
        log("[igv-l10n] 语言 " + locale + "，词条 " + table.size()
                + "，禁译规则 " + table.denyRules()
                + (table.rejected().isEmpty() ? "" : "，占位符不匹配被跳过 " + table.rejected().size())
                + "；home=" + home);
        if (table.size() == 0) return;

        inst.addTransformer(new Localizer(table), false);
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                log("[igv-l10n] 结束：改写 " + CLASSES.get() + " 个类 / " + STRINGS.get() + " 处字符串")));
    }

    private static Path resolveHome(String agentArgs) {
        if (agentArgs != null && !agentArgs.isBlank()) {
            String a = agentArgs.trim();
            if (a.startsWith("=")) a = a.substring(1);
            return Paths.get(a);
        }
        String prop = System.getProperty("igv.l10n.home");
        if (prop != null && !prop.isBlank()) return Paths.get(prop);
        return Paths.get(System.getProperty("user.home"), ".igv", "l10n");
    }

    static boolean interesting(String name) {
        if (name == null || name.indexOf('/') < 0) return false;
        if (name.endsWith("module-info") || name.endsWith("package-info")) return false;
        for (String p : PREFIXES) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }

    static void log(String msg) {
        if (logFile == null) return;
        String line = LocalTime.now().withNano(0) + " " + msg + System.lineSeparator();
        try {
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 日志失败不能影响 IGV
        }
    }

    private static final class Localizer implements ClassFileTransformer {
        private final StringsTable table;

        Localizer(StringsTable table) {
            this.table = table;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                                ProtectionDomain pd, byte[] bytes) {
            if (bytes == null || !interesting(className)) return null;
            try {
                ClassPatcher.Result r = ClassPatcher.patch(bytes, table, className);
                if (r == null) return null;
                CLASSES.incrementAndGet();
                STRINGS.addAndGet(r.hits.size());
                if (verbose) log("[igv-l10n] " + className + " : " + r.hits.size() + " 处");
                return r.bytes;
            } catch (Throwable t) {
                log("[igv-l10n] 跳过 " + className + " : " + t);
                return null;
            }
        }
    }

    /** 供工具复用：判断某个类名是否属于 IGV。 */
    public static List<String> prefixes() {
        return List.of(PREFIXES);
    }
}
