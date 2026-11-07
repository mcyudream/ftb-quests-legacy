package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.events.ClearFileCacheEvent;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.integration.jei.QuestRegistry;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObject;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObjectResponse;
import com.feed_the_beast.ftbquests.net.edit.MessageHistory;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.feed_the_beast.ftbquests.quest.task.ItemTask;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.nio.file.*;
import java.util.*;

/** Measures real editor calls with the existing book and the actual JEI runtime installed. */
@SideOnly(Side.CLIENT)
public final class AcceptanceEditing
{
    private static final int CHAPTER = 0x6e000001, QUEST = 0x6e000002, SENTINEL = 0x6e000003;
    private int ticks, step, wait, cacheClears;
    private volatile int serverStep;
    private volatile Throwable failure;
    private GuiQuestTree gui;
    private Object sentinelWrapper;
    private Object sentinelButton;
    private int clearsAfterSend;
    private final Map<String, Object> metrics = new LinkedHashMap<>();

    public static void install() { MinecraftForge.EVENT_BUS.register(new AcceptanceEditing()); }
    @SubscribeEvent public void cleared(ClearFileCacheEvent event)
    { if (event.getFile().isClient()) cacheClears++; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || step == 100) return;
        Minecraft mc = Minecraft.getMinecraft(); ticks++;
        try
        {
            if (failure != null) throw new AssertionError("Server editor check failed", failure);
            if (ticks > 2400) throw new AssertionError("Editor check timed out at " + step);
            if (step == 0 && mc.currentScreen instanceof GuiMainMenu)
            {
                step = 1;
                mc.launchIntegratedServer("editing-acceptance", "Editing Acceptance", new WorldSettings(2, GameType.CREATIVE, false, false, WorldType.FLAT));
            }
            else if (step == 1 && mc.player != null && ClientQuestFile.exists())
            {
                step = 2;
                mc.getIntegratedServer().addScheduledTask(() -> {
                    try
                    {
                        AcceptanceMod.roundTrip();
                        ServerQuestFile file = ServerQuestFile.INSTANCE;
                        Chapter chapter = new Chapter(file); chapter.id = CHAPTER; chapter.title = "Editing latency"; chapter.onCreated();
                        for (int id : new int[]{QUEST, SENTINEL})
                        {
                            Quest quest = new Quest(chapter); quest.id = id; quest.title = "Editing " + id; quest.onCreated();
                            ItemTask task = new ItemTask(quest); task.id = id + 10; task.items.add(new ItemStack(Items.IRON_INGOT)); task.onCreated();
                            ItemReward reward = new ItemReward(quest); reward.id = id + 20; reward.item = new ItemStack(Items.DIAMOND); reward.onCreated();
                        }
                        file.refreshIDMap(); file.clearCachedData();
                        var player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                        NBTUtils.getPersistedData(player, true).setBoolean("ftbquests_editing_mode", true);
                        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));
                        serverStep = 1;
                    }
                    catch (Throwable ex) { failure = ex; }
                });
            }
            else if (step == 2 && serverStep == 1 && ClientQuestFile.INSTANCE.getQuest(QUEST) != null && ClientQuestFile.INSTANCE.canEdit())
            {
                gui = ClientQuestFile.INSTANCE.questTreeGui;
                gui.open(ClientQuestFile.INSTANCE.getQuest(QUEST), false);
                wait = ticks; step = 3;
            }
            else if (step == 3 && ticks - wait > 15)
            {
                AcceptanceMod.require(!QuestRegistry.INSTANCE.list.isEmpty(), "JEI runtime was not exercised");
                sentinelWrapper = wrapper(SENTINEL);
                sentinelButton = button(SENTINEL);
                AcceptanceMod.require(sentinelWrapper != null, "JEI sentinel recipe missing");
                Quest quest = ClientQuestFile.INSTANCE.getQuest(QUEST);
                int clears = cacheClears;
                List<Double> durations = new ArrayList<>();
                for (int i = 0; i < 12; i++)
                {
                    quest.title = "Fast edit " + i;
                    long started = System.nanoTime();
                    MessageEditObject.send(quest);
                    durations.add((System.nanoTime() - started) / 1_000_000D);
                }
                metrics.put("send_ms", durations);
                metrics.put("cache_clears_on_send", cacheClears - clears);
                metrics.put("unrelated_jei_recipe_preserved", sentinelWrapper == wrapper(SENTINEL));
                long started = System.nanoTime();
                quest.move(quest.chapter, 5, 4);
                metrics.put("move_ms", (System.nanoTime() - started) / 1_000_000D);
                metrics.put("move_visible_before_reply", quest.x == 5 && quest.y == 4);
                metrics.put("unrelated_button_preserved", sentinelButton == button(SENTINEL));
                clearsAfterSend = cacheClears;
                if (!Boolean.getBoolean("ftbquests.acceptance.baseline"))
                {
                    AcceptanceMod.require(quest.x == 5 && quest.y == 4, "Move waited for a reply");
                    AcceptanceMod.require(sentinelButton == button(SENTINEL), "Unrelated button was rebuilt");
                    AcceptanceMod.require(sentinelWrapper == wrapper(SENTINEL), "Unrelated JEI recipe was rebuilt while sending");
                }
                wait = ticks; step = 4;
            }
            else if (step == 4 && ticks - wait > 40)
            {
                Quest quest = ClientQuestFile.INSTANCE.getQuest(QUEST);
                AcceptanceMod.require(quest.title.equals("Fast edit 11") && quest.x == 5 && quest.y == 4, "Final editor state diverged");
                metrics.put("unrelated_jei_recipe_preserved_after_reply", sentinelWrapper == wrapper(SENTINEL));
                metrics.put("viewed_quest_preserved", gui.getViewedQuest() == quest && gui == ClientQuestFile.INSTANCE.questTreeGui);
                metrics.put("client_cache_clears_total", cacheClears);
                metrics.put("cache_clears_on_unchanged_replies", cacheClears - clearsAfterSend);
                if (!Boolean.getBoolean("ftbquests.acceptance.baseline"))
                {
                    AcceptanceMod.require(sentinelWrapper == wrapper(SENTINEL), "Unrelated JEI recipe was rebuilt after replies");
                    AcceptanceMod.require(cacheClears == clearsAfterSend, "Matching replies rebuilt the UI caches");
                    verifyReplyOrdering(quest);
                    quest.chapter.title = "Edited chapter";
                    double scroll = gui.viewQuestPanel.panelContent.getScrollY();
                    MessageEditObject.send(quest.chapter);
                    AcceptanceMod.require(gui == ClientQuestFile.INSTANCE.questTreeGui && gui.getViewedQuest() == quest, "Chapter edit replaced the screen or closed the details");
                    AcceptanceMod.require(gui.viewQuestPanel.panelContent.getScrollY() == scroll, "Chapter edit reset detail scrolling");
                    metrics.put("chapter_edit_preserves_details", true);
                }
                step = 5;
                mc.getIntegratedServer().addScheduledTask(() -> {
                    try
                    {
                        Quest saved = ServerQuestFile.INSTANCE.getQuest(QUEST);
                        AcceptanceMod.require(saved.title.equals("Fast edit 11") && saved.x == 5 && saved.y == 4, "Server missed rapid edits or move");
                        ServerQuestFile.INSTANCE.saveNow(); serverStep = 2;
                    }
                    catch (Throwable ex) { failure = ex; }
                });
            }
            else if (step == 5 && serverStep == 2)
            {
                if (Boolean.getBoolean("ftbquests.acceptance.baseline")) { finish(mc); return; }
                // The actual history packet must undo the latest chapter edit, then the move.
                new MessageHistory(false).sendToServer();
                new MessageHistory(false).sendToServer();
                wait = ticks; step = 6;
            }
            else if (step == 6 && ticks - wait > 20)
            {
                Quest quest = ClientQuestFile.INSTANCE.getQuest(QUEST);
                AcceptanceMod.require(quest.x == 0 && quest.y == 0 && quest.title.equals("Fast edit 11"), "Network undo state: " + quest.title + " at " + quest.x + "," + quest.y + " in " + quest.chapter.title);
                new MessageHistory(true).sendToServer();
                new MessageHistory(true).sendToServer();
                wait = ticks; step = 7;
            }
            else if (step == 7 && ticks - wait > 20)
            {
                Quest quest = ClientQuestFile.INSTANCE.getQuest(QUEST);
                AcceptanceMod.require(quest.x == 5 && quest.y == 4 && quest.chapter.title.equals("Edited chapter"), "Network redo did not restore the final state");
                metrics.put("network_undo_redo", true);
                step = 8;
                mc.getIntegratedServer().addScheduledTask(() -> {
                    var player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player, true).setBoolean("ftbquests_editing_mode", false);
                    serverStep = 3;
                });
            }
            else if (step == 8 && serverStep == 3)
            {
                Quest quest = ClientQuestFile.INSTANCE.getQuest(QUEST);
                quest.title = "Permission denied edit"; MessageEditObject.send(quest);
                quest.move(quest.chapter, 6, 7);
                wait = ticks; step = 9;
            }
            else if (step == 9 && ticks - wait > 20)
            {
                Quest quest = ClientQuestFile.INSTANCE.getQuest(QUEST);
                AcceptanceMod.require(quest.title.equals("Fast edit 11") && quest.x == 5 && quest.y == 4, "Rejected edit followed by move did not restore the authoritative state");
                metrics.put("real_permission_rejection", true);
                step = 10;
                mc.getIntegratedServer().addScheduledTask(() -> {
                    try { AcceptanceMod.roundTrip(); serverStep = 4; }
                    catch (Throwable ex) { failure = ex; }
                });
            }
            else if (step == 10 && serverStep == 4)
            {
                metrics.put("final_save_reload", true);
                finish(mc);
            }
        }
        catch (Throwable ex)
        {
            try { Files.writeString(Paths.get("editing-metrics.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(metrics)); }
            catch (Exception ignored) {}
            int failedStep = step; step = 100;
            AcceptanceMod.report("FAIL", "editor step " + failedStep, ex); mc.shutdown();
        }
    }
    private Object wrapper(int id)
    { return QuestRegistry.INSTANCE.list.stream().filter(w -> w.quest.id == id).findFirst().orElse(null); }
    private Object button(int id)
    { return gui.questPanel.widgets.stream().filter(w -> w instanceof com.feed_the_beast.ftbquests.gui.tree.ButtonQuest && ((com.feed_the_beast.ftbquests.gui.tree.ButtonQuest)w).quest.id == id).findFirst().orElse(null); }
    private void verifyReplyOrdering(Quest quest)
    {
        var sync = ClientQuestFile.INSTANCE.editSync;
        String finalTitle = quest.title;
        quest.title = "Old edit";
        long first = sync.begin(quest.id);
        var old = new MessageEditObjectResponse(quest, sync.session, first);
        quest.title = "Newer edit";
        long latest = sync.begin(quest.id);
        var current = new MessageEditObjectResponse(quest, sync.session, latest);
        old.onMessage();
        AcceptanceMod.require(quest.title.equals("Newer edit"), "Old reply overwrote a newer edit");
        current.onMessage(); old.onMessage();
        AcceptanceMod.require(quest.title.equals("Newer edit"), "Duplicate old reply overwrote the acknowledged edit");
        quest.title = finalTitle;
        long rejected = sync.begin(quest.id);
        var correction = new MessageEditObjectResponse(quest, sync.session, rejected);
        quest.title = "Rejected edit";
        correction.onMessage();
        AcceptanceMod.require(quest.title.equals(finalTitle), "Rejected edit was not rolled back");
        metrics.put("stale_reply_and_rejection", true);
    }
    private void finish(Minecraft mc) throws Exception
    {
        Files.writeString(Paths.get("editing-metrics.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(metrics));
        AcceptanceMod.report("PASS", "Editing latency; rapid edits, immediate moves, ordered replies, real JEI, chapter context, network undo/redo, save/reload", null);
        step = 100; mc.shutdown();
    }
}
