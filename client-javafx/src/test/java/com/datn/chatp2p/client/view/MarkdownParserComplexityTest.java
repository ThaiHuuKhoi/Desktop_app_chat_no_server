package com.datn.chatp2p.client.view;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Hoi quy van de 8 (docs/Bao-cao-bao-mat-13-van-de.md): chuoi nhieu dau mo nghieng khong co dau dong
 * truoc day lam findClosing quet lai ca dong cho MOI dau mo (O(n^2)) tren luong JavaFX.
 */
class MarkdownParserComplexityTest {

    @Test
    void manyUnmatchedEmphasisOpenersParseQuickly() {
        String hostile = "*a ".repeat(200_000); // ~600k ky tu - truoc khi sua mat rat lau
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            List<MarkdownParser.Inline> inlines = MarkdownParser.parseInline(hostile);
            int total = inlines.stream().mapToInt(inline -> inline.text().length()).sum();
            assertEquals(hostile.length(), total, "Khong co cap dau nao hop le -> giu nguyen van ban");
        });
    }

    @Test
    void memoDoesNotChangeNormalEmphasis() {
        // "*a *b*": dau '*' sau khoang trang khong dong duoc, dau sau 'b' dong -> nghieng "a *b".
        assertEquals(List.of(
                        new MarkdownParser.Inline("a *b", Set.of(MarkdownParser.Style.ITALIC), null),
                        new MarkdownParser.Inline(" c", Set.of(), null)),
                MarkdownParser.parseInline("*a *b* c"));
    }
}
