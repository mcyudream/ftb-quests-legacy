package com.feed_the_beast.ftbquests.texteditor;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Deliberately small Markdown dialect used by both the quest view and its preview. */
public final class QuestMarkdown
{
    public enum Kind { PARAGRAPH, HEADING, QUOTE, LIST, CODE, RULE, TABLE, IMAGE, COMPONENT, SPACE }
    public record Block(Kind kind, String text, int level, List<List<String>> rows) {}
    public record Span(String text, boolean bold, boolean italic, boolean strike, boolean code, String link) {}
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+)$");
    private static final Pattern LIST = Pattern.compile("^\\s*([-+*]|\\d+\\.)[ \\t]+(.*)$");
    private static final Pattern IMAGE = Pattern.compile("^!\\[([^]]*)]\\(([^\\s)]+)\\)$");

    public static List<Block> parse(String source)
    {
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<Block> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i], trim = line.strip();
            var heading = HEADING.matcher(line); var list = LIST.matcher(line); var image = IMAGE.matcher(trim);
            if (trim.startsWith("```") || trim.startsWith("~~~"))
            {
                char fence = trim.charAt(0); int size = 0;
                while (size < trim.length() && trim.charAt(size) == fence) size++;
                StringBuilder code = new StringBuilder();
                boolean firstLine = true;
                while (++i < lines.length)
                {
                    String close = lines[i].strip();
                    if (close.length() >= size && close.chars().allMatch(c -> c == fence)) break;
                    if (!firstLine) code.append('\n');
                    firstLine = false;
                    code.append(lines[i]);
                }
                result.add(block(Kind.CODE, code.toString(), 0));
            }
            else if (trim.isEmpty()) result.add(block(Kind.SPACE, "", 0));
            else if (trim.matches("-{3,}|\\*{3,}|_{3,}")) result.add(block(Kind.RULE, "", 0));
            else if (heading.matches()) result.add(block(Kind.HEADING, heading.group(2), heading.group(1).length()));
            else if (trim.startsWith(">")) result.add(block(Kind.QUOTE, trim.substring(1).stripLeading(), 0));
            else if (list.matches()) result.add(block(Kind.LIST, (list.group(1).length() == 1 ? "•" : list.group(1)) + " " + list.group(2), 0));
            else if (image.matches()) result.add(block(Kind.IMAGE, image.group(2), 0));
            else if (i + 1 < lines.length && trim.contains("|") && tableSeparator(lines[i + 1]))
            {
                List<List<String>> rows = new ArrayList<>(); rows.add(cells(line)); i++;
                while (i + 1 < lines.length && lines[i + 1].contains("|") && !lines[i + 1].isBlank() && rows.size() < 128)
                    rows.add(cells(lines[++i]));
                result.add(new Block(Kind.TABLE, "", 0, rows));
            }
            else if (jsonComponent(trim)) result.add(block(Kind.COMPONENT, line, 0));
            else result.add(block(Kind.PARAGRAPH, line, 0));
        }
        return result;
    }

    private static Block block(Kind kind, String text, int level) { return new Block(kind, text, level, List.of()); }
    private static boolean jsonComponent(String line)
    {
        if (!line.startsWith("{")) return false;
        try { return new JsonParser().parse(line).isJsonObject(); }
        catch (RuntimeException ignored) { return false; }
    }
    private static boolean tableSeparator(String line)
    {
        List<String> cells = cells(line);
        return !cells.isEmpty() && cells.stream().allMatch(s -> s.matches(":?-{3,}:?"));
    }
    private static List<String> cells(String line)
    {
        String trim = line.strip();
        if (trim.startsWith("|")) trim = trim.substring(1);
        if (trim.endsWith("|") && !trim.endsWith("\\|")) trim = trim.substring(0, trim.length() - 1);
        List<String> cells = new ArrayList<>(); StringBuilder cell = new StringBuilder();
        for (int i = 0; i < trim.length(); i++)
        {
            char c = trim.charAt(i);
            if (c == '\\' && i + 1 < trim.length() && trim.charAt(i + 1) == '|') { cell.append('|'); i++; }
            else if (c == '|' && cells.size() < 63) { cells.add(cell.toString().strip()); cell.setLength(0); }
            else cell.append(c);
        }
        cells.add(cell.toString().strip()); return cells;
    }

    public static List<Span> inline(String text)
    {
        List<Span> spans = new ArrayList<>(); inline(text, false, false, false, "", 0, spans); return spans;
    }
    private static void inline(String text, boolean bold, boolean italic, boolean strike, String link, int depth, List<Span> out)
    {
        if (depth > 12) { out.add(new Span(text, bold, italic, strike, false, link)); return; }
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < text.length();)
        {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && "\\`*_{}[]()#+-.!>|~".indexOf(text.charAt(i + 1)) >= 0)
            { plain.append(text.charAt(i + 1)); i += 2; continue; }
            String delimiter = text.startsWith("**", i) ? "**" : text.startsWith("~~", i) ? "~~" : c == '*' ? "*" : c == '`' ? "`" : "";
            int end = delimiter.isEmpty() ? -1 : text.indexOf(delimiter, i + delimiter.length());
            if (end > i + delimiter.length())
            {
                flush(plain, bold, italic, strike, link, out);
                String inner = text.substring(i + delimiter.length(), end);
                if (delimiter.equals("`")) out.add(new Span(inner, bold, italic, strike, true, link));
                else inline(inner, bold || delimiter.equals("**"), italic || delimiter.equals("*"), strike || delimiter.equals("~~"), link, depth + 1, out);
                i = end + delimiter.length(); continue;
            }
            if (c == '[' && link.isEmpty())
            {
                int labelEnd = text.indexOf("](", i + 1);
                int urlEnd = labelEnd < 0 ? -1 : text.indexOf(')', labelEnd + 2);
                if (urlEnd > labelEnd + 2)
                {
                    String url = text.substring(labelEnd + 2, urlEnd);
                    if (!url.chars().anyMatch(Character::isWhitespace))
                    {
                        flush(plain, bold, italic, strike, link, out);
                        inline(text.substring(i + 1, labelEnd), bold, italic, strike, url, depth + 1, out);
                        i = urlEnd + 1; continue;
                    }
                }
            }
            plain.append(c); i++;
        }
        flush(plain, bold, italic, strike, link, out);
    }
    private static void flush(StringBuilder text, boolean bold, boolean italic, boolean strike, String link, List<Span> out)
    {
        if (text.isEmpty()) return;
        out.add(new Span(text.toString(), bold, italic, strike, false, link)); text.setLength(0);
    }
}
