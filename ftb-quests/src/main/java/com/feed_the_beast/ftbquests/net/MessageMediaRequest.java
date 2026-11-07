package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.UUID;

public final class MessageMediaRequest extends MessageToServer
{
    public UUID id; public String name; public int offset;
    public MessageMediaRequest() {}
    public MessageMediaRequest(UUID id, String name, int offset) { this.id = id; this.name = name; this.offset = offset; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsNetHandler.GENERAL; }
    @Override public void writeData(DataOut out) { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); out.writeString(name); out.writeInt(offset); }
    @Override public void readData(DataIn in) { id = new UUID(in.readLong(), in.readLong()); name = in.readString(); offset = in.readInt(); if (!QuestMedia.validName(name) || offset < -1 || offset > QuestMedia.limit(name) || offset >= 0 && offset % (QuestMedia.CHUNK * QuestMedia.WINDOW) != 0) throw new IllegalArgumentException("Invalid media request"); }
    @Override public void onMessage(EntityPlayerMP player) { ServerMediaTransfers.download(player, this); }
}
