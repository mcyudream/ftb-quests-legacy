package dev.yudream.mcp.api;

import com.google.gson.*;
import java.util.Base64;

/** MCP content blocks; image bytes are returned once, separately from structured metadata. */
public final class McpResult {
    private final JsonObject value = new JsonObject();
    private McpResult(JsonElement data, boolean error) {
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text"); text.addProperty("text", data.toString());
        content.add(text); value.add("content", content); value.addProperty("isError", error);
        if (data.isJsonObject()) value.add("structuredContent", data.deepCopy());
    }
    public static McpResult json(JsonElement data) { return new McpResult(data, false); }
    public static McpResult error(String message) {
        JsonObject error = new JsonObject(); error.addProperty("error", message);
        return new McpResult(error, true);
    }
    public McpResult png(byte[] data) {
        JsonObject image = new JsonObject(); image.addProperty("type", "image");
        image.addProperty("mimeType", "image/png"); image.addProperty("data", Base64.getEncoder().encodeToString(data));
        value.getAsJsonArray("content").add(image); return this;
    }
    public JsonObject toJson() { return value.deepCopy(); }
}
