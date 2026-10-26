package org.igvlingo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 改写 class 文件常量池里的「字符串字面量」。
 *
 * 三条纪律：
 *  1) 只动 CONSTANT_String，不动 CONSTANT_Utf8 —— 后者可能同时是字段名/方法名/类名；
 *  2) 译文作为新 Utf8 项追加到常量池末尾，String 项改指向它，旧项原样保留；
 *  3) class 内部引用全是常量池下标、没有绝对文件偏移，所以常量池变长是安全的。
 */
public final class ClassPatcher {

    public static final class Result {
        public final byte[] bytes;
        public final List<String> hits;
        public final int added;

        Result(byte[] bytes, List<String> hits, int added) {
            this.bytes = bytes;
            this.hits = hits;
            this.added = added;
        }
    }

    private ClassPatcher() {
    }

    public static Result patch(byte[] b, StringsTable table, String className) {
        if (b.length < 10 || (b[0] & 0xFF) != 0xCA || (b[1] & 0xFF) != 0xFE) return null;

        int cpCount = u2(b, 8);
        int[] start = new int[cpCount];
        int[] end = new int[cpCount];
        int[] tag = new int[cpCount];
        String[] utf = new String[cpCount];
        int[] strRef = new int[cpCount];

        int pos = 10;
        for (int i = 1; i < cpCount; i++) {
            start[i] = pos;
            if (pos + 1 > b.length) return null;
            int t = b[pos] & 0xFF;
            tag[i] = t;
            switch (t) {
                case 1: {
                    int len = u2(b, pos + 1);
                    utf[i] = decodeModifiedUtf8(b, pos + 3, len);
                    pos += 3 + len;
                    break;
                }
                case 3:
                case 4:
                    pos += 5;
                    break;
                case 5:
                case 6:
                    pos += 9;
                    end[i] = pos;
                    i++;                     // 8 字节常量占两个槽位
                    tag[i] = 0;
                    start[i] = pos;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    if (t == 8) strRef[i] = u2(b, pos + 1);
                    pos += 3;
                    break;
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    pos += 5;
                    break;
                case 15:
                    pos += 4;
                    break;
                default:
                    return null;
            }
            end[i] = pos;
        }
        int cpEnd = pos;
        if (cpEnd > b.length) return null;

        Map<Integer, Integer> strEntryToNewIdx = new LinkedHashMap<>();
        Map<Integer, String> newIdxText = new LinkedHashMap<>();
        List<String> hits = new ArrayList<>();
        int nextIdx = cpCount;
        for (int i = 1; i < cpCount; i++) {
            if (tag[i] != 8) continue;
            int u = strRef[i];
            if (u <= 0 || u >= cpCount || utf[u] == null) continue;
            if (table.denied(className, utf[u])) continue;
            String tr = table.translate(utf[u]);
            if (tr == null) continue;
            strEntryToNewIdx.put(i, nextIdx);
            newIdxText.put(nextIdx, tr);
            nextIdx++;
            hits.add(utf[u]);
        }
        if (hits.isEmpty()) return null;

        ByteArrayOutputStream bos = new ByteArrayOutputStream(b.length + 256);
        bos.write(b, 0, 8);                             // magic + minor + major
        writeU2(bos, cpCount + newIdxText.size());

        for (int i = 1; i < cpCount; i++) {
            if (tag[i] == 0) continue;                   // 8 字节常量的第二个槽位
            Integer mapped = tag[i] == 8 ? strEntryToNewIdx.get(i) : null;
            if (mapped != null) {
                bos.write(8);
                writeU2(bos, mapped);
            } else {
                bos.write(b, start[i], end[i] - start[i]);
            }
        }
        for (String tr : newIdxText.values()) {
            writeUtf8(bos, tr);
        }
        bos.write(b, cpEnd, b.length - cpEnd);
        return new Result(bos.toByteArray(), hits, newIdxText.size());
    }

    private static void writeU2(ByteArrayOutputStream bos, int v) {
        bos.write((v >>> 8) & 0xFF);
        bos.write(v & 0xFF);
    }

    private static void writeUtf8(ByteArrayOutputStream bos, String s) {
        byte[] bytes = encodeModifiedUtf8(s);
        bos.write(1);
        writeU2(bos, bytes.length);
        bos.write(bytes, 0, bytes.length);
    }

    private static int u2(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static String decodeModifiedUtf8(byte[] b, int off, int len) {
        try {
            return new DataInputStream(new ByteArrayInputStream(b, off, len)).readUTF();
        } catch (IOException e) {
            return new String(b, off, len, StandardCharsets.UTF_8);
        }
    }

    static byte[] encodeModifiedUtf8(String s) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(s.length() * 3);
        try {
            new DataOutputStream(bos).writeUTF(s);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        byte[] all = bos.toByteArray();
        byte[] out = new byte[all.length - 2];
        System.arraycopy(all, 2, out, 0, out.length);
        return out;
    }
}
