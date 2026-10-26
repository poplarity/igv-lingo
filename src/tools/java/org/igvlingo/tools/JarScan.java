package org.igvlingo.tools;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 读 igv.jar（或解压后的目录）里的 class，抽常量池字符串。 */
public final class JarScan {

    public record Cls(String name, byte[] bytes) {
    }

    private JarScan() {
    }

    public static List<Cls> classes(Path jar) throws IOException {
        List<Cls> out = new ArrayList<>();
        if (Files.isDirectory(jar)) {
            try (Stream<Path> s = Files.walk(jar)) {
                for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) {
                    String name = jar.relativize(p).toString().replace('\\', '/');
                    out.add(new Cls(name.substring(0, name.length() - 6), Files.readAllBytes(p)));
                }
            }
            return out;
        }
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                try (InputStream in = zf.getInputStream(e)) {
                    out.add(new Cls(n.substring(0, n.length() - 6), in.readAllBytes()));
                }
            }
        }
        return out;
    }

    public static boolean isIgv(String className) {
        return className.startsWith("org/broad/igv/") || className.startsWith("org/igv/");
    }

    /** 用于覆盖率统计的粗分区：org/broad/igv/ui/panel/X -> "ui/panel"。 */
    public static String area(String className) {
        String prefix = className.startsWith("org/broad/igv/") ? "org/broad/igv/" : "org/igv/";
        String rest = className.substring(prefix.length());
        String[] parts = rest.split("/");
        if (parts.length >= 2) return parts[0] + "/" + parts[1];
        return parts[0];
    }

    /**
     * 常量池里的字符串字面量（CONSTANT_String 指向的 Utf8）。
     * 必须分两遍：CONSTANT_String 可能前向引用后面才出现的 Utf8。
     */
    public static Set<String> literals(byte[] b) {
        Set<String> out = new LinkedHashSet<>();
        if (b.length < 10) return out;
        int cpCount = ((b[8] & 0xFF) << 8) | (b[9] & 0xFF);
        String[] utf = new String[cpCount];
        int[] refs = new int[cpCount];
        boolean[] isStr = new boolean[cpCount];
        int pos = 10;
        try {
            for (int i = 1; i < cpCount; i++) {
                int tag = b[pos++] & 0xFF;
                switch (tag) {
                    case 1: {
                        int len = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
                        utf[i] = readUtf(b, pos + 2, len);
                        pos += 2 + len;
                        break;
                    }
                    case 3:
                    case 4:
                        pos += 4;
                        break;
                    case 5:
                    case 6:
                        pos += 8;
                        i++;
                        break;
                    case 7:
                    case 16:
                    case 19:
                    case 20:
                        pos += 2;
                        break;
                    case 8:
                        refs[i] = ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
                        isStr[i] = true;
                        pos += 2;
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        pos += 4;
                        break;
                    case 15:
                        pos += 3;
                        break;
                    default:
                        return out;
                }
            }
            for (int i = 1; i < cpCount; i++) {
                if (isStr[i]) {
                    int idx = refs[i];
                    if (idx > 0 && idx < cpCount && utf[idx] != null) out.add(utf[idx]);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            return out;
        }
        return out;
    }

    private static String readUtf(byte[] b, int off, int len) {
        try {
            return new DataInputStream(new ByteArrayInputStream(b, off, len)).readUTF();
        } catch (IOException e) {
            return new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
