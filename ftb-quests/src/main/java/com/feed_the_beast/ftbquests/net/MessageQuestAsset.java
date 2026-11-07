package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import com.feed_the_beast.ftbquests.util.AssetTransfers;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

public final class MessageQuestAsset extends MessageToClient
{
    private static final AssetTransfers TRANSFERS = new AssetTransfers();
    private static final UUID SERVER = new UUID(0, 0);
    private String name;
    private UUID transfer;
    private int total, offset;
    private byte[] part;
    public MessageQuestAsset() {}
    private static void send(String name, byte[] bytes, EntityPlayerMP player)
    {
        UUID id = UUID.randomUUID();
        for (int offset = 0; offset < bytes.length; offset += AssetTransfers.CHUNK_SIZE)
        {
            MessageQuestAsset message = new MessageQuestAsset();
            message.name = name; message.transfer = id; message.total = bytes.length; message.offset = offset;
            message.part = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + AssetTransfers.CHUNK_SIZE));
            AssetTransfers.validate(message.total, offset, message.part.length);
            if (player == null) message.sendToAll(); else message.sendTo(player);
        }
    }
    public static void broadcast(String name, byte[] bytes) { send(name, bytes, null); }
    public static void clearTransfers() { TRANSFERS.clear(); }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsNetHandler.GENERAL; }
    @Override public void writeData(DataOut out)
    {
        out.writeString(name); out.writeLong(transfer.getMostSignificantBits()); out.writeLong(transfer.getLeastSignificantBits());
        out.writeVarInt(total); out.writeVarInt(offset); out.writeVarInt(part.length); out.writeBytes(part);
    }
    @Override public void readData(DataIn in)
    {
        name = in.readString(); transfer = new UUID(in.readLong(), in.readLong());
        total = in.readVarInt(); offset = in.readVarInt(); int size = in.readVarInt();
        if (!QuestAssets.validName(name)) throw new IllegalArgumentException("Invalid quest asset name");
        AssetTransfers.validate(total, offset, size); part = new byte[size]; in.readBytes(part);
    }
    @Override public void onMessage()
    {
        try
        {
            byte[] bytes = TRANSFERS.accept(new AssetTransfers.Key(SERVER, transfer), name, total, offset, part);
            if (bytes != null) QuestAssets.store(Loader.instance().getConfigDir().toPath().resolve("ftbquests/.asset-cache"), name, bytes);
        }
        catch (java.io.IOException | IllegalArgumentException ex) { com.feed_the_beast.ftbquests.FTBQuests.LOGGER.warn("Could not cache quest image " + name, ex); }
    }

    public static final class Request extends MessageToServer
    {
        private String name;
        public Request() {}
        public Request(String name) { this.name = name; }
        @Override public NetworkWrapper getWrapper() { return FTBQuestsNetHandler.GENERAL; }
        @Override public void writeData(DataOut out) { out.writeString(name); }
        @Override public void readData(DataIn in) { name = in.readString(); }
        @Override public void onMessage(EntityPlayerMP player)
        {
            if (!QuestAssets.validName(name) || ServerQuestFile.INSTANCE == null) return;
            try
            {
                java.nio.file.Path file = ServerQuestFile.INSTANCE.getFolder().toPath().resolve("assets").resolve(name);
                if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) <= QuestAssets.MAX_BYTES)
                    send(name, java.nio.file.Files.readAllBytes(file), player);
            }
            catch (java.io.IOException ex) { com.feed_the_beast.ftbquests.FTBQuests.LOGGER.warn("Could not read quest image " + name, ex); }
        }
    }
}
