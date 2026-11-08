package com.feed_the_beast.ftbquests.texteditor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Wrapped source positions. Newlines and soft wraps remain distinct in the buffer. */
public final class SourceLayout
{
    public record Row(int start, int end) {}
    public final List<Row> rows = new ArrayList<>();
    private final String text;
    private final IntUnaryOperator advance;

    public SourceLayout(String text, int width, IntUnaryOperator advance)
    {
        this.text = text; this.advance = advance;
        int start = 0, pixels = 0;
        for (int i = 0; i < text.length();)
        {
            int point = text.codePointAt(i);
            if (point == '\n') { rows.add(new Row(start, i)); start = ++i; pixels = 0; continue; }
            int size = advance.applyAsInt(point);
            if (pixels + size > Math.max(1, width) && i > start)
            { rows.add(new Row(start, i)); start = i; pixels = 0; }
            pixels += size;
            i += Character.charCount(point);
        }
        rows.add(new Row(start, text.length()));
    }

    public int rowAt(int position)
    {
        int low = 0, high = rows.size() - 1;
        while (low < high)
        {
            int middle = (low + high + 1) / 2;
            if (rows.get(middle).start <= position) low = middle; else high = middle - 1;
        }
        return low;
    }

    public int xAt(int position)
    {
        Row row = rows.get(rowAt(position)); int x = 0;
        for (int i = row.start; i < Math.min(position, row.end);) {
            int point = text.codePointAt(i); x += advance.applyAsInt(point); i += Character.charCount(point);
        }
        return x;
    }

    public int positionAt(int rowIndex, int x)
    {
        Row row = rows.get(Math.clamp(rowIndex, 0, rows.size() - 1)); int pixels = 0;
        for (int i = row.start; i < row.end;)
        {
            int point = text.codePointAt(i), size = advance.applyAsInt(point);
            if (x < pixels + size / 2) return i;
            pixels += size;
            i += Character.charCount(point);
        }
        return row.end;
    }
}
