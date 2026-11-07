package dev.yudream.mcp.api;

import com.google.gson.*;
import java.math.BigDecimal;

/** Small JSON Schema builder and validator for the schemas produced by this API. */
public final class McpSchema {
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();
    public static McpSchema object() { return new McpSchema(); }
    public McpSchema required(String key, JsonObject schema) { properties.add(key, schema); required.add(key); return this; }
    public McpSchema optional(String key, JsonObject schema) { properties.add(key, schema); return this; }
    public JsonObject build() {
        JsonObject schema = type("object"); schema.add("properties", properties.deepCopy());
        schema.add("required", required.deepCopy()); schema.addProperty("additionalProperties", false); return schema;
    }
    public static JsonObject type(String type) { JsonObject s = new JsonObject(); s.addProperty("type", type); return s; }
    public static JsonObject string(int max) { JsonObject s = type("string"); s.addProperty("minLength", 1); s.addProperty("maxLength", max); return s; }
    public static JsonObject text(int max) { JsonObject s = type("string"); s.addProperty("maxLength", max); return s; }
    public static JsonObject integer(int min, int max) { return range("integer", min, max); }
    public static JsonObject number(double min, double max) { return range("number", min, max); }
    private static JsonObject range(String type, double min, double max) {
        JsonObject s = type(type); s.addProperty("minimum", min); s.addProperty("maximum", max); return s;
    }
    public static JsonObject choice(String... values) {
        JsonObject s = type("string"); JsonArray a = new JsonArray(); for (String value : values) a.add(value); s.add("enum", a); return s;
    }
    public static JsonObject array(JsonObject items, int min, int max) {
        JsonObject s = type("array"); s.add("items", items); s.addProperty("minItems", min); s.addProperty("maxItems", max); return s;
    }
    public static void validate(JsonObject schema, JsonElement value) { validate(schema, value, "arguments", 0); }
    private static void validate(JsonObject s, JsonElement v, String path, int depth) {
        if (depth > 32 || v == null || v.isJsonNull()) throw new IllegalArgumentException(path + ": null or too deeply nested");
        String type = s.has("type") ? s.get("type").getAsString() : "any";
        boolean valid = switch (type) {
            case "object" -> v.isJsonObject(); case "array" -> v.isJsonArray();
            case "string" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
            case "boolean" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean();
            case "integer", "number" -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber();
            default -> true;
        };
        if (!valid) throw new IllegalArgumentException(path + ": expected " + type);
        if (s.has("enum") && !s.getAsJsonArray("enum").contains(v)) throw new IllegalArgumentException(path + ": unsupported value");
        if (type.equals("integer") || type.equals("number")) {
            BigDecimal number;
            try { number = v.getAsBigDecimal(); } catch (NumberFormatException ex) { throw new IllegalArgumentException(path + ": expected finite number"); }
            if (type.equals("integer") && number.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException(path + ": expected integer");
            if ((s.has("minimum") && number.compareTo(s.get("minimum").getAsBigDecimal()) < 0)
                || (s.has("maximum") && number.compareTo(s.get("maximum").getAsBigDecimal()) > 0)) throw new IllegalArgumentException(path + ": out of range");
        } else if (type.equals("string")) {
            int length = v.getAsString().length();
            if ((s.has("minLength") && length < s.get("minLength").getAsInt()) || (s.has("maxLength") && length > s.get("maxLength").getAsInt()))
                throw new IllegalArgumentException(path + ": invalid length");
        } else if (type.equals("array")) {
            JsonArray a = v.getAsJsonArray();
            if ((s.has("minItems") && a.size() < s.get("minItems").getAsInt()) || (s.has("maxItems") && a.size() > s.get("maxItems").getAsInt()))
                throw new IllegalArgumentException(path + ": invalid array size");
            if (s.has("items")) for (int i = 0; i < a.size(); i++) validate(s.getAsJsonObject("items"), a.get(i), path + "[" + i + "]", depth + 1);
        } else if (type.equals("object")) {
            JsonObject o = v.getAsJsonObject();
            if (s.has("required")) for (JsonElement key : s.getAsJsonArray("required"))
                if (!o.has(key.getAsString())) throw new IllegalArgumentException(path + ": missing " + key.getAsString());
            JsonObject properties = s.has("properties") ? s.getAsJsonObject("properties") : new JsonObject();
            for (var entry : o.entrySet()) {
                if (properties.has(entry.getKey())) validate(properties.getAsJsonObject(entry.getKey()), entry.getValue(), path + "." + entry.getKey(), depth + 1);
                else if (s.has("additionalProperties") && !s.get("additionalProperties").getAsBoolean()) throw new IllegalArgumentException(path + ": unknown field " + entry.getKey());
            }
        }
    }
}
