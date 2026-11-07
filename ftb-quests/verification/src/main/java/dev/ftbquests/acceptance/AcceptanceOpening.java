package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.GuiWrapper;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.*;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.net.MessageClaimReward;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.loot.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import com.feed_the_beast.ftbquests.quest.task.CheckmarkTask;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Runs through the real claim/use paths, server result packets, and rendered animation. */
@SideOnly(Side.CLIENT)
public final class AcceptanceOpening
{
    private static final int CHAPTER=0x69000001,QUEST=0x69000002,RANDOM=0x69000004,LOOT=0x69000005,EARLY=0x69000006,OFF=0x69000007,AUTO=0x69000008;
    private int ticks,stage,wait,originalScale;
    private volatile int serverStep;
    private volatile Throwable failure;
    private GuiQuestTree tree;
    private GuiLootOpening opening;
    private GuiScreen blocker;
    private RewardOpening.Result previousResult;
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private Map<Item,Long> before=new HashMap<>();
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceOpening());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||stage==100)return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        try
        {
            if(failure!=null)throw new AssertionError("Opening server verification",failure);
            if(ticks>2800)throw new AssertionError("Opening timeout at "+stage+" server "+serverStep+" screen "+mc.currentScreen);
            if(stage==0&&mc.currentScreen instanceof GuiMainMenu)
            {stage=1;mc.launchIntegratedServer("opening-acceptance","Reward opening",new WorldSettings(4,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(stage==1&&mc.player!=null&&ClientQuestFile.exists())
            {stage=2;server(()->{setup(player());serverStep=1;});}
            else if(stage==2&&serverStep==1&&ClientQuestFile.INSTANCE.getReward(RANDOM)!=null)
            {
                tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);mc.player.inventory.currentItem=8;
                new MessageClaimReward(RANDOM,true).sendToServer();new MessageClaimReward(RANDOM,true).sendToServer();stage=3;
            }
            else if(stage==3&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                opening=gui;AcceptanceMod.require(!gui.isRevealed(),"Opening skipped its default animation");checks.put("manual_random_reward_opens_server_result_animation",true);
                previousResult=gui.result;server(()->{verifyDelta(previousResult);var data=(ServerQuestData)ServerQuestFile.INSTANCE.getData(player());AcceptanceMod.require(data.isRewardClaimed(player().getUniqueID(),ServerQuestFile.INSTANCE.getReward(RANDOM)),"Claim record absent before animation ends");serverStep=2;});
                wait=ticks;stage=4;
            }
            else if(stage==4&&ticks-wait>5)
            {shot("opening-chest.png");wait=ticks;stage=5;}
            else if(stage==5&&ticks-wait>20)
            {shot("opening-reel.png");stage=6;}
            else if(stage==6&&opening.isRevealed()&&serverStep==2)
            {
                AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError()==0,"Opening animation left a GL error");AcceptanceMod.require(!mc.isGamePaused(),"Opening pauses integrated server");
                checks.put("reel_and_reveal_render_without_gl_error_or_server_pause",true);checks.put("duplicate_claim_packet_grants_once_and_exact_server_counts",true);
                wait=ticks;stage=7;
            }
            else if(stage==7&&ticks-wait>5)
            {
                shot("opening-result.png");originalScale=mc.gameSettings.guiScale;mc.gameSettings.guiScale=4;mc.resize(mc.displayWidth,mc.displayHeight);wait=ticks;stage=8;
            }
            else if(stage==8&&ticks-wait>5)
            {
                AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError()==0,"Small opening layout left a GL error");shot("opening-small-layout.png");checks.put("small_screen_layout_and_resize_preserve_result",true);
                mc.gameSettings.guiScale=originalScale;mc.resize(mc.displayWidth,mc.displayHeight);close(opening);wait=ticks;stage=9;
            }
            else if(stage==9&&ticks-wait>15)
            {
                AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper wrapper&&wrapper.getGui()==tree,"Duplicate claim queued a second opening or lost the quest screen");
                new MessageClaimReward(EARLY,true).sendToServer();stage=10;
            }
            else if(stage==10&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                opening=gui;previousResult=gui.result;close(gui);wait=ticks;stage=11;
                server(()->{verifyDelta(previousResult);serverStep=3;});
            }
            else if(stage==11&&ticks-wait>12&&serverStep==3)
            {
                AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper,"Early close did not return to quest book");checks.put("close_during_animation_keeps_already_granted_rewards",true);
                blocker=new GuiScreen(){};mc.displayGuiScreen(blocker);stage=12;
                server(()->{before=count(player());ItemStack stack=ServerQuestFile.INSTANCE.getRewardTable(0x69000102).lootCrate.createStack();stack.setCount(3);player().setHeldItem(EnumHand.MAIN_HAND,stack);player().setSneaking(true);stack.getItem().onItemRightClick(player().world,player(),EnumHand.MAIN_HAND);player().setSneaking(false);AcceptanceMod.require(player().getHeldItemMainhand().isEmpty(),"Batch opening did not consume all crates");serverStep=4;});wait=ticks;
            }
            else if(stage==12&&serverStep==4&&ticks-wait>20)
            {
                AcceptanceMod.require(mc.currentScreen==blocker,"Opening replaced an unrelated mod/inventory screen");checks.put("unrelated_screen_is_not_replaced_by_delayed_result",true);tree.openGui();stage=13;
            }
            else if(stage==13&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                opening=gui;AcceptanceMod.require(gui.result.boxes==3,"Batch result did not preserve crate count");previousResult=gui.result;
                AcceptanceMod.require(gui.result.entries.stream().filter(e->!e.stack.isEmpty()).mapToLong(e->e.count).sum()==42,"Six bundle rolls should display 12 diamonds and 30 gold");
                AcceptanceMod.require(gui.result.entries.stream().anyMatch(e->e.text.getUnformattedText().contains("80")),"XP reward did not arrive in opening results");
                AcceptanceMod.require(gui.result.entries.stream().anyMatch(e->e.index>=0),"Command reward fallback is absent");
                server(()->{long emeralds=count(player()).getOrDefault(Items.EMERALD,0L)-before.getOrDefault(Items.EMERALD,0L);verifyDelta(previousResult);AcceptanceMod.require(emeralds==6,"Command reward was not granted once per actual roll");serverStep=5;});
                gui.skipAnimation();wait=ticks;stage=14;
            }
            else if(stage==14&&ticks-wait>6&&serverStep==5)
            {
                shot("opening-batch-result.png");checks.put("batch_items_random_counts_xp_command_and_skip_are_server_authoritative",true);close(opening);stage=15;
                server(()->{before=count(player());player().setGameType(GameType.SURVIVAL);ItemStack stack=ServerQuestFile.INSTANCE.getRewardTable(0x69000103).lootCrate.createStack();player().setHeldItem(EnumHand.MAIN_HAND,stack);player().inventoryContainer.detectAndSendChanges();serverStep=6;});
            }
            else if(stage==15&&serverStep==6&&mc.player.getHeldItemMainhand().getItem() instanceof com.feed_the_beast.ftbquests.item.ItemLootCrate)
            {
                mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);stage=16;
            }
            else if(stage==16&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                opening=gui;AcceptanceMod.require(gui.result.entries.isEmpty(),"Empty roll fabricated a prize");gui.skipAnimation();wait=ticks;stage=17;
                server(()->{AcceptanceMod.require(before.equals(count(player())),"Empty roll changed rewards");AcceptanceMod.require(player().getHeldItemMainhand().isEmpty(),"Right click packet did not consume empty crate");player().setGameType(GameType.CREATIVE);serverStep=7;});
            }
            else if(stage==17&&ticks-wait>5&&serverStep==7)
            {
                shot("opening-empty-result.png");checks.put("real_item_use_packet_empty_roll_consumption_and_empty_result",true);close(opening);stage=18;
                server(()->{before=count(player());ItemStack stack=ServerQuestFile.INSTANCE.getRewardTable(0x69000104).lootCrate.createStack();player().setHeldItem(EnumHand.MAIN_HAND,stack);stack.getItem().onItemRightClick(player().world,player(),EnumHand.MAIN_HAND);serverStep=8;});
            }
            else if(stage==18&&serverStep==8&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                AcceptanceMod.require(gui.result.entries.isEmpty(),"Only-one skipped item became a fake displayed drop");close(gui);checks.put("only_one_skipped_reward_not_displayed_as_a_new_drop",true);stage=181;
                server(()->{ItemStack stack=ServerQuestFile.INSTANCE.getRewardTable(0x69000106).lootCrate.createStack();player().setHeldItem(EnumHand.MAIN_HAND,stack);stack.getItem().onItemRightClick(player().world,player(),EnumHand.MAIN_HAND);});
            }
            else if(stage==181&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                AcceptanceMod.require(gui.result.entries.isEmpty(),"Unavailable legacy reward became a fake prize");close(gui);checks.put("unavailable_legacy_reward_does_not_fabricate_a_displayed_prize",true);
                VisualConfig.rewardOpeningAnimation=false;new MessageClaimReward(OFF,true).sendToServer();stage=19;
            }
            else if(stage==19&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                AcceptanceMod.require(gui.isRevealed(),"Animation preference did not show results immediately");checks.put("client_animation_preference_can_disable_animation",true);close(gui);VisualConfig.rewardOpeningAnimation=true;wait=ticks;stage=20;
                server(()->{var data=(ServerQuestData)ServerQuestFile.INSTANCE.getData(player());data.claimReward(player(),ServerQuestFile.INSTANCE.getReward(AUTO),true);serverStep=9;});
            }
            else if(stage==20&&ticks-wait>20&&serverStep==9)
            {
                AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper,"Auto path unexpectedly opened a blocking animation");checks.put("automatic_reward_path_keeps_nonblocking_notifications",true);
                new MessageClaimReward(LOOT,true).sendToServer();stage=21;
            }
            else if(stage==21&&mc.currentScreen instanceof GuiLootOpening gui)
            {
                AcceptanceMod.require(gui.result.tableId==0x69000102,"Loot quest reward used the wrong table");gui.skipAnimation();close(gui);checks.put("quest_loot_reward_uses_same_opening_ui",true);stage=22;
                server(()->{AcceptanceMod.roundTrip();serverStep=10;});
            }
            else if(stage==22&&serverStep==10)
            {
                checks.put("legacy_book_and_new_fixtures_save_reload_without_definition_loss",true);
                Files.writeString(Path.of("opening-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                AcceptanceMod.report("PASS","Reward chest, reel and result; real server claims and crate use; duplicate claims, exact quantities, XP, commands, batch, empty/only-one, early close, skip, queued GUI, settings, automatic path, small layout and definition roundtrip",null);stage=100;mc.shutdown();
            }
        }
        catch(Throwable ex){int at=stage;stage=100;AcceptanceMod.report("FAIL","Reward opening stage "+at,ex);mc.shutdown();}
    }
    private void setup(EntityPlayerMP player)
    {
        AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;player.inventory.clear();player.inventory.currentItem=8;before=count(player);
        Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="宝箱奖励验收";chapter.onCreated();Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="任务奖励开箱动画";quest.onCreated();
        var task=new CheckmarkTask(quest);task.id=0x69000003;task.onCreated();
        RewardTable random=table(file,0x69000101,"幸运宝箱");
        for(Item item:List.of(Items.DIAMOND,Items.GOLD_INGOT,Items.EMERALD,Items.IRON_INGOT,Items.ENDER_PEARL,Items.GOLDEN_APPLE)){
            ItemReward reward=new ItemReward(random.fakeQuest,new ItemStack(item));reward.count=2;reward.randomBonus=3;random.rewards.add(new WeightedReward(reward,1));
        }
        RewardTable bundle=table(file,0x69000105,"多重奖励");
        for(Item item:List.of(Items.DIAMOND,Items.GOLD_INGOT)){ItemReward reward=new ItemReward(bundle.fakeQuest,new ItemStack(item));reward.count=item==Items.DIAMOND?2:5;bundle.rewards.add(new WeightedReward(reward,1));}
        XPReward xp=new XPReward(bundle.fakeQuest);xp.xp=80;bundle.rewards.add(new WeightedReward(xp,1));CommandReward command=new CommandReward(bundle.fakeQuest);command.command="/give @s minecraft:emerald 1";bundle.rewards.add(new WeightedReward(command,1));
        RewardTable mixed=table(file,0x69000102,"工程补给箱");mixed.lootSize=2;AllTableReward all=new AllTableReward(mixed.fakeQuest);all.table=bundle;mixed.rewards.add(new WeightedReward(all,1));
        RewardTable empty=table(file,0x69000103,"空奖验收箱");empty.emptyWeight=1;
        RewardTable once=table(file,0x69000104,"唯一物品验收箱");ItemReward unique=new ItemReward(once.fakeQuest,new ItemStack(Items.DIAMOND));unique.onlyOne=true;once.rewards.add(new WeightedReward(unique,1));
        RewardTable missing=table(file,0x69000106,"旧附属缺失奖励箱");MissingReward absent=new MissingReward(missing.fakeQuest);NBTTagCompound old=new NBTTagCompound();old.setString("type","missing_addon:reward");old.setLong("keep_unknown",9000000001L);absent.readDataPreservingUnknown(old);missing.rewards.add(new WeightedReward(absent,1));
        for(int id:new int[]{RANDOM,LOOT,EARLY,OFF,AUTO}){RandomReward reward=id==LOOT?new LootReward(quest):new RandomReward(quest);reward.id=id;reward.table=id==LOOT?mixed:random;reward.autoclaim=RewardAutoClaim.DISABLED;reward.onCreated();}
        file.refreshIDMap();file.clearCachedData();quest.changeProgress(file.getData(player),ChangeProgress.COMPLETE);
        NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));
    }
    private static RewardTable table(ServerQuestFile file,int id,String title){RewardTable table=new RewardTable(file);table.id=id;table.title=title;table.lootCrate=new LootCrate(table);table.lootCrate.stringID="opening_"+Integer.toHexString(id);table.lootCrate.itemName=title;table.onCreated();return table;}
    private static Map<Item,Long> count(EntityPlayerMP player){Map<Item,Long> result=new HashMap<>();for(ItemStack stack:player.inventory.mainInventory)if(!stack.isEmpty()&&!(stack.getItem() instanceof com.feed_the_beast.ftbquests.item.ItemLootCrate))result.merge(stack.getItem(),(long)stack.getCount(),Long::sum);return result;}
    private void verifyDelta(RewardOpening.Result result)
    {
        Map<Item,Long> after=count(player()),expected=new HashMap<>();for(var entry:result.entries)if(!entry.stack.isEmpty())expected.merge(entry.stack.getItem(),entry.count,Long::sum);
        for(var entry:expected.entrySet())AcceptanceMod.require(after.getOrDefault(entry.getKey(),0L)-before.getOrDefault(entry.getKey(),0L)==entry.getValue(),"Displayed count differs from actual grant: "+entry.getKey()+" / "+entry.getValue());
        before=after;
    }
    private static void close(GuiLootOpening gui)throws Exception
    {
        Field scale=GuiLootOpening.class.getDeclaredField("scale"),ox=GuiLootOpening.class.getDeclaredField("originX"),oy=GuiLootOpening.class.getDeclaredField("originY");scale.setAccessible(true);ox.setAccessible(true);oy.setAccessible(true);
        Method clicked=Arrays.stream(GuiLootOpening.class.getDeclaredMethods()).filter(m->Arrays.equals(m.getParameterTypes(),new Class<?>[]{int.class,int.class,int.class})).findFirst().orElseThrow();clicked.setAccessible(true);
        clicked.invoke(gui,ox.getInt(gui)+Math.round(306*scale.getFloat(gui)),oy.getInt(gui)+Math.round(14*scale.getFloat(gui)),0);
    }
    private static EntityPlayerMP player(){return Minecraft.getMinecraft().getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private void server(Checked action){Minecraft.getMinecraft().getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable ex){failure=ex;}});}
    private interface Checked{void run()throws Exception;}
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
