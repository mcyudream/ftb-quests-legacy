package com.feed_the_beast.ftbquests.texteditor;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TextEditorTest
{
    @Test void unchangedLegacyDescriptionsRetainEveryLineAndUnknownComponent()
    {
        for (List<String> lines : List.of(List.<String>of(), List.of(""), List.of("", ""),
                List.of("  &a文字  ", "", "{\"type\":\"future\",\"nested\":{\"list\":[1,2]}}", "", "")))
        {
            DescriptionDocument doc = new DescriptionDocument(lines);
            assertEquals(lines, doc.encode(doc.text(), doc.markdown()));
        }
    }

    @Test void editedLegacyDescriptionsKeepTrailingBlankLinesAndLiteralMarkup()
    {
        DescriptionDocument doc = new DescriptionDocument(List.of("old", ""));
        List<String> expected = List.of("**literal**", "  &b中文  ", "", "");
        assertEquals(expected, doc.encode(String.join("\n", expected), false));
    }

    @Test void markdownIsExplicitAndPreservesItsUnknownEnvelopeFields()
    {
        String original = " { \"type\":\"markdown\", \"text\":\"# Title\\n\\n\", \"addon\":{\"a\":[42]} } ";
        DescriptionDocument doc = new DescriptionDocument(List.of(original));
        assertTrue(doc.markdown()); assertEquals("# Title\n\n", doc.text());
        assertEquals(List.of(original), doc.encode(doc.text(), true));
        JsonObject changed = new JsonParser().parse(doc.encode("**new**\n", true).get(0)).getAsJsonObject();
        assertEquals("**new**\n", changed.get("text").getAsString());
        assertEquals(42, changed.getAsJsonObject("addon").getAsJsonArray("a").get(0).getAsInt());
        assertEquals(List.of("# Title", "", ""), doc.encode(doc.text(), false));
    }

    @Test void markdownRoundTripKeepsJsonUnicodeEscapesAndLargeText()
    {
        DescriptionDocument doc = new DescriptionDocument(List.of());
        String text = "# 中文\n\"\\&a§b${player:key}\"\n".repeat(3000) + "\n\n";
        List<String> saved = doc.encode(text, true);
        assertEquals(1, saved.size()); assertEquals(text, new DescriptionDocument(saved).text());
        assertNull(DescriptionDocument.markdownObject("{\"type\":\"markdown\",\"text\":null}"));
        assertNull(DescriptionDocument.markdownObject("{broken"));
    }

    @Test void editsUndoAndRedoRestoreTextAndSelection()
    {
        EditorBuffer buffer = new EditorBuffer("first\nsecond\n");
        buffer.select(6, false); buffer.select(12, true);
        buffer.replace("替换\n"); assertEquals("first\n替换\n\n", buffer.text());
        buffer.undo(); assertEquals("second", buffer.selected());
        buffer.redo(); assertEquals("first\n替换\n\n", buffer.text());
        buffer.undo(); buffer.replace("new"); assertFalse(buffer.canRedo());
    }

    @Test void formattingIsOneUndoStepAndDoesNotReplaceOtherLines()
    {
        EditorBuffer buffer = new EditorBuffer("before\nword\nafter\n");
        buffer.select(7, false); buffer.select(11, true); buffer.surround("**", "**", "text");
        assertEquals("before\n**word**\nafter\n", buffer.text()); assertEquals("word", buffer.selected());
        buffer.undo(); assertEquals("before\nword\nafter\n", buffer.text());
        buffer.prefixLine("- "); assertEquals("before\n- word\nafter\n", buffer.text());
    }

    @Test void backspaceRemovesWholeUnicodeCodePointsAndSelection()
    {
        EditorBuffer buffer = new EditorBuffer("a😀中");
        buffer.select(3, false); buffer.delete(true, false); assertEquals("a中", buffer.text());
        buffer.undo(); buffer.selectAll(); buffer.delete(false, true); assertEquals("", buffer.text());
        buffer.undo(); assertEquals("a😀中", buffer.text());
    }

    @Test void selectedMarkdownLinkCaptionKeepsOtherTextAndHasOneUndoStep()
    {
        String original = "{\"type\":\"future\",\"kept\":[1,2]}\n选择文字\n\n";
        EditorBuffer buffer = new EditorBuffer(original);
        int start = original.indexOf("选择文字"); buffer.select(start,false); buffer.select(start+4,true);
        buffer.insertMarkdownLink("ponder:pack:machine#pack:scene", "默认标题");
        assertEquals(original.replace("选择文字", "[选择文字](ponder:pack:machine#pack:scene)"),buffer.text());
        buffer.undo(); assertEquals(original,buffer.text()); assertEquals("选择文字",buffer.selected());
        buffer.redo(); assertTrue(buffer.text().contains("[选择文字](ponder:pack:machine#pack:scene)"));
    }

    @Test void generatedLinkCaptionWithMarkdownPunctuationRemainsPlainTextAndClickable()
    {
        String caption = "机器 [A](B) *标题* `演示` \\ 路径\n教程";
        EditorBuffer buffer = new EditorBuffer("");
        String target = "ponder:pack:machine#pack:scene";
        buffer.insertMarkdownLink(target,caption);
        var spans = QuestMarkdown.inline(buffer.text());
        assertEquals(caption.replace('\n',' '),spans.stream().map(QuestMarkdown.Span::text).collect(java.util.stream.Collectors.joining()));
        assertTrue(spans.stream().allMatch(s -> target.equals(s.link())&&!s.bold()&&!s.italic()&&!s.code()));
    }

    @Test void softWrapAndNewlinePositionsAllowAccurateMouseSelection()
    {
        SourceLayout layout = new SourceLayout("abcd\nef\n", 12, c -> 6);
        assertEquals(List.of(new SourceLayout.Row(0, 2), new SourceLayout.Row(2, 4), new SourceLayout.Row(5, 7), new SourceLayout.Row(8, 8)), layout.rows);
        assertEquals(1, layout.rowAt(2)); assertEquals(0, layout.xAt(2));
        assertEquals(3, layout.positionAt(1, 5)); assertEquals(4, layout.positionAt(1, 100));
        assertEquals(8, layout.positionAt(50, 0));
    }

    @Test void wrappedRowsAndMouseClicksNeverSplitSupplementaryHan()
    {
        SourceLayout layout = new SourceLayout("A𠀀B", 12, point -> 6);
        assertEquals(List.of(new SourceLayout.Row(0, 3), new SourceLayout.Row(3, 4)), layout.rows);
        for (int row = 0; row < layout.rows.size(); row++)
            for (int x = 0; x < 30; x++) assertNotEquals(2, layout.positionAt(row, x));
        assertEquals(6, layout.xAt(1));
    }

    @Test void selectionInsideSupplementaryHanKeepsCopyReplaceAndUndoWhole()
    {
        EditorBuffer buffer = new EditorBuffer("A𠀀B");
        buffer.select(2, false); buffer.select(3, true);
        assertEquals("𠀀", buffer.selected());
        buffer.replace("中"); assertEquals("A中B", buffer.text());
        buffer.undo(); assertEquals("A𠀀B", buffer.text()); assertEquals("𠀀", buffer.selected());
    }

    @Test void markdownBlocksCoverListsQuotesMediaTablesAndLiteralCode()
    {
        String text = "# 标题\n- 项目\n1. 步骤\n> 引用\n---\n![图](asset:demo.png)\n| A | B |\n| --- | :---: |\n| C\\|D | E |\n```zs\n\n**literal**\n```\n{\"type\":\"item\",\"item\":\"minecraft:stone\"}";
        var blocks = QuestMarkdown.parse(text);
        assertEquals(List.of(QuestMarkdown.Kind.HEADING, QuestMarkdown.Kind.LIST, QuestMarkdown.Kind.LIST, QuestMarkdown.Kind.QUOTE,
                QuestMarkdown.Kind.RULE, QuestMarkdown.Kind.IMAGE, QuestMarkdown.Kind.TABLE, QuestMarkdown.Kind.CODE, QuestMarkdown.Kind.COMPONENT), blocks.stream().map(QuestMarkdown.Block::kind).toList());
        assertEquals("C|D", blocks.get(6).rows().get(1).get(0));
        assertEquals("\n**literal**", blocks.get(7).text());
    }

    @Test void inlineStylesLinksEscapesAndCodeAreParsedWithoutChangingSource()
    {
        var spans = QuestMarkdown.inline("**粗体** *斜体* ~~划掉~~ `**code**` [打开](zs:guide/open) \\*literal\\*");
        assertTrue(spans.stream().anyMatch(s -> s.bold() && s.text().equals("粗体")));
        assertTrue(spans.stream().anyMatch(s -> s.italic() && s.text().equals("斜体")));
        assertTrue(spans.stream().anyMatch(s -> s.strike() && s.text().equals("划掉")));
        assertTrue(spans.stream().anyMatch(s -> s.code() && s.text().equals("**code**") && !s.bold()));
        assertTrue(spans.stream().anyMatch(s -> s.text().equals("打开") && s.link().equals("zs:guide/open")));
        assertTrue(spans.get(spans.size() - 1).text().endsWith("*literal*"));
        assertEquals("**unclosed", QuestMarkdown.inline("**unclosed").get(0).text());
    }

    @Test void editorAndDependencyControlsHaveBothEnglishAndChineseLabels() throws Exception
    {
        Properties en = language("en_us"), zh = language("zh_cn");
        for (String key : en.stringPropertyNames())
            if (key.startsWith("ftbquests.editor.") || key.startsWith("ftbquests.ponder.") || key.startsWith("ftbquests.media.") || key.startsWith("ftbquests.gui.search_") || key.startsWith("ftbquests.gui.line_") || key.equals("ftbquests.gui.edit_lines"))
            { assertNotNull(zh.getProperty(key), key); assertFalse(zh.getProperty(key).isBlank(), key); }
    }
    private Properties language(String name) throws Exception
    {
        Properties props = new Properties();
        try (var input = new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/ftbquests/lang/" + name + ".lang")), java.nio.charset.StandardCharsets.UTF_8))
        { props.load(input); }
        return props;
    }
}
