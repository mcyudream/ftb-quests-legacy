package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.events.ScriptTextClickEvent;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.ScriptTextActions;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import java.util.*;

public final class MessageScriptTextClick extends MessageToServer
{
    private static final Map<EntityPlayerMP, Long> LAST = new WeakHashMap<>();
    private int source;
    private String action;
    public MessageScriptTextClick() {}
    public MessageScriptTextClick(int source, String action) { this.source = source; this.action = action; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsNetHandler.GENERAL; }
    @Override public void writeData(DataOut out) { out.writeInt(source); out.writeString(action); }
    @Override public void readData(DataIn in) { source = in.readInt(); action = in.readString(); }
    @Override public void onMessage(EntityPlayerMP player)
    {
        var file = ServerQuestFile.INSTANCE;
        if (file == null || !ScriptTextActions.valid(action)) return;
        long now = System.nanoTime();
        if (now - LAST.getOrDefault(player, Long.MIN_VALUE / 2) < 250_000_000L) return;
        var object = file.get(source); var data = file.getData(player);
        if (object == null || object.invalid || !object.isVisible(data)) return;
        if (object instanceof Quest quest && (quest.hideTextUntilComplete && !quest.isComplete(data)
            || quest.hideDetailsUntilStartable() && !quest.areDependenciesComplete(data))) return;
        if (ScriptTextActions.declared(object, action))
        {
            LAST.put(player, now);
            MinecraftForge.EVENT_BUS.post(new ScriptTextClickEvent(player, object, action));
        }
    }
}
