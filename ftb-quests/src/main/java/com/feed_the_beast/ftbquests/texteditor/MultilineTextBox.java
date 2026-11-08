package com.feed_the_beast.ftbquests.texteditor;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/** In-game source editor with selection, wrapping, scrolling and local undo. */
public final class MultilineTextBox extends Widget implements FocusableTextInput
{
    public final EditorBuffer buffer;
    private final Runnable changed, paste;
    private boolean focused, dragging, scrollDragging;
    private final com.feed_the_beast.ftblib.lib.util.UnicodeTextInput characterInput = new com.feed_the_beast.ftblib.lib.util.UnicodeTextInput();
    private SourceLayout layout;
    private String layoutText;
    private int layoutWidth, topRow;
    private static final int LINE_HEIGHT = 12;

    public MultilineTextBox(Panel panel, String text, Runnable changed, Runnable paste)
    {
        super(panel); buffer = new EditorBuffer(text); this.changed = changed; this.paste = paste;
    }

    public void setFocused(boolean value) {
        if (focused != value) characterInput.reset();
        focused = value;
        if (!value) dragging = scrollDragging = false;
        getGui().onTextInputFocusChanged(this, value);
    }
    public boolean isFocused() { return focused; }
    public int topRow() { return topRow; }
    private int visibleRows() { return Math.max(1, (height - 8) / LINE_HEIGHT); }
    private int maxScroll() { return Math.max(0, layout().rows.size() - visibleRows()); }
    private static int advance(int c)
    {
        if (c == '\t') return 16;
        if (Character.isSupplementaryCodePoint(c)) return Minecraft.getMinecraft().fontRenderer.getStringWidth(new String(Character.toChars(c)));
        int width = Minecraft.getMinecraft().fontRenderer.getCharWidth((char)c);
        return width < 0 ? 6 : width;
    }

    private SourceLayout layout()
    {
        if (layout == null || layoutText != buffer.text() || layoutWidth != width)
        {
            layoutText = buffer.text(); layoutWidth = width;
            layout = new SourceLayout(layoutText, Math.max(1, width - 14), MultilineTextBox::advance);
        }
        return layout;
    }

    public void edit(Runnable action)
    {
        characterInput.reset();
        String before = buffer.text(); action.run();
        ensureCursorVisible();
        if (!before.equals(buffer.text())) changed.run();
    }

    public void insert(String text) { edit(() -> buffer.replace(text.replace("\r\n", "\n").replace('\r', '\n'))); }
    public void undo() { edit(buffer::undo); }
    public void redo() { edit(buffer::redo); }
    private void ensureCursorVisible()
    {
        int row = layout().rowAt(buffer.cursor());
        if (row < topRow) topRow = row;
        if (row >= topRow + visibleRows()) topRow = row - visibleRows() + 1;
        topRow = Math.clamp(topRow, 0, maxScroll());
    }

    @Override public boolean mousePressed(MouseButton button)
    {
        if (!isMouseOver() || !button.isLeft()) return false;
        setFocused(true);
        if (getMouseX() >= getX() + width - 8) { scrollDragging = true; updateScrollDrag(); }
        else { buffer.select(positionAtMouse(), isShiftKeyDown()); dragging = true; }
        return true;
    }

    private int positionAtMouse()
    {
        return layout().positionAt(topRow + Math.floorDiv(getMouseY() - getY() - 4, LINE_HEIGHT), getMouseX() - getX() - 4);
    }
    private void updateScrollDrag()
    {
        topRow = Math.clamp((int)((getMouseY() - getY() - 4D) / Math.max(1, height - 8) * (maxScroll() + visibleRows()) - visibleRows() / 2D), 0, maxScroll());
    }
    @Override public void mouseReleased(MouseButton button) { dragging = scrollDragging = false; }
    @Override public boolean mouseScrolled(int scroll)
    {
        if (!isMouseOver()) return false;
        topRow = Math.clamp(topRow - scroll * 3, 0, maxScroll()); return true;
    }

    @Override public boolean keyPressed(int key, char character)
    {
        if (!focused) return false;
        if (key == Keyboard.KEY_NONE && ChatAllowedCharacters.isAllowedCharacter(character)) { String committed = characterInput.accept(character); if (!committed.isEmpty()) insert(committed); return true; }
        if (isCtrlKeyDown())
        {
            switch (key)
            {
                case Keyboard.KEY_A -> { buffer.selectAll(); ensureCursorVisible(); return true; }
                case Keyboard.KEY_C -> { if (!buffer.selected().isEmpty()) setClipboardString(buffer.selected()); return true; }
                case Keyboard.KEY_X -> { if (!buffer.selected().isEmpty()) { setClipboardString(buffer.selected()); edit(() -> buffer.replace("")); } return true; }
                case Keyboard.KEY_V -> { paste.run(); return true; }
                case Keyboard.KEY_Z -> { if (isShiftKeyDown()) redo(); else undo(); return true; }
                case Keyboard.KEY_Y -> { redo(); return true; }
            }
        }
        switch (key)
        {
            case Keyboard.KEY_RETURN, Keyboard.KEY_NUMPADENTER -> insert("\n");
            case Keyboard.KEY_TAB -> insert("    ");
            case Keyboard.KEY_BACK -> edit(() -> buffer.delete(true, isCtrlKeyDown()));
            case Keyboard.KEY_DELETE -> edit(() -> buffer.delete(false, isCtrlKeyDown()));
            case Keyboard.KEY_LEFT, Keyboard.KEY_RIGHT -> {
                boolean left = key == Keyboard.KEY_LEFT;
                int position = !isShiftKeyDown() && buffer.start() != buffer.end() ? (left ? buffer.start() : buffer.end())
                        : isCtrlKeyDown() ? buffer.wordBoundary(left) : buffer.characterBoundary(left);
                buffer.select(position, isShiftKeyDown()); ensureCursorVisible();
            }
            case Keyboard.KEY_HOME, Keyboard.KEY_END -> {
                boolean home = key == Keyboard.KEY_HOME; SourceLayout.Row row = layout().rows.get(layout().rowAt(buffer.cursor()));
                buffer.select(isCtrlKeyDown() ? (home ? 0 : buffer.text().length()) : (home ? row.start() : row.end()), isShiftKeyDown());
                ensureCursorVisible();
            }
            case Keyboard.KEY_UP, Keyboard.KEY_DOWN, Keyboard.KEY_PRIOR, Keyboard.KEY_NEXT -> {
                int delta = key == Keyboard.KEY_UP ? -1 : key == Keyboard.KEY_DOWN ? 1 : key == Keyboard.KEY_PRIOR ? -visibleRows() : visibleRows();
                buffer.select(layout().positionAt(layout().rowAt(buffer.cursor()) + delta, layout().xAt(buffer.cursor())), isShiftKeyDown()); ensureCursorVisible();
            }
            default -> {
                if (!isCtrlKeyDown() && ChatAllowedCharacters.isAllowedCharacter(character)) insert(Character.toString(character));
                else return false;
            }
        }
        return true;
    }

    @Override public void draw(Theme theme, int x, int y, int w, int h)
    {
        theme.drawTextBox(x, y, w, h);
        if (focused) GuiHelper.drawHollowRect(x, y, w, h, Color4I.rgb(0x63B8DA), false);
        if (scrollDragging) updateScrollDrag();
        if (dragging)
        {
            if (getMouseY() < y + 4) topRow = Math.max(0, topRow - 1);
            if (getMouseY() >= y + h - 4) topRow = Math.min(maxScroll(), topRow + 1);
            buffer.select(positionAtMouse(), true);
        }
        SourceLayout data = layout(); topRow = Math.clamp(topRow, 0, maxScroll());
        GuiHelper.pushScissor(getScreen(), x + 2, y + 2, w - 10, h - 4);
        for (int index = topRow; index < Math.min(data.rows.size(), topRow + visibleRows()); index++)
        {
            SourceLayout.Row row = data.rows.get(index); int lineY = y + 4 + (index - topRow) * LINE_HEIGHT, pixel = 0;
            int selectionStart = buffer.start(), selectionEnd = buffer.end();
            for (int at = row.start(); at < row.end();)
            {
                int c = buffer.text().codePointAt(at), size = advance(c);
                if (at >= selectionStart && at < selectionEnd) Color4I.rgb(0x315D7B).draw(x + 4 + pixel, lineY - 1, size, LINE_HEIGHT);
                if (c != '\t') theme.drawString(new String(Character.toChars(c)), x + 4 + pixel, lineY, Color4I.WHITE, 0);
                pixel += size;
                at += Character.charCount(c);
            }
            if (row.end() >= selectionStart && row.end() < selectionEnd) Color4I.rgb(0x315D7B).draw(x + 4 + pixel, lineY - 1, 4, LINE_HEIGHT);
            if (focused && data.rowAt(buffer.cursor()) == index && Minecraft.getSystemTime() % 1000 < 600)
                Color4I.WHITE.draw(x + 4 + data.xAt(buffer.cursor()), lineY - 1, 1, 11);
        }
        GuiHelper.popScissor(getScreen());
        if (maxScroll() > 0)
        {
            Color4I.rgb(0x252B32).draw(x + w - 7, y + 2, 5, h - 4);
            int thumb = Math.max(8, (h - 4) * visibleRows() / data.rows.size());
            Color4I.rgb(0x738797).draw(x + w - 7, y + 2 + (h - 4 - thumb) * topRow / maxScroll(), 5, thumb);
        }
    }

    @Override public void onClosed() { setFocused(false); characterInput.reset(); }

    /** Compatibility entry point: keep editing in the original source box. */
    @Deprecated public boolean openNativeInput() { setFocused(true); return true; }
}
