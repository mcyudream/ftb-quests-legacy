package com.feed_the_beast.ftbquests.integration.crafttweaker;

import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.util.*;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.data.IData;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.api.player.IPlayer;
import crafttweaker.api.world.IWorld;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import stanhebben.zenscript.annotations.*;
import java.util.*;

/** Optional ZenScript API; definitions retain the Nova 32-bit IDs and SNBT field names. */
@ZenRegister
@ZenClass("mods.ftbquests.Quests")
public final class QuestScripts
{
    static final Map<String, CustomDefinition> TASKS = new LinkedHashMap<>();
    static final Map<String, IQuestReward> REWARDS = new LinkedHashMap<>();
    static final Map<String, IQuestCompleted> COMPLETED = new LinkedHashMap<>();
    static final Map<String, IQuestTextClick> TEXT_CLICKS = new LinkedHashMap<>();
    static final Set<String> ERRORS = new HashSet<>();
    record CustomDefinition(IQuestTaskCheck check, int interval, long maximum) {}

    static ServerQuestFile file(IWorld world)
    {
        World actual = CraftTweakerMC.getWorld(Objects.requireNonNull(world, "world"));
        if (actual == null || actual.isRemote || actual.getMinecraftServer() == null || !actual.getMinecraftServer().isCallingFromMinecraftThread())
            throw new IllegalStateException("FTB Quests scripts must run in a server event");
        return Objects.requireNonNull(ServerQuestFile.INSTANCE, "Quest file is not loaded");
    }
    private static EntityPlayerMP player(IPlayer player)
    {
        if (!(CraftTweakerMC.getPlayer(player) instanceof EntityPlayerMP actual)) throw new IllegalArgumentException("Expected a server player");
        file(CraftTweakerMC.getIWorld(actual.world)); return actual;
    }
    private static QuestObjectBase object(ServerQuestFile file, String id)
    {
        QuestObjectBase object = file.getBase(file.getID(id));
        if (QuestObjectBase.isNull(object)) throw new IllegalArgumentException("Unknown quest object: " + id);
        return object;
    }
    private static NBTTagCompound compound(IData data)
    {
        if (data == null || !(CraftTweakerMC.getNBT(data) instanceof NBTTagCompound compound)) throw new IllegalArgumentException("Expected a data map");
        return compound.copy();
    }
    @ZenMethod public static boolean exists(IWorld world, String id)
    {
        World actual = CraftTweakerMC.getWorld(world);
        if (actual == null || actual.isRemote || ServerQuestFile.INSTANCE == null) return false;
        return !QuestObjectBase.isNull(file(world).getBase(file(world).getID(id)));
    }
    @ZenMethod public static String createQuest(IWorld world, String chapterID, double x, double y, IData definition)
    {
        ServerQuestFile file = file(world); Chapter chapter = file.getChapter(file.getID(chapterID));
        if (chapter == null) throw new IllegalArgumentException("Unknown chapter: " + chapterID);
        return QuestEdits.create(chapter, x, y, compound(definition)).getCodeString();
    }
    @ZenMethod public static String[] findObjects(IWorld world, String selector)
    {
        String key = selector(selector); ServerQuestFile file = file(world);
        return file.getAllObjects().stream().filter(object -> key.startsWith("tag:") ? object.getTags().contains(key.substring(4)) : object.getCodeString().equals(key))
            .map(object -> object.getCodeString()).sorted().toArray(String[]::new);
    }
    @ZenMethod public static void editObject(IWorld world, String objectID, IData patch)
    { QuestEdits.edit(object(file(world), objectID), compound(patch)); }
    @ZenMethod public static IData getObjectData(IWorld world, String objectID)
    { return CraftTweakerMC.getIData(EditHistory.snapshot(object(file(world), objectID))); }
    @ZenMethod public static boolean isComplete(IPlayer player, String objectID)
    {
        EntityPlayerMP actual = player(player); ServerQuestFile file = ServerQuestFile.INSTANCE;
        return object(file, objectID) instanceof QuestObject object && object.isComplete(file.getData(actual));
    }
    @ZenMethod public static long getProgress(IPlayer player, String taskID)
    {
        EntityPlayerMP actual = player(player); ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (!(object(file, taskID) instanceof Task task)) throw new IllegalArgumentException("Expected a task ID");
        return file.getData(actual).getTaskData(task).progress;
    }
    @ZenMethod public static void setProgress(IPlayer player, String taskID, long progress)
    {
        EntityPlayerMP actual = player(player); ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (!(object(file, taskID) instanceof Task task)) throw new IllegalArgumentException("Expected a task ID");
        QuestData data = file.getData(actual);
        if (task.canStart(data)) data.getTaskData(task).setProgress(progress);
    }
    @ZenMethod public static int getTeamUID(IPlayer player)
    { return ServerQuestFile.INSTANCE.getData(player(player)).getTeamUID(); }

    static String selector(String selector)
    {
        if (selector.startsWith("tag:") && selector.length() > 4) return selector;
        if (!selector.matches("[0-9a-fA-F]{8}")) throw new IllegalArgumentException("Expected an eight-digit ID or tag:name");
        return selector.toLowerCase(Locale.ROOT);
    }
    static synchronized <T> T find(Map<String, T> handlers, QuestObjectBase object)
    {
        T exact = handlers.get(object.getCodeString()); if (exact != null) return exact;
        for (String tag : new TreeSet<>(object.getTags())) { T match = handlers.get("tag:" + tag); if (match != null) return match; }
        return null;
    }
    @ZenMethod public static synchronized void customTask(String selector, IQuestTaskCheck check, int interval, long maximum)
    {
        if (interval < 1 || interval > 1200 || maximum < 1) throw new IllegalArgumentException("Interval must be 1..1200 ticks; maximum must be positive");
        TASKS.put(selector(selector), new CustomDefinition(Objects.requireNonNull(check), interval, maximum));
        CraftTweakerIntegration.rebind();
    }
    @ZenMethod public static synchronized void customReward(String selector, IQuestReward reward)
    { REWARDS.put(selector(selector), Objects.requireNonNull(reward)); }
    @ZenMethod public static synchronized void onCompleted(String selector, IQuestCompleted callback)
    { COMPLETED.put(selector(selector), Objects.requireNonNull(callback)); }
    @ZenMethod public static synchronized void onTextClick(String action, IQuestTextClick callback)
    {
        if (!ScriptTextActions.valid(action)) throw new IllegalArgumentException("Expected an action of 1..128 letters, digits, _, ., :, / or -");
        TEXT_CLICKS.put(action, Objects.requireNonNull(callback));
    }
    @ZenMethod public static synchronized void clearHandlers()
    { TASKS.clear(); REWARDS.clear(); COMPLETED.clear(); TEXT_CLICKS.clear(); ERRORS.clear(); CraftTweakerIntegration.rebind(); }
}
