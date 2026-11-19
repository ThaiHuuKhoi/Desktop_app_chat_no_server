package com.datn.chatp2p.client.view;

import com.datn.chatp2p.client.util.LinkOpener;
import com.datn.chatp2p.client.view.MarkdownParser.Block;
import com.datn.chatp2p.client.view.MarkdownParser.CodeBlock;
import com.datn.chatp2p.client.view.MarkdownParser.Inline;
import com.datn.chatp2p.client.view.MarkdownParser.ListItem;
import com.datn.chatp2p.client.view.MarkdownParser.Paragraph;
import com.datn.chatp2p.client.view.MarkdownParser.Quote;
import com.datn.chatp2p.client.view.MarkdownParser.Style;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;

/** Dung node JavaFX tu ket qua {@link MarkdownParser} - kieu chu dat bang CSS (md-*) theo theme. */
final class MarkdownView {

    private MarkdownView() {
    }

    static Node render(String text) {
        VBox box = new VBox(2);
        box.getStyleClass().add("md-root");
        for (Block block : MarkdownParser.parse(text)) {
            box.getChildren().add(renderBlock(block));
        }
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node renderBlock(Block block) {
        if (block instanceof CodeBlock code) {
            Label label = new Label(code.code());
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            label.setMaxWidth(Double.MAX_VALUE);
            label.getStyleClass().add("md-code-block");
            return label;
        }
        TextFlow flow = new TextFlow();
        flow.setMinHeight(Region.USE_PREF_SIZE);
        if (block instanceof ListItem item) {
            flow.getChildren().add(text("•  ", List.of()));
            addInlines(flow, item.inlines());
        } else if (block instanceof Quote quote) {
            flow.getStyleClass().add("md-quote");
            addInlines(flow, quote.inlines());
        } else if (block instanceof Paragraph paragraph) {
            if (paragraph.inlines().isEmpty()) {
                flow.getChildren().add(text(" ", List.of())); // dong trong giu chieu cao
            }
            addInlines(flow, paragraph.inlines());
        }
        return flow;
    }

    private static void addInlines(TextFlow flow, List<Inline> inlines) {
        for (Inline inline : inlines) {
            if (inline.link() != null) {
                Hyperlink link = new Hyperlink(inline.text());
                link.getStyleClass().add("md-link");
                link.setTooltip(new Tooltip("Mở trong trình duyệt: " + inline.link()));
                link.setOnAction(event -> LinkOpener.open(inline.link()));
                flow.getChildren().add(link);
            } else {
                flow.getChildren().add(text(inline.text(), inline.styles()));
            }
        }
    }

    private static Text text(String value, java.util.Collection<Style> styles) {
        Text text = new Text(value);
        text.getStyleClass().add("md-text");
        for (Style style : styles) {
            text.getStyleClass().add("md-" + style.name().toLowerCase());
        }
        return text;
    }
}
