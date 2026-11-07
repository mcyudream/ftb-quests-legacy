package dev.yudream.mcp.api;

import com.google.gson.JsonArray;
import java.util.*;

/** Public registration entry point; safe during preInit/init/postInit. No Minecraft client classes. */
public final class McpRegistry {
    private static final McpRegistry GLOBAL = new McpRegistry();
    private final Map<String, McpModule> modules = new LinkedHashMap<>();
    private Set<String> disabled = Set.of();
    public static McpRegistry global() { return GLOBAL; }
    public synchronized void register(McpModule module) {
        if (modules.putIfAbsent(module.id(), module) != null) throw new IllegalArgumentException("Duplicate MCP module " + module.id());
    }
    public synchronized void setDisabled(Collection<String> ids) { disabled = Set.copyOf(ids); }
    public synchronized McpModule module(String id) {
        McpModule module = modules.get(id);
        if (module == null || disabled.contains(id)) throw new IllegalArgumentException("Unknown or disabled module " + id);
        return module;
    }
    public synchronized JsonArray descriptors() {
        JsonArray result = new JsonArray(); modules.values().stream().filter(m -> !disabled.contains(m.id())).forEach(m -> result.add(m.descriptor())); return result;
    }
}
