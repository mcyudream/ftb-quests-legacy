package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.UUID;

public final class MessageMediaUpload extends MessageToServer
{
    public UUID id; public String name; public int total, offset; public byte[] part;
    public MessageMediaUpload() {}
    public MessageMediaUpload(UUID id, String name, int total, int offset, byte[] part)
    { this.id = id; this.name = name; this.total = total; this.offset = offset; this.part = part; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsEditNetHandler.EDIT; }
    @Override public void writeData(DataOut out)
    { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); out.writeString(name); out.writeVarInt(total); out.writeInt(offset); out.writeVarInt(part.length); out.writeBytes(part); }
    @Override public void readData(DataIn in)
    {
        id = new UUID(in.readLong(), in.readLong()); name = in.readString(); total = in.readVarInt(); offset = in.readInt(); int size = in.readVarInt();
        if (offset == -2) { if (size != 0 || !name.isEmpty() || total != 0) throw new IllegalArgumentException("Invalid media cancellation"); }
        else if (offset == -1) { if (!QuestMedia.validName(name) || total < 1 || total > QuestMedia.limit(name) || size != 0) throw new IllegalArgumentException("Invalid media upload"); }
        else QuestMedia.validateChunk(name, total, offset, size);
        part = new byte[size]; in.readBytes(part);
    }
    @Override public void onMessage(EntityPlayerMP player) { ServerMediaTransfers.upload(player, this); }
}
