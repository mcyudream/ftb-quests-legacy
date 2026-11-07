package dev.yudream.mcp.api;

import com.google.gson.*;
import java.util.*;

/** Immutable module. The owner registers it only when its optional dependencies are loaded. */
public final class McpModule {
    private final String id, title, description, owner;
    private final Map<String, McpTool> tools;
    private McpModule(Builder b) {
        id = b.id; title = b.title; description = b.description; owner = b.owner;
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(b.tools));
    }
    public static Builder builder(String id, String title, String description, String owner) { return new Builder(id, title, description, owner); }
    public String id() { return id; }
    public McpTool tool(String name) {
        McpTool tool = tools.get(name); if (tool == null) throw new IllegalArgumentException("Unknown tool " + id + "/" + name); return tool;
    }
    public JsonArray definitions() { JsonArray result = new JsonArray(); tools.values().forEach(t -> result.add(t.definition())); return result; }
    public JsonObject descriptor() {
        JsonObject result = new JsonObject(); result.addProperty("id", id); result.addProperty("title", title);
        result.addProperty("description", description); result.addProperty("owner", owner);
        result.addProperty("endpoint", "/mcp/" + id); result.addProperty("toolCount", tools.size()); return result;
    }
    public static final class Builder {
        private final String id, title, description, owner;
        private final Map<String, McpTool> tools = new LinkedHashMap<>();
        private Builder(String id, String title, String description, String owner) {
            if (!id.matches("[a-z][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Invalid module ID");
            this.id = id; this.title = Objects.requireNonNull(title); this.description = Objects.requireNonNull(description); this.owner = Objects.requireNonNull(owner);
        }
        public Builder tool(McpTool tool) {
            if (tools.putIfAbsent(tool.name(), tool) != null) throw new IllegalArgumentException("Duplicate tool " + tool.name()); return this;
        }
        public McpModule build() { return new McpModule(this); }
    }
}
