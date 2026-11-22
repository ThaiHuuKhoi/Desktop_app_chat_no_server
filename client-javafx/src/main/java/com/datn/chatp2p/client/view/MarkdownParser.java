package com.datn.chatp2p.client.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Phan tich 1 tap con Markdown cho tin nhan chat (Tai-lieu-ky-thuat.md muc E.8.1):
 *
 * <ul>
 *   <li>Khoi: dong thuong, khoi code {@code ```...```}, danh sach {@code - }/{@code * }/{@code + },
 *       trich dan {@code > }. Moi dong giu nguyen xuong dong nhu nguoi go.</li>
 *   <li>Trong dong: {@code **dam**}, {@code *nghieng*}/{@code _nghieng_}, {@code ~~gach~~},
 *       {@code `code`}, URL http(s) tu thanh link. {@code \*} de go ky tu that.</li>
 * </ul>
 *
 * <p><b>Co y KHONG ho tro</b> {@code [chu](url)}: link luon hien dung URL that, khong the gia
 * dang 1 dong chu vo hai (chong lua dao). Khong co HTML - noi dung chi la chu.
 */
final class MarkdownParser {

    enum Style { BOLD, ITALIC, STRIKE, CODE }

    /** 1 doan chu cung kieu; {@code link} khac null neu la URL. */
    record Inline(String text, Set<Style> styles, String link) {
        Inline {
            styles = styles.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(styles));
        }
    }

    sealed interface Block {
    }

    record Paragraph(List<Inline> inlines) implements Block {
    }

    record ListItem(List<Inline> inlines) implements Block {
    }

    record Quote(List<Inline> inlines) implements Block {
    }

    record CodeBlock(String code) implements Block {
    }

    private MarkdownParser() {
    }

    static List<Block> parse(String text) {
        List<Block> blocks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return blocks;
        }
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.startsWith("```")) {
                StringBuilder code = new StringBuilder();
                String afterFence = trimmed.substring(3);
                int closeOnSameLine = afterFence.indexOf("```");
                if (closeOnSameLine >= 0) { // ```code``` tren 1 dong
                    blocks.add(new CodeBlock(afterFence.substring(0, closeOnSameLine)));
                    continue;
                }
                // Phan sau ``` tren dong mo la ten ngon ngu (vd ```java) - bo qua.
                int j = i + 1;
                while (j < lines.length && !lines[j].strip().startsWith("```")) {
                    if (code.length() > 0) {
                        code.append('\n');
                    }
                    code.append(lines[j]);
                    j++;
                }
                blocks.add(new CodeBlock(code.toString()));
                i = j; // bo qua dong dong ``` (hoac het van ban neu quen dong)
            } else if (isListItem(line)) {
                blocks.add(new ListItem(parseInline(line.stripLeading().substring(2))));
            } else if (line.stripLeading().startsWith(">")) {
                String quoted = line.stripLeading().substring(1);
                blocks.add(new Quote(parseInline(quoted.startsWith(" ") ? quoted.substring(1) : quoted)));
            } else {
                blocks.add(new Paragraph(parseInline(line)));
            }
        }
        return blocks;
    }

    private static boolean isListItem(String line) {
        String s = line.stripLeading();
        return s.length() >= 2 && "-*+".indexOf(s.charAt(0)) >= 0 && s.charAt(1) == ' ';
    }

    static List<Inline> parseInline(String text) {
        List<Inline> out = new ArrayList<>();
        parseInline(text, EnumSet.noneOf(Style.class), out);
        return out;
    }

    private static void parseInline(String s, EnumSet<Style> active, List<Inline> out) {
        StringBuilder plain = new StringBuilder();
        // Bao mat (chong treo UI): nho vi tri nho nhat ma tu do KHONG con dau dong hop le cho tung
        // marker. Thieu cai nay, moi dau mo khong co cap lai quet het phan con lai cua dong -> O(n^2)
        // voi chuoi kieu "*a *a *a ..." va 1 tin dai lam dung luong JavaFX.
        java.util.Map<String, Integer> noCloserFrom = new java.util.HashMap<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);

            if (c == '\\' && i + 1 < s.length() && "\\`*_~>-+#".indexOf(s.charAt(i + 1)) >= 0) {
                plain.append(s.charAt(i + 1));
                i += 2;
                continue;
            }

            if (c == '`') {
                int close = s.indexOf('`', i + 1);
                if (close > i + 1) {
                    flush(plain, active, out);
                    EnumSet<Style> code = EnumSet.copyOf(active);
                    code.add(Style.CODE);
                    out.add(new Inline(s.substring(i + 1, close), code, null));
                    i = close + 1;
                    continue;
                }
            }

            if (s.startsWith("**", i) || s.startsWith("~~", i)) {
                String marker = s.substring(i, i + 2);
                int close = findClosing(s, marker, i + 2, noCloserFrom);
                if (close > 0) {
                    flush(plain, active, out);
                    EnumSet<Style> nested = EnumSet.copyOf(active);
                    nested.add(marker.equals("**") ? Style.BOLD : Style.STRIKE);
                    parseInline(s.substring(i + 2, close), nested, out);
                    i = close + 2;
                    continue;
                }
            }

            if ((c == '*' || c == '_') && canOpenEmphasis(s, i)) {
                int close = findClosing(s, String.valueOf(c), i + 1, noCloserFrom);
                if (close > 0 && (c == '*' || isWordBoundary(s, close + 1))) {
                    flush(plain, active, out);
                    EnumSet<Style> nested = EnumSet.copyOf(active);
                    nested.add(Style.ITALIC);
                    parseInline(s.substring(i + 1, close), nested, out);
                    i = close + 1;
                    continue;
                }
            }

            if ((s.startsWith("http://", i) || s.startsWith("https://", i)) && isWordBoundary(s, i - 1)) {
                int end = i;
                while (end < s.length() && !Character.isWhitespace(s.charAt(end))) {
                    end++;
                }
                while (end > i && ".,;:!?)]}'\"".indexOf(s.charAt(end - 1)) >= 0) {
                    end--; // dau cau dung sau link khong thuoc link
                }
                String url = s.substring(i, end);
                if (url.length() > "https://".length()) {
                    flush(plain, active, out);
                    out.add(new Inline(url, active, url));
                    i = end;
                    continue;
                }
            }

            plain.append(c);
            i++;
        }
        flush(plain, active, out);
    }

    /** Dau mo: ky tu sau khong phai khoang trang; voi '_' thi truoc do phai la ranh gioi tu (snake_case khong bi nghieng). */
    private static boolean canOpenEmphasis(String s, int i) {
        if (i + 1 >= s.length() || Character.isWhitespace(s.charAt(i + 1))) {
            return false;
        }
        return s.charAt(i) == '*' || isWordBoundary(s, i - 1);
    }

    /**
     * Nhu {@link #findClosing(String, String, int)} nhung dung {@code memo}: neu tu vi tri f da biet
     * khong co dau dong thi moi {@code from >= f} cung khong co (tap ung vien chi thu hep lai).
     */
    private static int findClosing(String s, String marker, int from, java.util.Map<String, Integer> memo) {
        Integer known = memo.get(marker);
        if (known != null && from >= known) {
            return -1;
        }
        int close = findClosing(s, marker, from);
        if (close < 0) {
            memo.merge(marker, from, Math::min);
        }
        return close;
    }

    /** Vi tri dau dong hop le dau tien sau {@code from}: khong rong, ky tu truoc no khong phai khoang trang. */
    private static int findClosing(String s, String marker, int from) {
        int close = s.indexOf(marker, from);
        while (close > 0) {
            if (close > from && !Character.isWhitespace(s.charAt(close - 1))) {
                return close;
            }
            close = s.indexOf(marker, close + 1);
        }
        return -1;
    }

    private static boolean isWordBoundary(String s, int index) {
        return index < 0 || index >= s.length() || !Character.isLetterOrDigit(s.charAt(index));
    }

    private static void flush(StringBuilder plain, EnumSet<Style> active, List<Inline> out) {
        if (plain.length() > 0) {
            out.add(new Inline(plain.toString(), active, null));
            plain.setLength(0);
        }
    }
}
