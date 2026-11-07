package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.client.ClientMediaAssets;
import com.feed_the_beast.ftbquests.util.QuestMedia;
import java.util.UUID;

public final class MessageMediaData extends MessageToClient
{
    public UUID id; public String name, error = ""; public int total, offset; public byte[] part = new byte[0];
    public MessageMediaData() {}
    public MessageMediaData(UUID id, String name, int total, int offset, byte[] part)
    { this.id = id; this.name = name; this.total = total; this.offset = offset; this.part = part; }
    public MessageMediaData(UUID id, String name, String error) { this.id = id; this.name = name; this.error = error; offset = -1; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsNetHandler.GENERAL; }
    @Override public void writeData(DataOut out)
    { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); out.writeString(name); out.writeVarInt(total); out.writeInt(offset); out.writeString(error); out.writeVarInt(part.length); out.writeBytes(part); }
    @Override public void readData(DataIn in)
    {
        id = new UUID(in.readLong(), in.readLong()); name = in.readString(); total = in.readVarInt(); offset = in.readInt(); error = in.readString(); int size = in.readVarInt();
        if (offset == -1) { if (!QuestMedia.validName(name) || size != 0 || total != 0 || error.length() > 32) throw new IllegalArgumentException("Invalid media error"); }
        else { QuestMedia.validateChunk(name, total, offset, size); if (!error.isEmpty()) throw new IllegalArgumentException("Invalid media data"); }
        part = new byte[size]; in.readBytes(part);
    }
    @Override public void onMessage() { ClientMediaAssets.receive(this); }
}
