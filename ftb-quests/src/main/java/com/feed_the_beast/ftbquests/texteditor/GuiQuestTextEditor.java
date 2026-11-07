package com.feed_the_beast.ftbquests.texteditor;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObject;
import com.feed_the_beast.ftbquests.net.edit.MessagePasteImage;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import com.feed_the_beast.ftbquests.util.PonderTarget;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import java.util.*;

/** All draft changes stay local until Save. No external window or Swing event thread is required. */
public final class GuiQuestTextEditor extends GuiBase
{
    private final int questId;
    private String savedTitle, savedSubtitle, status = "";
    private List<String> savedLines;
    private DescriptionDocument document;
    private boolean markdown, previewOnly;
    private final TextBox title, subtitle;
    public final MultilineTextBox source;
    public final Panel preview;
    private final PanelScrollBar previewScroll;
    private final List<SimpleTextButton> toolbar = new ArrayList<>();
    private final SimpleTextButton mode, showSource, showPreview, help, save, cancel;
    private int previewDelay = 1, bodyY;
    private String encodedText;
    private boolean encodedMarkdown;
    private DescriptionDocument encodedDocument;
    private List<String> encodedLines;

    public GuiQuestTextEditor(Quest quest)
    {
        questId = quest.id; savedTitle = quest.title; savedSubtitle = quest.subtitle;
        savedLines = List.copyOf(quest.description); document = new DescriptionDocument(savedLines); markdown = document.markdown();
        title = input(savedTitle); subtitle = input(savedSubtitle);
        source = new MultilineTextBox(this, document.text(), this::changed, this::paste);
        preview = new Panel(this) {
            @Override public void addWidgets() {
                if (!title.getText().isEmpty()) add(new TextField(this).setMaxWidth(width).setText(FTBQuestsClient.addI18nAndColors(title.getText())));
                if (!subtitle.getText().isEmpty()) add(new TextField(this).setMaxWidth(width).setColor(Color4I.GRAY).setText(FTBQuestsClient.addI18nAndColors(subtitle.getText())));
                DescriptionComponents.addPreview(this, draftLines().stream().map(line -> DescriptionDocument.markdownObject(line) != null ? line : FTBQuestsClient.addI18nAndColors(line)).toArray(String[]::new));
            }
            @Override public void alignWidgets() {
                int y = 4;
                for (Widget widget : widgets) { widget.setPos(0, y); y += widget.height + 4; }
                previewScroll.setMaxValue(y);
            }
            @Override public void tick() {
                super.tick();
                if (DescriptionComponents.resizeMedia(this)) {
                    alignWidgets();
                    setScrollY(Math.max(0,Math.min(getScrollY(),previewScroll.getMaxValue())));
                    previewScroll.setValue((int)getScrollY());
                }
            }
            @Override public void drawBackground(Theme theme, int x, int y, int w, int h) { Color4I.rgb(0x171E26).draw(x, y, w, h); }
            @Override public boolean mousePressed(MouseButton button) { return isMouseOver(); }
        };
        previewScroll = new PanelScrollBar(this, preview);
        mode = button("", () -> { markdown = !markdown; modeTitle(); changed(); focusSource(); }); modeTitle();
        showSource = button(tr("source"), () -> { previewOnly = false; refreshWidgets(); });
        showPreview = button(tr("preview"), () -> { previewOnly = true; refreshWidgets(); });
        help = button("?", this::showHelp);
        save = button(tr("save"), () -> save(true)); cancel = button(I18n.format("gui.cancel"), this::onBack);
        toolbar("heading", () -> prefix("# "));
        toolbar("bold", () -> surround("**", "**"));
        toolbar("italic", () -> surround("*", "*"));
        toolbar("list", () -> prefix("- "));
        toolbar("link", () -> surround("[", "](https://example.com)"));
        toolbar("image", () -> insertComponent("image"));
        toolbar("video", () -> insertComponent("video"));
        toolbar("quest", () -> insertComponent("quest"));
        toolbar("item", () -> insertComponent("item"));
        toolbar("ponder", this::insertPonder);
        toolbar("code", () -> surround("\n```\n", "\n```\n"));
        toolbar("table", () -> {
            if (markdown) source.insert("\n| " + tr("sample_heading") + " | " + tr("sample_text") + " |\n| --- | --- |\n| A | B |\n");
            else insertComponent("table");
        });
        toolbar("component", this::componentMenu);
        toolbar("undo", source::undo); toolbar("redo", source::redo);
    }

    public static String tr(String key, Object... args) { return I18n.format("ftbquests.editor." + key, args); }
    private TextBox input(String value)
    {
        TextBox box = new TextBox(this) { @Override public void onTextChanged() { changed(); } };
        box.charLimit = Math.max(32767, value.length()); box.setText(value, false); return box;
    }
    private SimpleTextButton button(String label, Runnable action)
    {
        return new SimpleTextButton(this, label, Icon.EMPTY) {
            @Override public void onClicked(MouseButton button) { if (button.isLeft()) { GuiHelper.playClickSound(); action.run(); } }
            @Override public boolean renderTitleInCenter() { return true; }
        };
    }
    private void toolbar(String key, Runnable action)
    {
        SimpleTextButton button = button(tr(key), () -> { focusSource(); action.run(); });
        button.setHeight(18); toolbar.add(button);
    }
    private void modeTitle() { mode.setTitle(tr(markdown ? "mode_markdown" : "mode_legacy")); mode.setWidth(144); }
    private void focusSource() { title.setFocused(false); subtitle.setFocused(false); source.setFocused(true); }
    private void changed() { previewDelay = 3; status = ""; }
    public List<String> draftLines() {
        if (encodedText != source.buffer.text() || encodedMarkdown != markdown || encodedDocument != document) {
            encodedText = source.buffer.text(); encodedMarkdown = markdown; encodedDocument = document;
            encodedLines = document.encode(encodedText, markdown);
        }
        return encodedLines;
    }
    public boolean isMarkdown() { return markdown; }
    public boolean isDirty() { return !savedTitle.equals(title.getText()) || !savedSubtitle.equals(subtitle.getText()) || !savedLines.equals(draftLines()); }

    private void surround(String before, String after)
    {
        if (!markdown) { status = tr("enable_markdown"); return; }
        source.edit(() -> source.buffer.surround(before, after, tr("sample_text")));
    }
    private void prefix(String prefix)
    {
        if (!markdown && !prefix.equals("# ")) { status = tr("enable_markdown"); return; }
        source.edit(() -> source.buffer.prefixLine(prefix));
    }

    @Override public boolean onInit() { setFullscreen(); return true; }
    @Override public void addWidgets()
    {
        add(title); add(subtitle); add(mode); add(showSource); add(showPreview); add(help); addAll(toolbar); add(save); add(cancel);
        if (width >= 460 || !previewOnly) add(source);
        if (width >= 460 || previewOnly) { add(preview); add(previewScroll); }
    }
    @Override public void alignWidgets()
    {
        title.setPosAndSize(58, 26, width - 66, 18); subtitle.setPosAndSize(58, 48, width - 66, 18);
        mode.setPosAndSize(8, 70, 144, 18);
        showSource.setPosAndSize(mode.posX + mode.width + 6, 70, 54, 18);
        showPreview.setPosAndSize(showSource.posX + 58, 70, 54, 18); help.setPosAndSize(width - 26, 70, 18, 18);
        int x = 8, y = 92;
        for (SimpleTextButton button : toolbar)
        {
            if (x + button.width > width - 8) { x = 8; y += 21; }
            button.setPos(x, y); x += button.width + 4;
        }
        bodyY = y + 32; int bodyHeight = Math.max(20, height - bodyY - 38);
        int sourceWidth = width >= 460 ? (width - 24) / 2 : width - 16;
        source.setPosAndSize(8, bodyY, sourceWidth, bodyHeight);
        int previewX = width >= 460 ? sourceWidth + 16 : 8;
        preview.setPosAndSize(previewX, bodyY, width - previewX - 16, bodyHeight);
        previewScroll.setPosAndSize(width - 16, bodyY, 8, bodyHeight);
        // Leave room for the panel's four-pixel padding on either side.
        preview.refreshWidgets(); previewDelay = 0;
        save.setPosAndSize(width - 158, height - 26, 82, 18); cancel.setPosAndSize(width - 72, height - 26, 64, 18);
        source.setFocused(!previewOnly);
    }

    @Override public void tick()
    {
        super.tick();
        Keyboard.enableRepeatEvents(title.isFocused() || subtitle.isFocused() || source.isFocused());
        if (previewDelay > 0 && --previewDelay == 0) {
            double scroll = preview.getScrollY(); preview.refreshWidgets(); preview.setScrollY(Math.max(0, Math.min(scroll, previewScroll.getMaxValue())));
            previewScroll.setValue((int)preview.getScrollY());
        }
    }
    @Override public boolean mousePressed(MouseButton button)
    {
        if (contextMenu != null) return super.mousePressed(button);
        if (button == MouseButton.BACK) { onBack(); return true; }
        // Set every focus explicitly; button dispatch stops at the first consumer in FTBLib.
        title.setFocused(title.isMouseOver()); subtitle.setFocused(subtitle.isMouseOver());
        source.setFocused(source.isMouseOver() && (width >= 460 || !previewOnly));
        return super.mousePressed(button);
    }
    @Override public boolean keyPressed(int key, char character)
    {
        if (contextMenu != null) {
            if (key == Keyboard.KEY_ESCAPE) { closeContextMenu(); focusSource(); }
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE) { onBack(); return true; }
        if (isCtrlKeyDown() && key == Keyboard.KEY_S) { save(false); return true; }
        if (source.isFocused() && isCtrlKeyDown() && key == Keyboard.KEY_B) { surround("**", "**"); return true; }
        if (source.isFocused() && isCtrlKeyDown() && key == Keyboard.KEY_I) { surround("*", "*"); return true; }
        if (key == Keyboard.KEY_TAB && !source.isFocused()) {
            boolean wasTitle = title.isFocused(); title.setFocused(false); subtitle.setFocused(wasTitle); source.setFocused(!wasTitle); return true;
        }
        return super.keyPressed(key, character);
    }
    @Override public boolean onClosedByKey(int key) { return false; }
    @Override public void onBack()
    {
        if (!isDirty()) { closeGui(true); return; }
        List<ContextMenuItem> options = new ArrayList<>();
        options.add(new ContextMenuItem(tr("save"), GuiIcons.ACCEPT, () -> save(true)));
        options.add(new ContextMenuItem(tr("discard"), GuiIcons.CANCEL, () -> closeGui(true)));
        options.add(new ContextMenuItem(tr("continue"), Icon.EMPTY, () -> source.setFocused(true)));
        openContextMenu(options);
    }

    public void save(boolean close)
    {
        Quest quest = ClientQuestFile.INSTANCE == null ? null : ClientQuestFile.INSTANCE.getQuest(questId);
        if (quest == null || !ClientQuestFile.INSTANCE.canEdit()) { status = tr("cannot_save"); return; }
        List<String> lines = draftLines();
        if (isDirty())
        {
            quest.title = title.getText(); quest.subtitle = subtitle.getText(); quest.description.clear(); quest.description.addAll(lines);
            MessageEditObject.send(quest);
            savedTitle = quest.title; savedSubtitle = quest.subtitle; savedLines = List.copyOf(lines); document = new DescriptionDocument(lines);
        }
        status = tr("submitted");
        if (close) closeGui(true);
    }

    private void paste()
    {
        try
        {
            byte[] bytes = CanvasClipboard.imageBytes();
            if (bytes != null)
            {
                String name = QuestAssets.name(bytes, "png"); MessagePasteImage.send(0, 0, 0, bytes);
                JsonObject image = new JsonObject(); image.addProperty("type", "image"); image.addProperty("source", "asset:" + name);
                insertLine(image.toString()); return;
            }
        }
        catch (Exception | LinkageError ex)
        {
            String text = GuiScreen.getClipboardString();
            if (!text.isEmpty()) { source.insert(text); return; }
            FTBQuests.LOGGER.warn("Could not paste description image", ex); status = tr("paste_failed"); return;
        }
        source.insert(GuiScreen.getClipboardString());
    }

    private void insertLine(String value)
    {
        String text = source.buffer.text(); int start = source.buffer.start(), end = source.buffer.end();
        source.insert((start > 0 && text.charAt(start - 1) != '\n' ? "\n" : "") + value + (end < text.length() && text.charAt(end) == '\n' ? "" : "\n"));
    }
    private void insertPonder()
    {
        if (!markdown) { insertComponent("ponder"); return; }
        PonderLink.choose(this, option -> {
            source.edit(() -> source.buffer.insertMarkdownLink(PonderTarget.parse(option.value()).uri(), option.title()));
            focusSource();
        });
    }
    private void componentMenu()
    {
        List<ContextMenuItem> entries = new ArrayList<>();
        entries.add(new ContextMenuItem(tr("edit_component"), GuiIcons.INFO, () -> insertComponent("text")));
        entries.add(ContextMenuItem.SEPARATOR);
        for (String kind : new String[]{"image", "link", "copy", "command", "quest", "item", "video", "entity", "hover", "font", "heading", "localize", "keybind", "obfuscated", "table", "ponder", "zs"})
            entries.add(new ContextMenuItem(tr("kind." + kind), Icon.EMPTY, () -> insertComponent(kind)));
        openContextMenu(entries);
    }
    private void insertComponent(String kind)
    {
        String text = source.buffer.text(); int cursor = source.buffer.cursor();
        int start = text.lastIndexOf('\n', cursor - 1) + 1, end = text.indexOf('\n', cursor); if (end < 0) end = text.length();
        JsonObject json = null;
        try { json = new JsonParser().parse(text.substring(start, end)).getAsJsonObject(); }
        catch (RuntimeException ignored) {}
        boolean replace = json != null;
        if (json == null) { json = new JsonObject(); json.addProperty("type", kind); }
        final int from = start, to = end;
        new GuiDescriptionComponent(json, edited -> {
            if (replace) source.edit(() -> source.buffer.replaceRange(from, to, edited.toString()));
            else insertLine(edited.toString());
            openGui(); source.setFocused(true);
        }).openGui();
    }

    private void showHelp()
    {
        List<ContextMenuItem> entries = new ArrayList<>();
        for (String key : new String[]{"help.heading", "help.style", "help.list", "help.link", "help.ponder", "help.image", "help.code", "help.table", "help.keys", "help.compat", "help.preview"})
            entries.add(new ContextMenuItem(tr(key), Icon.EMPTY, () -> {}).setEnabled(false));
        openContextMenu(entries);
    }
    @Override public boolean handleClick(String scheme, String path) { return false; }
    @Override public void drawBackground(Theme theme, int x, int y, int w, int h)
    {
        Color4I.rgb(0x10171F).draw(x, y, w, h);
        theme.drawString(tr("title") + (isDirty() ? " *" : ""), x + 8, y + 8, Color4I.WHITE, Theme.SHADOW);
        theme.drawString(I18n.format("ftbquests.title"), x + 8, y + 31, Color4I.GRAY, 0);
        theme.drawString(I18n.format("ftbquests.quest.subtitle"), x + 8, y + 53, Color4I.GRAY, 0);
        theme.drawString(tr(width < 460 && previewOnly ? "preview" : "source"), x + 8, y + bodyY - 11, Color4I.GRAY, 0);
        if (width >= 460) theme.drawString(tr("preview"), x + preview.posX, y + bodyY - 11, Color4I.GRAY, 0);
        String footer = status.isEmpty() ? tr("shortcuts") : status;
        theme.drawString(theme.trimStringToWidth(footer, Math.max(30, width - 178)), x + 8, y + height - 21, Color4I.rgb(0xA8C7D9), 0);
    }
}
