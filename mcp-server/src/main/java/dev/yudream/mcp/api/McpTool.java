package dev.yudream.mcp.api;

import com.google.gson.JsonObject;
import java.util.Objects;

/** Tool handlers execute on the declared game thread. They must not wait on that same thread. */
public final class McpTool {
    public enum Thread { IO, CLIENT, SERVER }
    @FunctionalInterface public interface Handler { McpResult call(JsonObject arguments) throws Exception; }
    private final String name, description;
    private final JsonObject schema;
    private final Thread thread;
    private final boolean readOnly;
    private final Handler handler;
    public McpTool(String name, String description, JsonObject schema, Thread thread, boolean readOnly, Handler handler) {
        if (!name.matches("[a-zA-Z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid tool name");
        this.name = name; this.description = Objects.requireNonNull(description);
        this.schema = schema.deepCopy(); this.thread = Objects.requireNonNull(thread);
        this.readOnly = readOnly; this.handler = Objects.requireNonNull(handler);
    }
    public String name() { return name; }
    public Thread thread() { return thread; }
    public McpResult invoke(JsonObject arguments) throws Exception {
        McpSchema.validate(schema, arguments); return handler.call(arguments.deepCopy());
    }
    public JsonObject definition() {
        JsonObject result = new JsonObject(); result.addProperty("name", name); result.addProperty("description", description);
        result.add("inputSchema", schema.deepCopy());
        JsonObject annotations = new JsonObject(); annotations.addProperty("readOnlyHint", readOnly);
        annotations.addProperty("destructiveHint", !readOnly); annotations.addProperty("openWorldHint", false);
        result.add("annotations", annotations); return result;
    }
}
