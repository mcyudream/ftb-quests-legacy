package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.StructureTask;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

public final class MessageStructurePosition extends MessageToServer
{
    private int task; private long position;
    public MessageStructurePosition() {}
    public MessageStructurePosition(int id, BlockPos position) {task=id;this.position=position.toLong();}
    @Override public NetworkWrapper getWrapper(){return FTBQuestsNetHandler.GENERAL;}
    @Override public void writeData(DataOut out){out.writeInt(task);out.writeLong(position);}
    @Override public void readData(DataIn in){task=in.readInt();position=in.readLong();}
    @Override public void onMessage(EntityPlayerMP player)
    {
        var object=ServerQuestFile.INSTANCE.getTask(task);BlockPos origin=BlockPos.fromLong(position);
        var data=ServerQuestData.get(Universe.get().getPlayer(player).team);
        if(!(object instanceof StructureTask) || !object.canStart(data) || player.getDistanceSq(origin)>128*128 || origin.getY()<0 || origin.getY()>255) return;
        var state=data.getTaskState(task);state.setLong("origin",position);state.setInteger("dimension",player.dimension);data.markDirty();
    }
}
