package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.EditHistory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

public final class MessageHistory extends MessageToServer
{
    private boolean redo;
    public MessageHistory() {}
    public MessageHistory(boolean redo) { this.redo = redo; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsEditNetHandler.EDIT; }
    @Override public void writeData(DataOut data) { data.writeBoolean(redo); }
    @Override public void readData(DataIn data) { redo = data.readBoolean(); }
    @Override public void onMessage(EntityPlayerMP player)
    {
        if (!FTBQuests.canEdit(player)) return;
        try { for (QuestObjectBase object : EditHistory.restore(ServerQuestFile.INSTANCE, redo)) new MessageEditObjectResponse(object).sendToAll(); }
        catch (IllegalStateException ex) { player.sendMessage(new TextComponentString(ex.getMessage())); }
    }
}
