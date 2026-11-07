package com.feed_the_beast.ftbquests.integration.crafttweaker;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.events.*;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.task.CustomTask;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** All CraftTweaker references stay behind the optional mod check. */
public final class CraftTweakerIntegration
{
    public static void preInit()
    {
        MinecraftForge.EVENT_BUS.register(CraftTweakerIntegration.class);
        CraftTweakerAPI.tweaker.registerLoadStartedEvent(event -> {
            if (event.getLoader().getMainName().equals("crafttweaker")) QuestScripts.clearHandlers();
        });
        CraftTweakerAPI.tweaker.registerLoadFinishedEvent(event -> rebind());
    }
    static void rebind()
    {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || ServerQuestFile.INSTANCE == null) return;
        server.addScheduledTask(() -> {
            var file = ServerQuestFile.INSTANCE; if (file == null) return;
            for (var chapter : file.chapters) for (var quest : chapter.quests) for (var task : quest.tasks)
                if (task instanceof CustomTask custom)
                {
                    bind(custom);
                    new com.feed_the_beast.ftbquests.net.edit.MessageEditObjectResponse(custom).sendToAll();
                }
            file.clearCachedData();
        });
    }
    private static void bind(CustomTask task)
    {
        var definition = QuestScripts.find(QuestScripts.TASKS, task);
        if (definition == null) { if (task.check instanceof ScriptCheck) task.check = null; return; }
        task.maxProgress = definition.maximum(); task.checkTimer = definition.interval(); task.check = new ScriptCheck();
    }
    private static void failure(String source, Throwable error)
    {
        synchronized (QuestScripts.class)
        {
            if (!QuestScripts.ERRORS.add(source)) return;
            CraftTweakerAPI.logError("FTB Quests " + source + ": " + error.getMessage()); FTBQuests.LOGGER.error("ZenScript quest callback failed: " + source, error);
        }
    }
    private static final class ScriptCheck implements CustomTask.Check
    {
        @Override public void check(CustomTask.Data data, net.minecraft.entity.player.EntityPlayerMP player)
        {
            var definition = QuestScripts.find(QuestScripts.TASKS, data.task); if (definition == null) return;
            try { data.setProgress(definition.check().check(CraftTweakerMC.getIPlayer(player), data.progress, data.task.getMaxProgress())); }
            catch (RuntimeException | LinkageError ex) { failure("task " + data.task, ex); }
        }
    }
    @SubscribeEvent public static void customTask(CustomTaskEvent event)
    { if (!event.getTask().getQuestFile().isClient()) bind(event.getTask()); }
    @SubscribeEvent public static void customReward(CustomRewardEvent event)
    {
        var callback = QuestScripts.find(QuestScripts.REWARDS, event.getReward()); if (callback == null) return;
        try { callback.give(CraftTweakerMC.getIPlayer(event.getPlayer())); }
        catch (RuntimeException | LinkageError ex) { failure("reward " + event.getReward(), ex); }
    }
    @SubscribeEvent public static void completed(ObjectCompletedEvent<?> event)
    {
        var callback = QuestScripts.find(QuestScripts.COMPLETED, event.getObject()); if (callback == null) return;
        for (var player : event.getOnlineMembers())
            try { callback.complete(CraftTweakerMC.getIPlayer(player), event.getObject().getCodeString(), event.getData().getTeamUID()); }
            catch (RuntimeException | LinkageError ex) { failure("completion " + event.getObject(), ex); }
    }
    @SubscribeEvent public static void textClick(ScriptTextClickEvent event)
    {
        IQuestTextClick callback;
        synchronized (QuestScripts.class) { callback = QuestScripts.TEXT_CLICKS.get(event.getAction()); }
        if (callback == null) return;
        try { callback.click(CraftTweakerMC.getIPlayer(event.getPlayer()), event.getAction(), event.getSource().getCodeString(), event.getSource().getQuestFile().getData(event.getPlayer()).getTeamUID()); }
        catch (RuntimeException | LinkageError ex) { failure("text click " + event.getAction(), ex); }
    }
}
