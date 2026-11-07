package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public final class MessagePasteObjects extends MessageToServer
{
    private int chapter; private double x, y; private NBTTagCompound clipboard;
    public MessagePasteObjects() {}
    public static void send(int chapter, double x, double y, NBTTagCompound clipboard)
    { MessageEditPayload.send(0, chapter, x, y, clipboard); }
    public MessagePasteObjects(int chapter, double x, double y, NBTTagCompound clipboard) { this.chapter = chapter; this.x = x; this.y = y; this.clipboard = clipboard; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsEditNetHandler.EDIT; }
    @Override public void writeData(DataOut out) { out.writeInt(chapter); out.writeDouble(x); out.writeDouble(y); out.writeNBT(clipboard); }
    @Override public void readData(DataIn in) { chapter = in.readInt(); x = in.readDouble(); y = in.readDouble(); clipboard = in.readNBT(); }
    @Override public void onMessage(EntityPlayerMP player)
    {
        if (!FTBQuests.canEdit(player) || clipboard == null) return;
        ServerQuestFile file = ServerQuestFile.INSTANCE; Chapter target = file.getChapter(chapter); if (target == null) return;
        try
        {
            var created = QuestCopies.paste(target, clipboard, x, y); EditHistory.clear(file); file.clearCachedData(); file.save();
            // Parents must exist before the client reads children or references between copies.
            for (QuestObjectBase object : created) if (object instanceof Quest) new MessageCreateObjectResponse(object, null).sendToAll();
            for (QuestObjectBase object : created) if (!(object instanceof Quest))
            {
                NBTTagCompound extra = new NBTTagCompound(); extra.setString("type", object instanceof Task ? ((Task)object).getTypeForNBT() : ((Reward)object).getTypeForNBT());
                new MessageCreateObjectResponse(object, extra).sendToAll();
            }
            for (QuestObjectBase object : created) if (object instanceof Quest) new MessageEditObjectResponse(object).sendToAll();
            new MessageEditObjectResponse(target).sendToAll();
        }
        catch (IllegalArgumentException ex) { FTBQuests.LOGGER.warn("Rejected invalid quest clipboard", ex); }
    }
}
