package dev.ftblib.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.*;
import com.feed_the_beast.ftblib.lib.util.AtomicNBT;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.GsonBuilder;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import java.nio.file.*;
import java.util.*;

@Mod(modid="ftblib_team_uid_acceptance", name="FTB Library Team UID Acceptance", version="1", dependencies="required-after:ftbquests", acceptableRemoteVersions="*")
public final class TeamUIDAcceptance
{
    static final int CHAPTER=0x6A000001, QUEST=0x6A000002, TASK=0x6A000003, CHECKMARK=0x6A000004, REWARD=0x6A000005;
    static final UUID PLAYER=UUID.fromString("e32af18d-0810-383c-bd28-0cb70e61a70a");
    static final Map<String,Boolean> checks=new LinkedHashMap<>();
    @Mod.EventHandler public void loaded(FMLLoadCompleteEvent event)
    { if(event.getSide().isClient()) TeamUIDClient.install(); }
    @Mod.EventHandler public void started(FMLServerStartedEvent event)
    {
        if(!Boolean.getBoolean("ftblib.acceptance.server"))return;
        var server=FMLCommonHandler.instance().getMinecraftServerInstance();
        server.addScheduledTask(()->{
            try { verify();saveAndVerify();report("PASS",null); }
            catch(Throwable error){report("FAIL",error);}
            server.initiateShutdown();
        });
    }
    static void require(boolean value,String description)
    {if(!value)throw new AssertionError(description);checks.put(description,true);}
    static void seed()throws Exception
    {
        var universe=Universe.get();var file=ServerQuestFile.INSTANCE;var team=universe.getTeam("singleplayer");
        require(team.isValid()&&team.getUID()==2,"new_singleplayer_world_uses_uid_2");
        Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="单人存档回归";chapter.onCreated();
        Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="保存后重新进入";quest.description.add("任务正文及旧进度应保留");quest.onCreated();
        KillTask task=new KillTask(quest);task.id=TASK;task.value=4_000_000_000L;task.onCreated();
        CheckmarkTask mark=new CheckmarkTask(quest);mark.id=CHECKMARK;mark.onCreated();
        ItemReward reward=new ItemReward(quest);reward.id=REWARD;reward.item=new ItemStack(Items.DIAMOND);reward.onCreated();
        var definition=EditHistory.snapshot(quest);definition.setLong("pack_definition",9_000_000_001L);quest.readDataPreservingUnknown(definition);
        file.refreshIDMap();file.clearCachedData();
        var data=ServerQuestData.get(team);data.createTaskData(task);data.createTaskData(mark);
        data.readData(JsonToNBT.getTagFromJson("{Tasks:{6a000003:3500000000L,6a000004:1b,7fffffff:7000000000L},ClaimedTeamRewards:[I;1778384901],ClaimedPlayerRewards:{\"e32af18d-0810-383c-bd28-0cb70e61a70a\":[I;1778384901]},pack_progress:{timer:1234567890123L}}"));
        var tag=team.serializeNBT();tag.setString("pack_team","保留团队扩展");tag.getCompoundTag("Players").setString("OfflinePackMember","member");team.deserializeNBT(tag);team.markDirty();
        var player=universe.getPlayer(PLAYER);tag=player.serializeNBT();tag.setLong("pack_player",7_000_000_000L);player.deserializeNBT(tag);player.markDirty();
        universe.markDirty();
    }
    static void verify()
    {
        var universe=Universe.get();var file=ServerQuestFile.INSTANCE;var team=universe.getTeam("singleplayer");
        require(team.isValid()&&team.getUID()==2&&universe.getTeam(2)==team,"saved_singleplayer_uid_2_and_both_lookups_preserved");
        require(team.type==TeamType.SERVER,"singleplayer_server_team_type_preserved");
        var player=universe.getPlayer(PLAYER);
        require(player!=null&&player.team==team,"player_membership_preserved");
        require(player.serializeNBT().getLong("pack_player")==7_000_000_000L,"unknown_player_data_preserved");
        var tag=team.serializeNBT();
        require("保留团队扩展".equals(tag.getString("pack_team")),"unknown_team_data_preserved");
        require("member".equals(tag.getCompoundTag("Players").getString("OfflinePackMember")),"unresolved_team_member_preserved");
        var quest=file.getQuest(QUEST);
        require(quest!=null&&quest.description.equals(List.of("任务正文及旧进度应保留"))&&EditHistory.snapshot(quest).getLong("pack_definition")==9_000_000_001L,"quest_id_description_and_unknown_definition_preserved");
        var data=ServerQuestData.get(team);tag=new NBTTagCompound();data.writeData(tag);
        require(data.getTaskData(file.getTask(TASK)).progress==3_500_000_000L,"long_task_progress_preserved");
        require(file.getTask(CHECKMARK).isComplete(data),"completed_task_preserved");
        require(tag.getCompoundTag("Tasks").getLong("7fffffff")==7_000_000_000L,"orphan_task_progress_preserved");
        require(data.claimedTeamRewards.contains(REWARD)&&data.claimedPlayerRewards.get(PLAYER).contains(REWARD),"team_and_player_claimed_rewards_preserved");
        require(tag.getCompoundTag("pack_progress").getLong("timer")==1_234_567_890_123L,"unknown_quest_progress_preserved");
    }
    static void saveAndVerify()
    {
        var universe=Universe.get();var team=universe.getTeam(2);team.markDirty();universe.getPlayer(PLAYER).markDirty();universe.markDirty();
        var file=ServerQuestFile.INSTANCE;
        Map<Integer,NBTTagCompound> before=new TreeMap<>();for(var object:file.getAllObjects())before.put(object.id,EditHistory.snapshot(object));
        file.saveNow();file.readDataFull(file.getFolder());
        Map<Integer,NBTTagCompound> after=new TreeMap<>();for(var object:file.getAllObjects())after.put(object.id,EditHistory.snapshot(object));
        require(before.equals(after),"quest_book_save_and_reload_preserved_every_object");
        universe.server.saveAllWorlds(false);
        require(AtomicNBT.read(team.getDataFile("")).getInteger("UID")==2,"disk_team_uid_still_2_after_save");
        var progress=AtomicNBT.read(team.getDataFile("ftbquests"));
        require(progress.getCompoundTag("Tasks").getLong("6a000003")==3_500_000_000L&&progress.getIntArray("ClaimedTeamRewards")[0]==REWARD,"disk_progress_and_claim_records_saved");
        verify();
    }
    static synchronized void report(String result,Throwable error)
    {
        try{
            Map<String,Object> report=new LinkedHashMap<>();report.put("result",result);report.put("cleanroom",Loader.instance().getIndexedModList().get("cleanroom").getVersion());
            report.put("phase",System.getProperty("ftblib.acceptance.phase","reload"));report.put("checks",checks);
            report.put("ftblib",Loader.instance().getIndexedModList().get("ftblib").getVersion());
            if(error!=null){var text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));report.put("error",text.toString());error.printStackTrace();}
            Files.writeString(Path.of("team-uid-acceptance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception failure){throw new RuntimeException(failure);}
    }
}
