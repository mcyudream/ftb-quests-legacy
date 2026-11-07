package com.feed_the_beast.ftbquests.integration.mcp;

import com.google.gson.*;
import dev.yudream.mcp.api.*;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import com.feed_the_beast.ftbquests.quest.reward.RewardType;
import com.feed_the_beast.ftbquests.net.edit.*;
import com.feed_the_beast.ftbquests.util.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;

/** Optional MCP authoring adapter. Every book read/write runs on the local server thread. */
public final class FtbQuestsMcpModule {
    private FtbQuestsMcpModule() {}
    public static void register(boolean client) {
        var module = McpModule.builder("ftbquests", "FTB Quests 任务编辑", "服务端任务编辑、保存与同步；支持章节、任务、任务条件、奖励、布局与撤销。多人游戏请连接专服 MCP，客户端端口只能编辑本地集成服务端。", FTBQuests.MOD_ID);
        add(module, "chapters", "List chapter IDs and titles in the current server quest book.", McpSchema.object(), true, a -> {
            JsonArray chapters = new JsonArray(); for (Chapter chapter : file().chapters) { JsonObject entry = summary(chapter); entry.addProperty("quests", chapter.quests.size()); chapters.add(entry); }
            JsonObject result = new JsonObject(); result.add("chapters", chapters); result.addProperty("folder", file().folderName); return McpResult.json(result);
        });
        add(module, "chapter_get", "Read a chapter's quest nodes, positions and dependency IDs.", idSchema(), true, a -> {
            Chapter chapter = chapter(a.get("id").getAsString()); JsonObject result = summary(chapter); JsonArray quests = new JsonArray();
            chapter.quests.forEach(q -> quests.add(summary(q))); result.add("quests", quests); return McpResult.json(result);
        });
        add(module, "object_get", "Read complete object properties as SNBT plus task/reward child IDs. Use SNBT patches with object_edit.", idSchema(), true, a -> {
            QuestObjectBase object = object(a.get("id").getAsString()); JsonObject result = summary(object); result.addProperty("snbt", EditHistory.snapshot(object).toString());
            if (object instanceof Quest quest) {
                JsonArray tasks = new JsonArray(), rewards = new JsonArray(); quest.tasks.forEach(t -> tasks.add(summary(t))); quest.rewards.forEach(r -> rewards.add(summary(r)));
                result.add("tasks", tasks); result.add("rewards", rewards);
            } return McpResult.json(result);
        });
        add(module, "types", "List registered task and reward type IDs for object_create and quest_create definitions.", McpSchema.object(), true, a -> {
            JsonObject result = new JsonObject(); JsonArray tasks = new JsonArray(), rewards = new JsonArray();
            TaskType.getRegistry().getKeys().stream().sorted(Comparator.comparing(Object::toString)).forEach(id -> tasks.add(id.toString()));
            RewardType.getRegistry().getKeys().stream().sorted(Comparator.comparing(Object::toString)).forEach(id -> rewards.add(id.toString()));
            result.add("tasks", tasks); result.add("rewards", rewards); return McpResult.json(result);
        });
        add(module, "chapter_create", "Create a chapter with a title.", McpSchema.object().required("title", McpSchema.string(512)), false, a -> {
            ServerQuestFile file = file(); Chapter chapter = new Chapter(file); chapter.title = a.get("title").getAsString(); chapter.id = file.newID();
            chapter.onCreated(); file.registerCreatedObject(chapter); EditHistory.clear(file); file.save(); new MessageCreateObjectResponse(chapter, null).sendToAll(); file.saveNow(); return McpResult.json(summary(chapter));
        });
        add(module, "quest_create", "Create a quest at x,y in a chapter. Optional definition is SNBT with title, description, tasks and rewards, using FTB Quests' native format.",
            McpSchema.object().required("chapter", McpSchema.string(16)).required("x", coord()).required("y", coord()).optional("definition", McpSchema.string(131072)), false, a -> {
                NBTTagCompound definition = a.has("definition") ? JsonToNBT.getTagFromJson(a.get("definition").getAsString()) : new NBTTagCompound();
                Quest quest = QuestEdits.create(chapter(a.get("chapter").getAsString()), a.get("x").getAsDouble(), a.get("y").getAsDouble(), definition); file().saveNow(); return McpResult.json(summary(quest));
            });
        add(module, "object_create", "Add a typed task or reward to an existing quest. Properties are SNBT; type is a registered type ID from types.",
            McpSchema.object().required("quest", McpSchema.string(16)).required("kind", McpSchema.choice("task", "reward")).required("type", McpSchema.string(256)).optional("properties", McpSchema.string(131072)), false, a -> {
                ServerQuestFile file = file(); QuestObjectBase parent = object(a.get("quest").getAsString()); if (!(parent instanceof Quest)) throw new IllegalArgumentException("Expected a quest parent");
                boolean task = a.get("kind").getAsString().equals("task"); String type = a.get("type").getAsString(); ResourceLocation key = new ResourceLocation(type.contains(":") ? type : "ftbquests:" + type);
                if (!(task ? TaskType.getRegistry().containsKey(key) : RewardType.getRegistry().containsKey(key))) throw new IllegalArgumentException("Unknown task/reward type");
                NBTTagCompound properties = a.has("properties") ? JsonToNBT.getTagFromJson(a.get("properties").getAsString()) : new NBTTagCompound();
                rejectStructural(properties); NBTTagCompound extra = new NBTTagCompound(); extra.setString("type", key.toString());
                QuestObjectBase created = file.create(task ? QuestObjectType.TASK : QuestObjectType.REWARD, parent.id, extra);
                created.readDataPreservingUnknown(properties); created.id = file.newID(); created.onCreated(); file.registerCreatedObject(created); EditHistory.clear(file); file.save();
                new MessageCreateObjectResponse(created, extra).sendToAll(); file.saveNow(); return McpResult.json(summary(created));
            });
        add(module, "object_edit", "Merge a native SNBT property patch with validation, shared undo history, persistence and synchronization. Structural fields are rejected.",
            idSchema().required("patch", McpSchema.string(131072)), false, a -> {
                QuestObjectBase object = object(a.get("id").getAsString()); NBTTagCompound patch = JsonToNBT.getTagFromJson(a.get("patch").getAsString()); rejectStructural(patch);
                QuestEdits.edit(object, patch); file().saveNow(); return McpResult.json(summary(object));
            });
        JsonObject position = McpSchema.object().required("id", McpSchema.string(16)).required("x", coord()).required("y", coord()).build();
        add(module, "layout", "Apply up to 512 explicit quest positions as one undoable change. Validates every ID before changing anything.",
            McpSchema.object().required("positions", McpSchema.array(position, 1, 512)), false, a -> {
                Map<Quest, double[]> positions = new LinkedHashMap<>();
                for (JsonElement entry : a.getAsJsonArray("positions")) {
                    JsonObject p = entry.getAsJsonObject(); QuestObjectBase object = object(p.get("id").getAsString()); if (!(object instanceof Quest quest)) throw new IllegalArgumentException("Expected quest node");
                    if (positions.put(quest, new double[]{p.get("x").getAsDouble(), p.get("y").getAsDouble()}) != null) throw new IllegalArgumentException("Duplicate quest ID");
                } return applyLayout(positions);
            });
        add(module, "layout_grid", "Arrange a chapter's quests on a grid, preserving quest order and dependencies. One undoable operation.",
            McpSchema.object().required("chapter", McpSchema.string(16)).optional("columns", McpSchema.integer(1, 64)).optional("x", coord()).optional("y", coord())
                .optional("spacingX", McpSchema.number(0.25, 100)).optional("spacingY", McpSchema.number(0.25, 100)), false, a -> {
                    Chapter chapter = chapter(a.get("chapter").getAsString()); if (chapter.quests.size() > 512) throw new IllegalArgumentException("At most 512 quests in one layout");
                    Map<Quest, double[]> positions = new LinkedHashMap<>(); int columns = a.has("columns") ? a.get("columns").getAsInt() : 6, i = 0;
                    double x = number(a, "x", 0), y = number(a, "y", 0), sx = number(a, "spacingX", 2), sy = number(a, "spacingY", 2);
                    for (Quest quest : chapter.quests) { double px = x + (i % columns) * sx, py = y + (i / columns) * sy;
                        if (Math.abs(px) > 100000 || Math.abs(py) > 100000) throw new IllegalArgumentException("Resulting coordinate out of range"); positions.put(quest, new double[]{px, py}); i++; }
                    return applyLayout(positions);
                });
        add(module, "history", "Undo or redo the latest shared quest editor property/layout operation. Creation clears history.",
            McpSchema.object().required("action", McpSchema.choice("undo", "redo")), false, a -> {
                var changed = EditHistory.restore(file(), a.get("action").getAsString().equals("redo")); changed.forEach(o -> new MessageEditObjectResponse(o).sendToAll()); file().saveNow();
                JsonObject result = new JsonObject(); result.addProperty("changed", changed.size()); return McpResult.json(result);
            });
        if (client) FtbQuestsMcpClient.addTools(module);
        McpRegistry.global().register(module.build());
    }
    private static void add(McpModule.Builder b, String name, String description, McpSchema schema, boolean readOnly, McpTool.Handler handler) {
        b.tool(new McpTool(name, description, schema.build(), McpTool.Thread.SERVER, readOnly, handler));
    }
    private static McpSchema idSchema() { return McpSchema.object().required("id", McpSchema.string(16)); }
    private static JsonObject coord() { return McpSchema.number(-100000, 100000); }
    private static double number(JsonObject a, String key, double fallback) { return a.has(key) ? a.get(key).getAsDouble() : fallback; }
    private static ServerQuestFile file() { if (ServerQuestFile.INSTANCE == null) throw new IllegalStateException("Quest book is not loaded; enter a world first"); return ServerQuestFile.INSTANCE; }
    private static QuestObjectBase object(String code) {
        if (!code.matches("[0-9a-fA-F]{1,8}")) throw new IllegalArgumentException("Use a hexadecimal object ID returned by this module");
        QuestObjectBase object = file().getBase(file().getID(code)); if (object == null || object == file()) throw new IllegalArgumentException("Unknown object " + code); return object;
    }
    private static Chapter chapter(String code) { QuestObjectBase object = object(code); if (!(object instanceof Chapter)) throw new IllegalArgumentException("Expected chapter"); return (Chapter) object; }
    private static void rejectStructural(NBTTagCompound patch) {
        for (String key : new String[]{"tasks", "rewards", "uid", "id", "type", "chapters", "reward_tables", "parent", "source_uid"})
            if (patch.hasKey(key)) throw new IllegalArgumentException("Structural field is not editable: " + key);
    }
    private static JsonObject summary(QuestObjectBase object) {
        JsonObject result = new JsonObject(); result.addProperty("id", object.getCodeString()); result.addProperty("kind", object.getObjectType().getId()); result.addProperty("title", object.title);
        result.addProperty("parent", QuestObjectBase.getCodeString(object.getParentID()));
        if (object instanceof Quest quest) { result.addProperty("x", quest.x); result.addProperty("y", quest.y); JsonArray dependencies = new JsonArray(); quest.dependencies.forEach(d -> dependencies.add(d.getCodeString())); result.add("dependencies", dependencies); }
        return result;
    }
    private static McpResult applyLayout(Map<Quest, double[]> positions) {
        ServerQuestFile file = file(); Map<Integer, NBTTagCompound> before = new LinkedHashMap<>(); positions.keySet().forEach(q -> before.put(q.id, EditHistory.snapshot(q)));
        positions.forEach((q, p) -> { q.x = p[0]; q.y = p[1]; }); EditHistory.record(file, before); file.clearCachedData(); file.save();
        positions.keySet().forEach(q -> new MessageEditObjectResponse(q).sendToAll()); file.saveNow();
        JsonObject result = new JsonObject(); result.addProperty("changed", positions.size()); return McpResult.json(result);
    }
}
