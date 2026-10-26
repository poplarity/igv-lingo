package org.igvlingo;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 运行时文本替换。
 *
 * 大部分界面文字是 class 里的字符串常量，加载时就能改（ClassPatcher）。
 * 但少数界面是"数据驱动"的，文字来自资源文件而不是常量——典型的是
 * 首选项对话框，标签读自 igv.jar 里的 preferences.tab。
 *
 * 这些文字没法在 class 层面改，所以退一步：窗口第一次显示时，
 * 遍历组件树，把文字和词表里有中文的控件换掉。只替换完全匹配的条目，
 * 不影响布局与逻辑，也不写回任何文件。
 */
final class UiSweep {

    private final StringsTable table;
    private final boolean enabled;

    UiSweep(StringsTable table) {
        this.table = table;
        this.enabled = Boolean.parseBoolean(System.getProperty("igv.lingo.uiSweep", "true"));
    }

    void start() {
        if (!enabled) return;
        Thread t = new Thread(this::loop, "igv-lingo-ui");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        Set<Window> seen = Collections.newSetFromMap(new WeakHashMap<>());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                return;
            }
            try {
                Window[] windows = Window.getWindows();
                for (final Window w : windows) {
                    if (w.isVisible() && seen.add(w)) {
                        // 组件是陆续加进去的，多扫几遍再收手
                        for (int i = 0; i < 6; i++) {
                            final int delay = i * 300;
                            new Thread(() -> {
                                try { Thread.sleep(delay); } catch (InterruptedException e) { return; }
                                SwingUtilities.invokeLater(() -> translate(w));
                            }, "igv-lingo-ui-pass").start();
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 任何异常都不能影响 IGV
            }
        }
    }

    private String tr(String s) {
        if (s == null) return null;
        String zh = table.translate(s);
        if (zh == null) zh = table.translate(s.trim());
        return zh;
    }

    private void translate(Component c) {
        try {
            if (c instanceof AbstractButton) {
                AbstractButton b = (AbstractButton) c;
                String zh = tr(b.getText());
                if (zh != null) b.setText(zh);
            } else if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                String zh = tr(l.getText());
                if (zh != null) l.setText(zh);
            } else if (c instanceof JTabbedPane) {
                JTabbedPane tp = (JTabbedPane) c;
                for (int i = 0; i < tp.getTabCount(); i++) {
                    String zh = tr(tp.getTitleAt(i));
                    if (zh != null) tp.setTitleAt(i, zh);
                }
            }
            if (c instanceof JComponent) {
                JComponent jc = (JComponent) c;
                String zh = tr(jc.getToolTipText());
                if (zh != null) jc.setToolTipText(zh);
                Border border = jc.getBorder();
                if (border instanceof TitledBorder) {
                    TitledBorder tb = (TitledBorder) border;
                    String t = tr(tb.getTitle());
                    if (t != null) {
                        jc.setBorder(new TitledBorder(tb.getBorder(), t, tb.getTitleJustification(),
                                tb.getTitlePosition(), tb.getTitleFont(), tb.getTitleColor()));
                    }
                }
            }
            if (c instanceof Container) {
                for (Component child : ((Container) c).getComponents()) {
                    translate(child);
                }
            }
        } catch (Throwable ignored) {
            // 单个组件失败就跳过
        }
    }
}
