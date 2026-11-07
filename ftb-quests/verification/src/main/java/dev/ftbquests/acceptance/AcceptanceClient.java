package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.net.MessageSubmitTask;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.util.*;

@SideOnly(Side.CLIENT)
public final class AcceptanceClient
{
    private int ticks,step,wait;private volatile int serverStep;private volatile Throwable failure;private GuiQuestTree gui;
    private AcceptanceFeatures features;
    private static final int CHAPTER=0x6f000001,QUEST=0x6f000002,FIRST=0x6f000003,SECOND=0x6f000004;
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceClient());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        try
        {
            if(failure!=null)throw new AssertionError("Server acceptance failed",failure);
            if(ticks>8000)throw new AssertionError("Client acceptance timed out at step "+step+" server "+serverStep);
            if(step==0 && mc.currentScreen instanceof GuiMainMenu)
            {step=1;mc.launchIntegratedServer("quest-acceptance","Quest Acceptance",new WorldSettings(1,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1 && mc.player!=null && ClientQuestFile.exists() && mc.getIntegratedServer()!=null)
            {
                step=2;mc.getIntegratedServer().addScheduledTask(() -> {
                    try { setup(mc.getIntegratedServer().getPlayerList().getPlayers().get(0));serverStep=1;}catch(Throwable ex){failure=ex;}
                });
            }
            else if(step==2 && serverStep==1 && ClientQuestFile.INSTANCE.getQuest(QUEST)!=null)
            {
                gui=ClientQuestFile.INSTANCE.questTreeGui;gui.open(ClientQuestFile.INSTANCE.getChapter(CHAPTER),false);
                new MessageSubmitTask(SECOND).sendToServer();wait=ticks;step=3;
            }
            else if(step==3 && ticks-wait>20)
            {
                mc.getIntegratedServer().addScheduledTask(() -> {try{var data=ServerQuestFile.INSTANCE.getData(mc.getIntegratedServer().getPlayerList().getPlayers().get(0));AcceptanceMod.require(data.getTaskData(ServerQuestFile.INSTANCE.getTask(SECOND)).progress==0,"Sequential task bypassed through network");serverStep=2;}catch(Throwable ex){failure=ex;}});
                step=4;
            }
            else if(step==4 && serverStep==2){new MessageSubmitTask(FIRST).sendToServer();wait=ticks;step=5;}
            else if(step==5 && ticks-wait>20){new MessageSubmitTask(SECOND).sendToServer();wait=ticks;step=6;}
            else if(step==6 && ticks-wait>30)
            {
                mc.getIntegratedServer().addScheduledTask(() -> {
                    try { verify(mc.getIntegratedServer().getPlayerList().getPlayers().get(0));serverStep=3;}catch(Throwable ex){failure=ex;}
                });step=7;
            }
            else if(step==7 && serverStep==3 && ClientQuestFile.INSTANCE.getQuest(QUEST)!=null)
            {gui=ClientQuestFile.INSTANCE.questTreeGui;gui.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);VisualConfig.shaderBackground=true;gui.addZoom(1);wait=ticks;step=8;}
            else if(step==8 && ticks-wait>50)
            {
                AcceptanceMod.require(Math.abs(gui.getZoom()-20)<0.05,"Zoom did not settle");
                net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.gameDir,"ftbquests-acceptance.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError()==0,"OpenGL error while displaying quest book");
                features=new AcceptanceFeatures(); step=9;
            }
            else if(step==9 && features.tick())
            {AcceptanceMod.report("PASS","Existing quest round trip; real packet sequential/optional/flexible tasks; chapter prerequisites; claims/history/copy; >32 KiB PNG, edit and clipboard; three repeat reward modes and duplicate packets; persisted/player data; structure rejection/completion; script authoring API; rendered media/entity/font/shader; save/reload",null);step=100;mc.shutdown();}
        }
        catch(Throwable ex){if(step!=100){int failedStep=step;step=100;AcceptanceMod.report("FAIL","client step "+failedStep,ex);mc.shutdown();}}
    }
    private static void setup(EntityPlayerMP player) throws Exception
    {
        AcceptanceMod.roundTrip();ServerQuestFile file=ServerQuestFile.INSTANCE;
        AcceptanceFeatures.initialGold=AcceptanceFeatures.countItem(player, Items.GOLD_INGOT);
        AcceptanceFeatures.initialEmeralds=AcceptanceFeatures.countItem(player, Items.EMERALD);
        AcceptanceFeatures.initialRedstone=AcceptanceFeatures.countItem(player, Items.REDSTONE);
        Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="重制版功能验证";chapter.subtitle.add("# 章节说明");chapter.subtitle.add("{\"type\":\"keybind\",\"value\":\"key.jump\"}");chapter.onCreated();
        Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="顺序任务与可选任务";quest.requireSequentialTasks=com.feed_the_beast.ftblib.lib.config.EnumTristate.TRUE;quest.description.add("这是在正式 Cleanroom 0.4.0 中渲染的任务书。 &a保存重载检查通过。");quest.onCreated();
        Task first=new CheckmarkTask(quest);first.id=FIRST;first.onCreated();Task second=new CheckmarkTask(quest);second.id=SECOND;second.onCreated();Task optional=new CheckmarkTask(quest);optional.id=0x6f000005;optional.optionalTask=true;optional.onCreated();
        file.refreshIDMap();file.clearCachedData();NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));
    }
    private static void verify(EntityPlayerMP player) throws Exception
    {
        ServerQuestFile file=ServerQuestFile.INSTANCE;ServerQuestData data=(ServerQuestData)file.getData(player);Quest quest=file.getQuest(QUEST);
        AcceptanceMod.require(quest.isComplete(data),"Real sequential submissions did not complete");AcceptanceMod.require(data.getTaskData(file.getTask(0x6f000005)).progress==0,"Optional task was completed unexpectedly");
        NBTTagCompound before=EditHistory.snapshot(quest);quest.title="修改标题";EditHistory.record(file,quest,before);EditHistory.restore(file,false);AcceptanceMod.require(quest.title.equals(before.getString("title")),"Undo failed");EditHistory.restore(file,true);AcceptanceMod.require(quest.title.equals("修改标题"),"Redo failed");
        var copies=QuestCopies.paste(quest.chapter,QuestCopies.capture(Collections.singleton(quest)),4,0);Quest copy=(Quest)copies.get(0);AcceptanceMod.require(copy.id!=quest.id && !copy.isComplete(data),"Copy inherited original progress");
        Quest flexible=new Quest(quest.chapter);flexible.id=file.newID();flexible.title="弹性前置";flexible.dependencies.add(copy);flexible.progressionMode=ProgressionMode.FLEXIBLE;flexible.onCreated();file.refreshIDMap();Task task=new CheckmarkTask(flexible);task.id=file.newID();task.onCreated();file.refreshIDMap();
        AcceptanceMod.require(task.canStart(data),"Flexible task blocked before dependency");data.getTaskData(task).addProgress(1);AcceptanceMod.require(!flexible.isComplete(data),"Flexible quest completed before dependency");copy.changeProgress(data,ChangeProgress.COMPLETE);AcceptanceMod.require(flexible.isComplete(data),"Flexible completion did not propagate");
        ItemReward reward=new ItemReward(quest);reward.id=file.newID();reward.item=new ItemStack(Items.DIAMOND);reward.count=3;reward.onCreated();file.refreshIDMap();
        int count=countDiamonds(player);data.claimReward(player,reward,false);data.claimReward(player,reward,false);AcceptanceMod.require(countDiamonds(player)==count+3,"Reward was duplicated or lost");
        verifyChapterPrerequisites(file, data);
        NBTTagCompound progress=new NBTTagCompound();data.writeData(progress);file.saveNow();AcceptanceMod.roundTrip();data.taskData.clear();data.readData(progress);
        AcceptanceMod.require(file.getQuest(QUEST).isComplete(data),"Progress lost after save/reload");AcceptanceMod.require(data.isRewardClaimed(player.getUniqueID(),file.getReward(reward.id)),"Claim record lost after save/reload");
        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));
    }
    private static void verifyChapterPrerequisites(ServerQuestFile file, ServerQuestData data)
    {
        Quest prerequisite = new Quest(file.getChapter(CHAPTER)); prerequisite.id = file.newID(); prerequisite.onCreated(); file.refreshIDMap();
        Task before = new CheckmarkTask(prerequisite); before.id = file.newID(); before.onCreated(); file.refreshIDMap();
        Chapter gated = new Chapter(file); gated.id = file.newID(); gated.prerequisiteQuests.add(prerequisite.id); gated.onCreated(); file.refreshIDMap();
        Quest blocked = new Quest(gated); blocked.id = file.newID(); blocked.onCreated(); file.refreshIDMap();
        Task after = new CheckmarkTask(blocked); after.id = file.newID(); after.onCreated(); file.refreshIDMap(); file.clearCachedData();
        AcceptanceMod.require(!after.canStart(data) && !blocked.isVisible(data), "Chapter prerequisite did not block the task and its display");
        data.getTaskData(before).addProgress(1); file.clearCachedData();
        AcceptanceMod.require(after.canStart(data) && blocked.isVisible(data), "Chapter did not unlock after its prerequisite");
        int missing = file.newID(); gated.prerequisiteQuests.add(missing); file.clearCachedData();
        AcceptanceMod.require(!after.canStart(data), "Missing chapter prerequisite unlocked the task");
        gated.prerequisiteQuests.remove(Integer.valueOf(missing)); file.clearCachedData();
    }
    private static int countDiamonds(EntityPlayerMP player){int count=0;for(ItemStack stack:player.inventory.mainInventory)if(stack.getItem()==Items.DIAMOND)count+=stack.getCount();return count;}
}
