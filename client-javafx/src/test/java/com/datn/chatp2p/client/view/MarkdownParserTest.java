package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.view.MarkdownParser.Block;
import com.datn.chatp2p.client.view.MarkdownParser.CodeBlock;
import com.datn.chatp2p.client.view.MarkdownParser.Inline;
import com.datn.chatp2p.client.view.MarkdownParser.ListItem;
import com.datn.chatp2p.client.view.MarkdownParser.Paragraph;
import com.datn.chatp2p.client.view.MarkdownParser.Quote;
import com.datn.chatp2p.client.view.MarkdownParser.Style;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarkdownParserTest {

    private static Inline plain(String text) {
        return new Inline(text, Set.of(), null);
    }

    private static Inline styled(String text, Style... styles) {
        return new Inline(text, Set.of(styles), null);
    }

    @Test
    void plainTextStaysPlain() {
        assertEquals(List.of(plain("xin chào 2*3 = 6")), MarkdownParser.parseInline("xin chào 2*3 = 6"));
    }

    @Test
    void boldItalicStrikeAndCode() {
        assertEquals(List.of(styled("đậm", Style.BOLD), plain(" và "), styled("nghiêng", Style.ITALIC),
                        plain(" "), styled("gạch", Style.STRIKE), plain(" "), styled("x = 1", Style.CODE)),
                MarkdownParser.parseInline("**đậm** và *nghiêng* ~~gạch~~ `x = 1`"));
    }

    @Test
    void nestedEmphasisAndCodeIsLiteral() {
        assertEquals(List.of(styled("a ", Style.BOLD), styled("b", Style.BOLD, Style.ITALIC)),
                MarkdownParser.parseInline("**a _b_**"));
        assertEquals(List.of(styled("**khong dam**", Style.CODE)), MarkdownParser.parseInline("`**khong dam**`"));
    }

    @Test
    void snakeCaseAndUnclosedMarkersAreNotFormatted() {
        assertEquals(List.of(plain("bien my_var_name va * le")), MarkdownParser.parseInline("bien my_var_name va * le"));
        assertEquals(List.of(plain("**chua dong")), MarkdownParser.parseInline("**chua dong"));
    }

    @Test
    void backslashEscapesMarker() {
        assertEquals(List.of(plain("*khong nghieng*")), MarkdownParser.parseInline("\\*khong nghieng\\*"));
    }

    @Test
    void urlsBecomeLinksWithoutTrailingPunctuation() {
        List<Inline> inlines = MarkdownParser.parseInline("xem https://example.com/a?b=1, nhé");
        assertEquals(3, inlines.size());
        assertEquals("https://example.com/a?b=1", inlines.get(1).link());
        assertEquals(plain(", nhé"), inlines.get(2));
    }

    @Test
    void labelledLinksAreNotSupportedSoUrlStaysVisible() {
        List<Inline> inlines = MarkdownParser.parseInline("[ngân hàng](https://evil.example)");
        assertEquals("[ngân hàng](", inlines.get(0).text());
        assertEquals("https://evil.example", inlines.get(1).link(), "URL that phai hien ra, khong bi giau sau chu");
    }

    @Test
    void javascriptSchemeIsNotALink() {
        MarkdownParser.parseInline("javascript:alert(1)").forEach(inline -> assertNull(inline.link()));
    }

    @Test
    void blocksCodeListQuoteAndLineBreaks() {
        List<Block> blocks = MarkdownParser.parse("dòng 1\n- mục\n> trích\n```java\nint x;\n  y();\n```\ndòng cuối");
        assertInstanceOf(Paragraph.class, blocks.get(0));
        assertInstanceOf(ListItem.class, blocks.get(1));
        assertInstanceOf(Quote.class, blocks.get(2));
        assertEquals(new CodeBlock("int x;\n  y();"), blocks.get(3));
        assertInstanceOf(Paragraph.class, blocks.get(4));
        assertEquals(5, blocks.size());
    }

    @Test
    void unclosedFenceTakesRestAsCode() {
        assertEquals(List.of(new Paragraph(List.of(plain("a"))), new CodeBlock("b\nc")),
                MarkdownParser.parse("a\n```\nb\nc"));
    }
}
