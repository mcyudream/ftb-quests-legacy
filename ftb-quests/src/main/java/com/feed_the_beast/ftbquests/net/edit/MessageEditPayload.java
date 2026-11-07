package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.util.AssetTransfers;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Keeps large descriptions and selections below the 1.12 client payload limit. */
public final class MessageEditPayload extends MessageToServer
{
    private static final AssetTransfers TRANSFERS = new AssetTransfers();
    private int kind, id, total, offset;
    private UUID transfer;
    private double x, y;
    private byte[] part;
    private UUID session = com.feed_the_beast.ftbquests.client.ClientEditSync.UNSOLICITED;
    private long revision;
    public MessageEditPayload() {}

    public static void send(int kind, int id, double x, double y, NBTTagCompound nbt)
    { send(kind, id, x, y, nbt, com.feed_the_beast.ftbquests.client.ClientEditSync.UNSOLICITED, 0); }

    public static void send(int kind, int id, double x, double y, NBTTagCompound nbt, UUID session, long revision)
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(nbt, stream);
            byte[] bytes = stream.toByteArray();
            if (bytes.length > QuestAssets.MAX_BYTES) throw new IllegalArgumentException("Quest edit exceeds 8 MiB");
            UUID transfer = UUID.randomUUID();
            for (int offset = 0; offset < bytes.length; offset += AssetTransfers.CHUNK_SIZE)
            {
                MessageEditPayload message = new MessageEditPayload();
                message.kind = kind; message.id = id; message.x = x; message.y = y;
                message.session = session; message.revision = revision;
                message.transfer = transfer; message.total = bytes.length; message.offset = offset;
                message.part = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + AssetTransfers.CHUNK_SIZE));
                message.sendToServer();
            }
        }
        catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsEditNetHandler.EDIT; }
    @Override public void writeData(DataOut out)
    {
        out.writeByte(kind); out.writeInt(id); out.writeDouble(x); out.writeDouble(y);
        out.writeLong(transfer.getMostSignificantBits()); out.writeLong(transfer.getLeastSignificantBits());
        out.writeUUID(session); out.writeLong(revision);
        out.writeVarInt(total); out.writeVarInt(offset); out.writeVarInt(part.length); out.writeBytes(part);
    }
    @Override public void readData(DataIn in)
    {
        kind = in.readByte(); id = in.readInt(); x = in.readDouble(); y = in.readDouble();
        transfer = new UUID(in.readLong(), in.readLong());
        session = in.readUUID(); revision = in.readLong(); total = in.readVarInt(); offset = in.readVarInt();
        int length = in.readVarInt(); AssetTransfers.validate(total, offset, length);
        if (kind < 0 || kind > 1 || !Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Invalid quest edit");
        part = new byte[length]; in.readBytes(part);
    }
    @Override public void onMessage(EntityPlayerMP player)
    {
        if (!FTBQuests.canEdit(player))
        {
            var file = com.feed_the_beast.ftbquests.quest.ServerQuestFile.INSTANCE;
            if (kind == 1 && offset == 0 && file != null && file.getBase(id) != null)
                new MessageEditObjectResponse(file.getBase(id), session, revision).sendTo(player);
            return;
        }
        try
        {
            byte[] bytes = TRANSFERS.accept(new AssetTransfers.Key(player.getUniqueID(), transfer),
                    kind + ":" + id + ":" + x + ":" + y + ":" + session + ":" + revision, total, offset, part);
            if (bytes == null) return;
            NBTTagCompound nbt;
            try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))))
            { nbt = CompressedStreamTools.read(input, new NBTSizeTracker(16 * 1024 * 1024)); }
            if (kind == 0) new MessagePasteObjects(id, x, y, nbt).onMessage(player);
            else MessageEditObject.apply(player, id, nbt, session, revision);
        }
        catch (IOException | RuntimeException ex) { FTBQuests.LOGGER.warn("Rejected quest edit transfer", ex); }
    }
}
