package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.net.edit.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Server-thread authoring API, also exposed to optional CraftTweaker / ZenScript. */
public final class QuestEdits
{
    private static void requireServerThread(QuestFile file)
    {
        var server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (!(file instanceof ServerQuestFile) || server == null || !server.isCallingFromMinecraftThread())
            throw new IllegalStateException("Quest authoring must run on the server thread");
    }
    public static void broadcastCreated(Collection<QuestObjectBase> created, Chapter chapter)
    {
        for(QuestObjectBase object:created)if(object instanceof Quest)new MessageCreateObjectResponse(object,null).sendToAll();
        for(QuestObjectBase object:created)if(!(object instanceof Quest))
        {
            NBTTagCompound type=new NBTTagCompound();type.setString("type",object instanceof Task?((Task)object).getTypeForNBT():((Reward)object).getTypeForNBT());
            new MessageCreateObjectResponse(object,type).sendToAll();
        }
        for(QuestObjectBase object:created)if(object instanceof Quest)new MessageEditObjectResponse(object).sendToAll();
        new MessageEditObjectResponse(chapter).sendToAll();
    }
    public static Quest create(Chapter chapter,double x,double y,NBTTagCompound definition)
    {
        requireServerThread(chapter.file);
        NBTTagCompound input=definition.copy();input.setInteger("source_uid",1);int next=2;
        for(String key:new String[]{"tasks","rewards"})for(NBTBase value:input.getTagList(key,10))((NBTTagCompound)value).setInteger("source_uid",next++);
        input.setDouble("x",0);input.setDouble("y",0);
        NBTTagCompound clipboard=new NBTTagCompound();clipboard.setInteger("ftbquests_clipboard",1);
        NBTTagList list=new NBTTagList();list.appendTag(input);clipboard.setTag("quests",list);
        var created=QuestCopies.paste(chapter,clipboard,x,y,false);
        ServerQuestFile file=(ServerQuestFile)chapter.file;EditHistory.clear(file);file.clearCachedData();file.save();
        broadcastCreated(created,chapter);return (Quest)created.get(0);
    }
    public static void edit(QuestObjectBase object,NBTTagCompound patch)
    {
        requireServerThread(object.getQuestFile());
        if(patch.hasKey("tasks") || patch.hasKey("rewards") || patch.hasKey("uid") || patch.hasKey("type"))throw new IllegalArgumentException("editObject accepts properties; use createQuest for new tasks/rewards");
        ServerQuestFile file=(ServerQuestFile)object.getQuestFile();NBTTagCompound before=EditHistory.snapshot(object),next=before.copy();next.merge(patch);
        try
        {
            object.readDataPreservingUnknown(next);file.validateChapterGroups();
            for(Chapter chapter:file.chapters)for(Quest quest:chapter.quests)if(!quest.verifyDependencies(false))throw new IllegalArgumentException("Quest dependency cycle");
        }
        catch(RuntimeException ex){object.readDataPreservingUnknown(before);throw ex;}
        EditHistory.record(file,object,before);file.clearCachedData();file.save();new MessageEditObjectResponse(object).sendToAll();
    }
}
