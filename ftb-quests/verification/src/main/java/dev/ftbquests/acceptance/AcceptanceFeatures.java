package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.net.*;
import com.feed_the_beast.ftbquests.net.edit.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import com.feed_the_beast.ftbquests.quest.loot.*;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

/** Exercises the installation's actual networking and rendering, outside the shipped mod. */
final class AcceptanceFeatures
{
    private final Minecraft mc = Minecraft.getMinecraft();
    private final byte[] image;
    private final String imageName, probe;
    private int stage, ticks, wait, diamonds;
    private volatile int serverStage;
    private volatile Throwable failure;
    private BlockPos origin;
    private GuiQuestTree gui;
    private VideoBackend video;
    private static final int CHAPTER = 0x6f000001, QUEST = 0x6f000002, STRUCTURE = 0x6f000031;
    static int initialGold, initialEmeralds, initialRedstone;

    AcceptanceFeatures() throws Exception
    {
        Random random = new Random(20261104);
        var picture = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) picture.setRGB(x, y, random.nextInt());
        var out = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(picture, "png", out); image = out.toByteArray();
        AcceptanceMod.require(image.length > 32767, "Image fixture must exceed one client payload");
        imageName = QuestAssets.name(image, "png");
        byte[] noise = new byte[36000]; random.nextBytes(noise); probe = Base64.getEncoder().encodeToString(noise);
        MessagePasteImage.send(CHAPTER, -5, 0, image);
        NBTTagCompound edit = EditHistory.snapshot(ClientQuestFile.INSTANCE.getQuest(QUEST));
        edit.setString("large_edit_probe", probe); MessageEditPayload.send(1, QUEST, 0, 0, edit);
        NBTTagCompound clipboard = QuestCopies.capture(List.of(ClientQuestFile.INSTANCE.getQuest(QUEST)));
        clipboard.getTagList("quests", 10).getCompoundTagAt(0).setString("title", "Network Copy");
        clipboard.getTagList("quests", 10).getCompoundTagAt(0).setString("large_copy_probe", probe);
        MessagePasteObjects.send(CHAPTER, clipboard.getDouble("origin_x") + 8, 0, clipboard);
    }

    boolean tick() throws Exception
    {
        ticks++; if (failure != null) throw new AssertionError("Feature verification failed", failure);
        if (ticks > 2400) throw new AssertionError("Feature verification timeout: stage " + stage + "/" + serverStage);
        if (stage == 0 && ticks > 60)
        { stage = 1; server(() -> { verifyUploads(); fixtures(); serverStage = 1; }); }
        else if (stage == 1 && serverStage == 1 && ClientQuestFile.INSTANCE.getTask(STRUCTURE) != null)
        {
            Path cache = net.minecraftforge.fml.common.Loader.instance().getConfigDir().toPath().resolve("ftbquests/.asset-cache").resolve(imageName);
            AcceptanceMod.require(Files.exists(cache) && Arrays.equals(image, Files.readAllBytes(cache)), "Asset response did not survive chunk transfer");
            for (int i = 0; i < 3; i++)
                for (int duplicate = 0; duplicate < 2; duplicate++)
                    if (i == 2) new MessageClaimChoiceReward(0x6f000012 + i * 4, 0).sendToServer();
                    else new MessageClaimReward(0x6f000012 + i * 4, false).sendToServer();
            if (Boolean.getBoolean("ftbquests.acceptance.zs"))
            {
                var scripted = ClientQuestFile.INSTANCE.chapters.stream().flatMap(c -> c.quests.stream()).filter(q -> q.getTags().contains("zs_acceptance")).findFirst().orElseThrow();
                for (int duplicate = 0; duplicate < 2; duplicate++) new MessageClaimReward(scripted.rewards.get(0).id, false).sendToServer();
                new MessageScriptTextClick(QUEST, "acceptance").sendToServer();
                for (int duplicate = 0; duplicate < 2; duplicate++) new MessageScriptTextClick(scripted.id, "acceptance").sendToServer();
            }
            new MessageStructurePosition(STRUCTURE, origin).sendToServer(); new MessageSubmitTask(STRUCTURE).sendToServer();
            gui = ClientQuestFile.INSTANCE.questTreeGui; gui.open(ClientQuestFile.INSTANCE.getQuest(QUEST), false);
            wait = ticks; stage = 2;
        }
        else if (stage == 2 && ticks - wait > 60)
        { stage = 3; server(() -> { verifyRewardsAndWrongStructure(); mc.getIntegratedServer().getEntityWorld().setBlockState(origin.east(), Blocks.PLANKS.getDefaultState()); serverStage = 2; }); }
        else if (stage == 3 && serverStage == 2)
        { new MessageSubmitTask(STRUCTURE).sendToServer(); wait = ticks; stage = 4; }
        else if (stage == 4 && ticks - wait > 60)
        {
            // Full quest sync and editing-mode sync may replace the GUI after the test opened it.
            // Inspect the current screen's object, then allow its field subscription to synchronize.
            if (gui != ClientQuestFile.INSTANCE.questTreeGui)
            {
                gui = ClientQuestFile.INSTANCE.questTreeGui;
                gui.open(ClientQuestFile.INSTANCE.getQuest(QUEST), false);
                wait = ticks;
                return false;
            }
            AcceptanceMod.require("42".equals(DescriptionData.get("persisted:acceptance")), "Persisted variable was not synchronized");
            AcceptanceMod.require("17".equals(DescriptionData.get("player:score")), "Player data variable was not synchronized");
            AcceptanceMod.require(DescriptionComponents.substitute("${persisted:acceptance}/${player:score}").equals("42/17"), "Description substitution failed");
            AcceptanceMod.require(gui.viewQuestPanel.panelText.widgets.size() >= 7, "Description components were not created: " + gui.viewQuestPanel.panelText.widgets.size() + " from " + Arrays.toString(gui.getViewedQuest().getDescription()));
            for (var widget : gui.viewQuestPanel.panelText.widgets) AcceptanceMod.require(widget.width > 0 && widget.height > 0, "Description widget has zero size");
            CanvasMedia.drawFit("asset:" + imageName, 8, 8, 96, 96, 255);
            new EntityModelIcon("minecraft:pig", "{}").draw(108, 8, 48, 48);
            QuestFonts.get("minecraft:textures/font/ascii.png").drawString("Resource font", 8, 110, 0xFFFFFF);
            AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError() == 0, "New media components left OpenGL errors");
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir, "ftbquests-media-acceptance.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            stage = 5; server(() -> { verifyFinal(); serverStage = 3; });
        }
        else if (stage == 5 && serverStage == 3)
        {
            if (Boolean.getBoolean("ftbquests.acceptance.ponder"))
            {
                AcceptanceMod.require(PonderLink.open("minecraft:compass"), "Ponder link did not open the scene");
                wait = ticks; stage = 8; return false;
            }
            if (!Boolean.getBoolean("ftbquests.acceptance.video")) { CanvasMedia.invalidate(); return true; }
            video = VideoBackend.get("local:acceptance.avi"); wait = ticks; stage = 6;
        }
        else if (stage == 6)
        {
            video.draw(8, 8, 128, 128, "");
            if (video.status().isEmpty()) { video.toggle(); wait = ticks; stage = 7; }
            else if (ticks - wait > 400) throw new AssertionError("Video initialization failed: " + video.status());
        }
        else if (stage == 7)
        {
            video.draw(8, 8, 128, 128, "");
            if (video.hasFrame())
            {
                video.toggle(); video.replay();
                AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError() == 0, "VLC texture left GL errors");
                CanvasMedia.invalidate(); return true;
            }
            if (ticks - wait > 400) throw new AssertionError("Video produced no decoded frame: " + video.status());
        }
        else if (stage == 8 && ticks - wait > 60)
        {
            AcceptanceMod.require(mc.currentScreen.getClass().getName().endsWith(".PonderUI"), "Ponder screen closed unexpectedly");
            Object scene = mc.currentScreen.getClass().getMethod("getActiveScene").invoke(mc.currentScreen);
            AcceptanceMod.require((Integer)scene.getClass().getMethod("getCurrentTick").invoke(scene) > 0, "Ponder scene did not play");
            AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError() == 0, "Ponder scene left a GL error");
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir, "ftbquests-ponder-acceptance.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            CanvasMedia.invalidate(); return true;
        }
        return false;
    }

    private void server(Checked action)
    { mc.getIntegratedServer().addScheduledTask(() -> { try { action.run(); } catch (Throwable ex) { failure = ex; } }); }
    private interface Checked { void run() throws Exception; }
    private EntityPlayerMP player() { return mc.getIntegratedServer().getPlayerList().getPlayers().get(0); }
    private ServerQuestData data() { return (ServerQuestData)ServerQuestFile.INSTANCE.getData(player()); }
    private void verifyUploads() throws Exception
    {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        AcceptanceMod.require(Arrays.equals(image, Files.readAllBytes(file.getFolder().toPath().resolve("assets").resolve(imageName))), "Image upload corrupted bytes");
        AcceptanceMod.require(EditHistory.snapshot(file.getQuest(QUEST)).getString("large_edit_probe").equals(probe), "Fragmented edit was lost");
        Quest copied = file.getChapter(CHAPTER).quests.stream().filter(q -> q.title.equals("Network Copy")).findFirst().orElseThrow();
        AcceptanceMod.require(EditHistory.snapshot(copied).getString("large_copy_probe").equals(probe) && !copied.isComplete(data()), "Fragmented clipboard was lost or inherited progress");
    }
    private void fixtures() throws Exception
    {
        ServerQuestFile file = ServerQuestFile.INSTANCE; Chapter chapter = file.getChapter(CHAPTER);
        var player = player(); diamonds = countDiamonds(player);
        NBTUtils.getPersistedData(player, true).setInteger("acceptance", 42);
        player.getEntityData().setInteger("score", 17);
        Quest display = file.getQuest(QUEST); display.description.clear();
        display.viewBackgroundImage = "asset:" + imageName;
        KillTask modelTask = new KillTask(display); modelTask.id = file.newID(); modelTask.optionalTask = true; modelTask.onCreated(); file.refreshIDMap();
        display.description.add("{\"type\":\"heading\",\"text\":\"新版组件验收\"}");
        display.description.add("玩家数据 ${persisted:acceptance} / ${player:score}");
        display.description.add("{\"type\":\"image\",\"source\":\"asset:" + imageName + "\",\"width\":96,\"height\":64}");
        display.description.add("{\"type\":\"entity\",\"source\":\"minecraft:pig\",\"width\":64,\"height\":48}");
        display.description.add("{\"type\":\"font\",\"font\":\"minecraft:textures/font/ascii.png\",\"text\":\"Resource font\"}");
        display.description.add("{\"type\":\"table\",\"rows\":[[\"Key\",\"Value\"],[\"Assets\",\"Chunked PNG\"]]}");
        for (int i = 0; i < 3; i++)
        {
            Quest quest = new Quest(chapter); quest.id = 0x6f000010 + i * 4; quest.title = "Repeat " + i; quest.x = i * 2; quest.y = 3; quest.onCreated();
            Task task = new CheckmarkTask(quest); task.id = quest.id + 1; task.onCreated();
            RandomReward reward = i == 0 ? new RepeatableRewards.All(quest) : i == 1 ? new RepeatableRewards.Random(quest) : new RepeatableRewards.Choice(quest);
            reward.id = quest.id + 2; RewardTable table = new RewardTable(file); table.id = quest.id + 3; table.onCreated();
            ItemReward entry = new ItemReward(table.fakeQuest); entry.item = new ItemStack(Items.DIAMOND); entry.count = 2;
            table.rewards.add(new WeightedReward(entry, 1)); reward.table = table; reward.onCreated();
        }
        Quest quest = new Quest(chapter); quest.id = 0x6f000030; quest.title = "Structure"; quest.x = 0; quest.y = 6; quest.onCreated();
        StructureTask structure = new StructureTask(quest); structure.id = STRUCTURE;
        structure.template = JsonToNBT.getTagFromJson("{palette:[{Name:\"minecraft:cobblestone\"},{Name:\"minecraft:planks\"}],blocks:[{pos:[0,0,0],state:0},{pos:[1,0,0],state:1}]}"); structure.onCreated();
        origin = player.getPosition().add(3, 0, 3); player.world.setBlockState(origin, Blocks.COBBLESTONE.getDefaultState()); player.world.setBlockToAir(origin.east());
        file.refreshIDMap(); file.clearCachedData();
        for (int i = 0; i < 3; i++) file.getQuest(0x6f000010 + i * 4).changeProgress(data(), ChangeProgress.COMPLETE);
        AcceptanceMod.require(!structure.matches(player.world, origin), "Incorrect structure unexpectedly matched");
        // The programmatic authoring API must not accidentally copy text from synthetic IDs 1, 2, ... .
        Quest dynamic = QuestEdits.create(chapter, 4, 6, JsonToNBT.getTagFromJson("{title:\"Script-created\",tasks:[{type:\"checkmark\"}]}"));
        QuestEdits.edit(dynamic, JsonToNBT.getTagFromJson("{title:\"Script-edited\"}"));
        AcceptanceMod.require(dynamic.title.equals("Script-edited") && !dynamic.isComplete(data()), "Authoring API failed");
        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));
    }
    private void verifyRewardsAndWrongStructure()
    {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        AcceptanceMod.require(countDiamonds(player()) == diamonds + 6, "Repeat rewards were lost or duplicated");
        for (int i = 0; i < 3; i++) AcceptanceMod.require(!file.getQuest(0x6f000010 + i * 4).isComplete(data()), "Repeat quest did not reset");
        AcceptanceMod.require(data().getTaskData(file.getTask(STRUCTURE)).progress == 0, "Wrong structure completed through network");
    }
    private void verifyFinal() throws Exception
    {
        AcceptanceMod.require(ServerQuestFile.INSTANCE.getTask(STRUCTURE).isComplete(data()), "Correct structure did not complete through network");
        if (Boolean.getBoolean("ftbquests.acceptance.zs"))
        {
            Quest scripted = ServerQuestFile.INSTANCE.chapters.stream().flatMap(chapter -> chapter.quests.stream()).filter(q -> q.getTags().contains("zs_acceptance")).findFirst().orElseThrow();
            AcceptanceMod.require(scripted.title.equals("ZS edited") && scripted.isComplete(data()), "Actual ZenScript create/edit/custom-task callback failed");
            AcceptanceMod.require(scripted.tasks.get(0) instanceof CustomTask && ((CustomTask)scripted.tasks.get(0)).check != null && scripted.tasks.get(0).getMaxProgress() == 7, "ZenScript custom task event did not attach its callback");
            AcceptanceMod.require(countItem(player(), Items.EMERALD) == initialEmeralds + 1, "ZenScript completion callback was missing or duplicated");
            AcceptanceMod.require(countItem(player(), Items.GOLD_INGOT) == initialGold + 2, "ZenScript reward was missing or duplicated");
            AcceptanceMod.require(countItem(player(), Items.REDSTONE) == initialRedstone + 1, "ZenScript text callback allowed an undeclared source, missed the click or repeated within its cooldown");
        }
        NBTTagCompound progress = new NBTTagCompound(); data().writeData(progress);
        AcceptanceMod.roundTrip(); data().taskData.clear(); data().readData(progress);
        AcceptanceMod.require(ServerQuestFile.INSTANCE.getTask(STRUCTURE).isComplete(data()), "New task progress did not survive reload");
        verifyUploads();
    }
    private static int countDiamonds(EntityPlayerMP player)
    { int count = 0; for (ItemStack stack : player.inventory.mainInventory) if (stack.getItem() == Items.DIAMOND) count += stack.getCount(); return count; }
    static int countItem(EntityPlayerMP player, net.minecraft.item.Item item)
    { int count = 0; for (ItemStack stack : player.inventory.mainInventory) if (stack.getItem() == item) count += stack.getCount(); return count; }
}
