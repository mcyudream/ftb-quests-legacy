package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.EditHistory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.FMLCommonHandler;
import java.nio.file.*;
import java.util.*;

@Mod(modid="ftbquests_acceptance",name="FTB Quests Acceptance Harness",version="1",dependencies="required-after:ftbquests",acceptableRemoteVersions="*")
public final class AcceptanceMod
{
    @Mod.EventHandler public void loaded(FMLLoadCompleteEvent event)
    {
        if (event.getSide().isClient())
        {
            if (Boolean.getBoolean("ftbquests.acceptance.input")) AcceptanceInput.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.animation")) AcceptanceAnimation.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.creation")) AcceptanceCreation.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.douyin")) AcceptanceDouyin.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.localization")) AcceptanceLocalization.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.ponderlinks")) AcceptancePonderLinks.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.opening")) AcceptanceOpening.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.bilibili")) AcceptanceBilibili.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.mediaeditor")) AcceptanceMediaEditor.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.texteditor")) AcceptanceTextEditor.install();
            else if (Boolean.getBoolean("ftbquests.acceptance.edits")) AcceptanceEditing.install();
            else AcceptanceClient.install();
        }
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event)
    {
        if(!Boolean.getBoolean("ftbquests.acceptance.server")) return;
        var server=FMLCommonHandler.instance().getMinecraftServerInstance();
        server.addScheduledTask(() -> {
            try {
                if(Boolean.getBoolean("ftbquests.acceptance.ponderlinks")) {
                    roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=0x69000001;chapter.title="Ponder links server";chapter.onCreated();
                    Quest quest=new Quest(chapter);quest.id=0x69000002;quest.title="Optional Ponder";quest.onCreated();
                    quest.description.add("{\"type\":\"ponder\",\"value\":\"minecraft:compass#ftbquests:links_second\",\"addon\":{\"kept\":[1,2]}}");
                    quest.description.add("{\"type\":\"markdown\",\"text\":\"[Ponder](ponder:minecraft:compass)\"}");
                    file.refreshIDMap();file.clearCachedData();
                }
                roundTrip(); report("PASS", "server definition round trip",null);
            }
            catch(Throwable error) { report("FAIL","server definition round trip",error); }
            server.initiateShutdown();
        });
    }
    public static void require(boolean condition,String description)
    { if(!condition)throw new AssertionError(description); }
    public static Map<Integer,net.minecraft.nbt.NBTTagCompound> snapshot(QuestFile file)
    { Map<Integer,net.minecraft.nbt.NBTTagCompound> result=new TreeMap<>();for(QuestObjectBase object:file.getAllObjects())result.put(object.id,EditHistory.snapshot(object));return result; }
    public static void roundTrip()
    {
        ServerQuestFile file=ServerQuestFile.INSTANCE;
        try { AcceptanceStorage.check(file); } catch (Exception ex) { throw new RuntimeException(ex); }
        var before=snapshot(file);
        require(!before.isEmpty(),"Existing quest book was not loaded");file.saveNow();file.readDataFull(file.getFolder());
        require(before.equals(snapshot(file)),"Quest definitions differ after save/reload");
    }
    public static synchronized void report(String result,String detail,Throwable error)
    {
        try
        {
            com.google.gson.JsonObject json=new com.google.gson.JsonObject();json.addProperty("result",result);json.addProperty("cleanroom",net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("cleanroom").getVersion());json.addProperty("detail",detail);
            for (String feature : new String[]{"video", "ponder", "zs"}) json.addProperty(feature, Boolean.getBoolean("ftbquests.acceptance." + feature));
            if(error!=null) {java.io.StringWriter text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));json.addProperty("error",text.toString());error.printStackTrace();}
            Path path=Paths.get("acceptance.json");Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
        }
        catch(Exception ex){throw new RuntimeException(ex);}
    }
}
