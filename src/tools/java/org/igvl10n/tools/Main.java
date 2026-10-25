package org.igvl10n.tools;

import java.util.Arrays;

/** 命令行入口：extract / audit / check / coverage / dryrun。 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        String cmd = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (cmd) {
            case "extract" -> Extract.run(rest);
            case "audit" -> Audit.run(rest);
            case "check" -> Check.run(rest);
            case "coverage" -> Coverage.run(rest);
            case "dryrun" -> DryRun.run(rest);
            case "version" -> System.out.println("igv-l10n tools " + version());
            default -> {
                usage();
                System.exit(2);
            }
        }
    }

    private static String version() {
        String v = Main.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    private static void usage() {
        System.out.println("""
                igv-l10n 工具集

                  extract  --jar igv.jar [--out candidates.tsv] [--skeleton locales/xx_YY]
                  audit    --jar igv.jar [--out igv/2.19.1]
                  check    [--locales locales] [--jar igv.jar] [--deny deny_by_class.tsv]
                  coverage --jar igv.jar --locale locales/zh_CN [--top 30]
                  dryrun   --jar igv.jar --locale locales/zh_CN [--deny deny_by_class.tsv] [--out DIR]
                """);
    }
}
