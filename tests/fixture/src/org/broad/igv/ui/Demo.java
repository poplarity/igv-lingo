package org.broad.igv.ui;

/**
 * 自检测试用的假 IGV 类：
 *  - "File"   纯展示字符串，应该被翻译
 *  - "Regions" 同时用于 Class.forName（结构用途），应该被禁译
 */
public final class Demo {

    public static String label() {
        return "File";
    }

    public static String tag() {
        return "Regions";
    }

    public static boolean same(String s) {
        return s.equals("Regions");
    }

    public static Class<?> ref() throws Exception {
        return Class.forName("Regions");
    }
}
