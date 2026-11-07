package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.client.ClientMediaAssets;
import java.util.UUID;

public final class MessageMediaUploadAck extends MessageToClient
{
    private UUID id; private int next; private String error;
    public MessageMediaUploadAck() {}
    public MessageMediaUploadAck(UUID id, int next, String error) { this.id = id; this.next = next; this.error = error; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsNetHandler.GENERAL; }
    @Override public void writeData(DataOut out) { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); out.writeInt(next); out.writeString(error); }
    @Override public void readData(DataIn in) { id = new UUID(in.readLong(), in.readLong()); next = in.readInt(); error = in.readString(); if (error.length() > 32) throw new IllegalArgumentException("Invalid media reply"); }
    @Override public void onMessage() { ClientMediaAssets.acknowledge(id, next, error); }
}
