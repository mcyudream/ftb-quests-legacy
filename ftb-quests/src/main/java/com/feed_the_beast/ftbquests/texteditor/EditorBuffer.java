package com.feed_the_beast.ftbquests.texteditor;

import java.util.ArrayDeque;
import java.util.Deque;

/** Text and selection only; independent of rendering, packets and quest mutations. */
public final class EditorBuffer
{
    private record State(String text, int cursor, int anchor) {}
    private final Deque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private String text;
    private int cursor, anchor;
    private static final int MAX_HISTORY = 100;
    private static final long MAX_HISTORY_CHARS = 4_000_000;

    public EditorBuffer(String text) { this.text = text; }
    public String text() { return text; }
    public int cursor() { return cursor; }
    public int anchor() { return anchor; }
    public int start() { return Math.min(cursor, anchor); }
    public int end() { return Math.max(cursor, anchor); }
    public String selected() { return text.substring(start(), end()); }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    public void select(int position, boolean extend)
    {
        cursor = Math.clamp(position, 0, text.length());
        if (!extend) anchor = cursor;
    }

    public void selectAll() { anchor = 0; cursor = text.length(); }

    public void replace(String value)
    {
        replaceRange(start(), end(), value);
    }

    public void replaceRange(int start, int end, String value)
    {
        String next = text.substring(0, start) + value + text.substring(end);
        if (next.equals(text)) { select(start + value.length(), false); return; }
        push(undo, state()); redo.clear();
        text = next; select(start + value.length(), false);
    }

    public void surround(String before, String after, String placeholder)
    {
        int start = start(); String chosen = selected();
        if (chosen.isEmpty()) chosen = placeholder;
        replace(before + chosen + after);
        select(start + before.length(), false);
        select(start + before.length() + chosen.length(), true);
    }

    /** One undo step, retaining all text outside the selection and using a plain-text caption. */
    public void insertMarkdownLink(String target, String defaultTitle)
    {
        String caption = selected().isEmpty() ? defaultTitle : selected();
        if (caption.isEmpty()) caption = target;
        caption = caption.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < caption.length(); i++) {
            char c = caption.charAt(i);
            if ("\\`*_[]()~".indexOf(c) >= 0) escaped.append('\\');
            escaped.append(c);
        }
        replace("[" + escaped + "](" + target + ")");
    }

    public void prefixLine(String prefix)
    {
        int start = text.lastIndexOf('\n', Math.max(-1, start() - 1)) + 1;
        replaceRange(start, start, prefix);
    }

    public void delete(boolean backwards, boolean word)
    {
        if (start() != end()) { replace(""); return; }
        int other = word ? wordBoundary(backwards) : characterBoundary(backwards);
        replaceRange(Math.min(cursor, other), Math.max(cursor, other), "");
    }

    public int characterBoundary(boolean backwards)
    {
        return backwards ? (cursor == 0 ? 0 : text.offsetByCodePoints(cursor, -1))
                : (cursor == text.length() ? cursor : text.offsetByCodePoints(cursor, 1));
    }

    public int wordBoundary(boolean backwards)
    {
        int at = cursor;
        if (backwards)
        {
            while (at > 0 && Character.isWhitespace(text.charAt(at - 1))) at--;
            while (at > 0 && !Character.isWhitespace(text.charAt(at - 1))) at--;
        }
        else
        {
            while (at < text.length() && !Character.isWhitespace(text.charAt(at))) at++;
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }
        return at;
    }

    public void undo() { restore(undo, redo); }
    public void redo() { restore(redo, undo); }

    private State state() { return new State(text, cursor, anchor); }
    private void restore(Deque<State> from, Deque<State> to)
    {
        if (from.isEmpty()) return;
        push(to, state()); State state = from.removeLast();
        text = state.text; cursor = state.cursor; anchor = state.anchor;
    }
    private static void push(Deque<State> history, State state)
    {
        history.addLast(state);
        long chars = 0;
        for (State entry : history) chars += entry.text.length();
        while (history.size() > 1 && (history.size() > MAX_HISTORY || chars > MAX_HISTORY_CHARS))
            chars -= history.removeFirst().text.length();
    }
}
