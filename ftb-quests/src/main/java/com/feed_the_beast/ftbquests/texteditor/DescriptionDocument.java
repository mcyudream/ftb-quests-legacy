package com.feed_the_beast.ftbquests.texteditor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.List;

/** A draft of the existing description list. Opening/saving an unchanged draft is lossless. */
public final class DescriptionDocument
{
    private final List<String> original;
    private final String originalText;
    private final boolean originalMarkdown;
    private final JsonObject envelope;

    public DescriptionDocument(List<String> lines)
    {
        original = List.copyOf(lines);
        envelope = lines.size() == 1 ? markdownObject(lines.get(0)) : null;
        originalMarkdown = envelope != null || lines.isEmpty();
        originalText = envelope == null ? String.join("\n", lines) : envelope.get("text").getAsString();
    }

    public String text() { return originalText; }
    public boolean markdown() { return originalMarkdown; }

    public List<String> encode(String text, boolean markdown)
    {
        if (text.equals(originalText) && markdown == originalMarkdown) return original;
        if (!markdown) return Arrays.asList(text.split("\n", -1));
        JsonObject json = envelope == null ? new JsonObject() : envelope.deepCopy();
        json.addProperty("type", "markdown");
        json.addProperty("text", text);
        return List.of(json.toString());
    }

    public static JsonObject markdownObject(String line)
    {
        if (!line.stripLeading().startsWith("{")) return null;
        try
        {
            JsonObject json = new JsonParser().parse(line).getAsJsonObject();
            if (json.has("type") && json.get("type").isJsonPrimitive()
                    && "markdown".equals(json.get("type").getAsString())
                    && json.has("text") && json.get("text").isJsonPrimitive()
                    && json.get("text").getAsJsonPrimitive().isString()) return json;
        }
        catch (RuntimeException ignored) {}
        return null;
    }
}
