package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.quest.*;
import com.google.gson.*;
import java.util.List;
import com.feed_the_beast.ftbquests.texteditor.QuestMarkdown;

public final class ScriptTextActions
{
    public static boolean valid(String action)
    { return action != null && action.length() <= 128 && action.matches("[a-zA-Z0-9_.:/-]+"); }

    public static boolean declared(QuestObject source, String action)
    {
        List<String> lines;
        if (source instanceof Quest quest) lines = quest.description;
        else if (source instanceof Chapter chapter)
        {
            if (chapter.images.stream().anyMatch(image -> image.click.equals("zs:" + action) && !image.dev)) return true;
            lines = chapter.subtitle;
        }
        else return false;
        for (String line : lines)
            try { if (contains(new JsonParser().parse(line), action, 0)) return true; }
            catch (RuntimeException ignored) {}
        return false;
    }

    private static boolean contains(JsonElement element, String action, int depth)
    {
        if (depth > 16 || element == null) return false;
        if (element.isJsonObject())
        {
            JsonObject json = element.getAsJsonObject();
            if (matches(json, "type", "markdown") && json.has("text") && json.get("text").isJsonPrimitive())
                for (var block : QuestMarkdown.parse(json.get("text").getAsString()))
                {
                    if (block.kind() == QuestMarkdown.Kind.COMPONENT && contains(new JsonParser().parse(block.text()), action, depth + 1)) return true;
                    if (block.kind() != QuestMarkdown.Kind.CODE && block.kind() != QuestMarkdown.Kind.COMPONENT
                        && block.kind() != QuestMarkdown.Kind.IMAGE && block.kind() != QuestMarkdown.Kind.TABLE)
                        for (var span : QuestMarkdown.inline(block.text())) if (("zs:" + action).equals(span.link())) return true;
                }
            if (matches(json, "type", "zs") && matches(json, "value", action)) return true;
            if (json.has("clickEvent") && json.get("clickEvent").isJsonObject()
                && matches(json.getAsJsonObject("clickEvent"), "value", "zs:" + action)) return true;
            for (var entry : json.entrySet()) if (contains(entry.getValue(), action, depth + 1)) return true;
        }
        else if (element.isJsonArray()) for (JsonElement child : element.getAsJsonArray()) if (contains(child, action, depth + 1)) return true;
        return false;
    }
    private static boolean matches(JsonObject json, String key, String value)
    { return json.has(key) && json.get(key).isJsonPrimitive() && value.equals(json.get(key).getAsString()); }
}
