package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftbquests.texteditor.QuestMarkdown;
import com.feed_the_beast.ftbquests.texteditor.SourceLayout;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;

/** Shared renderer: the editor preview uses the same blocks as the quest description. */
public final class MarkdownRenderer
{
    public static void add(Panel panel, String source, int depth)
    {
        // FTBLib refreshes child panels once before assigning their final size.
        // Vanilla's text splitter cannot make progress when a glyph is wider than its viewport.
        if (panel.width < 32) return;
        if (depth > 8) { panel.add(new TextField(panel).setMaxWidth(panel.width).setText(source)); return; }
        for (QuestMarkdown.Block block : QuestMarkdown.parse(source))
        {
            switch (block.kind())
            {
                case SPACE -> panel.add(new Widget(panel) {{ setSize(panel.width, 5); }});
                case RULE -> panel.add(new Widget(panel) {
                    { setSize(panel.width, 8); }
                    @Override public void draw(Theme theme, int x, int y, int w, int h) { Color4I.GRAY.draw(x, y + 3, w, 1); }
                });
                case COMPONENT -> DescriptionComponents.addLine(panel, block.text(), depth + 1);
                case IMAGE -> {
                    JsonObject image = new JsonObject(); image.addProperty("type", "image"); image.addProperty("source", block.text());
                    DescriptionComponents.addLine(panel, image.toString(), depth + 1);
                }
                case TABLE -> {
                    JsonObject table = new JsonObject(); table.addProperty("type", "table"); JsonArray rows = new JsonArray();
                    for (var row : block.rows()) { JsonArray cells = new JsonArray(); for (String cell : row) cells.add(inline(cell).getFormattedText()); rows.add(cells); }
                    table.add("rows", rows); DescriptionComponents.addLine(panel, table.toString(), depth + 1);
                }
                case CODE -> {
                    SourceLayout code = new SourceLayout(block.text(), Math.max(8, panel.width - 8), MarkdownRenderer::advance);
                    panel.add(new Widget(panel) {
                        { setSize(panel.width, code.rows.size() * 11 + 8); }
                        @Override public void draw(Theme theme, int x, int y, int w, int h) {
                            Color4I.rgb(0x18222B).draw(x, y, w, h); int rowY = y + 4;
                            for (SourceLayout.Row row : code.rows) {
                                int pixel = x + 4;
                                for (int i = row.start(); i < row.end(); i++) {
                                    char c = block.text().charAt(i);
                                    if (c != '\t') theme.drawString(Character.toString(c), pixel, rowY, Color4I.rgb(0xC4DAE9), 0);
                                    pixel += advance(c);
                                }
                                rowY += 11;
                            }
                        }
                    });
                }
                default -> {
                    float scale = block.kind() == QuestMarkdown.Kind.HEADING ? 1.7F - block.level() * .1F : 1F;
                    ITextComponent component = inline(block.text());
                    if (block.kind() == QuestMarkdown.Kind.HEADING) component.getStyle().setBold(true);
                    if (block.kind() == QuestMarkdown.Kind.QUOTE) component.getStyle().setColor(TextFormatting.GRAY).setItalic(true);
                    int wrap = Math.max(8, (int)(panel.width / scale));
                    var rows = GuiUtilRenderComponents.splitText(component, wrap, panel.getGui().getTheme().getFont(), false, false);
                    panel.add(new Widget(panel) {
                        { setSize(panel.width, (int)Math.ceil(Math.max(1, rows.size()) * 11 * scale)); }
                        private ClickEvent atMouse() {
                            if (!isMouseOver()) return null;
                            int row = (int)((getMouseY() - getY()) / scale / 11), x = 0;
                            if (row < 0 || row >= rows.size()) return null;
                            for (ITextComponent part : rows.get(row)) {
                                // getFormattedText() includes siblings; the empty row root would cover every link.
                                int width = getGui().getTheme().getStringWidth(part.getStyle().getFormattingCode() + part.getUnformattedComponentText());
                                if ((getMouseX() - getX()) / scale >= x && (getMouseX() - getX()) / scale < x + width) return part.getStyle().getClickEvent();
                                x += width;
                            }
                            return null;
                        }
                        @Override public boolean mousePressed(MouseButton button) {
                            var part = atMouse();
                            return button.isLeft() && part != null && handleClick(GuiHelper.clickEventToString(part));
                        }
                        @Override public void addMouseOverText(java.util.List<String> tooltip) {
                            var part = atMouse();
                            if (part != null) tooltip.add(part.getValue());
                        }
                        @Override public void draw(Theme theme, int x, int y, int w, int h) {
                            GlStateManager.pushMatrix(); GlStateManager.translate(x, y, 0); GlStateManager.scale(scale, scale, 1);
                            for (int row = 0; row < rows.size(); row++) theme.drawString(rows.get(row).getFormattedText(), 0, row * 11, Color4I.WHITE, 0);
                            GlStateManager.popMatrix();
                        }
                    });
                }
            }
        }
    }

    private static int advance(int c) { return c == '\t' ? 16 : Math.max(c == '§' ? 6 : 0, Minecraft.getMinecraft().fontRenderer.getCharWidth((char)c)); }

    private static ITextComponent inline(String text)
    {
        TextComponentString root = new TextComponentString("");
        for (QuestMarkdown.Span span : QuestMarkdown.inline(text))
        {
            String value = span.text();
            if (!span.code())
            {
                int first = 0, last = value.length();
                while (first < last && Character.isWhitespace(value.charAt(first))) first++;
                while (last > first && Character.isWhitespace(value.charAt(last - 1))) last--;
                value = value.substring(0, first) + FTBQuestsClient.addI18nAndColors(DescriptionComponents.substitute(value.substring(first, last))) + value.substring(last);
            }
            TextComponentString part = new TextComponentString(value);
            Style style = part.getStyle();
            if (span.bold()) style.setBold(true);
            if (span.italic()) style.setItalic(true);
            if (span.strike()) style.setStrikethrough(true);
            if (span.code()) style.setColor(TextFormatting.GRAY);
            if (!span.link().isEmpty()) style.setColor(TextFormatting.AQUA).setUnderlined(true)
                    .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, span.link()));
            root.appendSibling(part);
        }
        return root;
    }
}
