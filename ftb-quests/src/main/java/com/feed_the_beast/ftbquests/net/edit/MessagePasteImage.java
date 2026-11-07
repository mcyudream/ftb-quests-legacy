package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.net.MessageQuestAsset;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import com.feed_the_beast.ftbquests.util.AssetTransfers;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;

public final class MessagePasteImage extends MessageToServer
{
    private static final AssetTransfers TRANSFERS = new AssetTransfers();
    private int chapter, total, offset;
    private UUID transfer;
    private double x, y;
    private byte[] part;
    public MessagePasteImage() {}
    public static void send(int chapter, double x, double y, byte[] png)
    {
        UUID id = UUID.randomUUID();
        for (int offset = 0; offset < png.length; offset += AssetTransfers.CHUNK_SIZE)
        {
            MessagePasteImage message = new MessagePasteImage();
            message.chapter = chapter; message.x = x; message.y = y; message.transfer = id;
            message.total = png.length; message.offset = offset;
            message.part = Arrays.copyOfRange(png, offset, Math.min(png.length, offset + AssetTransfers.CHUNK_SIZE));
            AssetTransfers.validate(message.total, offset, message.part.length);
            message.sendToServer();
        }
    }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsEditNetHandler.EDIT; }
    @Override public void writeData(DataOut out)
    {
        out.writeInt(chapter); out.writeDouble(x); out.writeDouble(y);
        out.writeLong(transfer.getMostSignificantBits()); out.writeLong(transfer.getLeastSignificantBits());
        out.writeVarInt(total); out.writeVarInt(offset); out.writeVarInt(part.length); out.writeBytes(part);
    }
    @Override public void readData(DataIn in)
    {
        chapter = in.readInt(); x = in.readDouble(); y = in.readDouble();
        transfer = new UUID(in.readLong(), in.readLong()); total = in.readVarInt(); offset = in.readVarInt();
        int size = in.readVarInt(); AssetTransfers.validate(total, offset, size);
        part = new byte[size]; in.readBytes(part);
    }
    @Override public void onMessage(EntityPlayerMP player)
    {
        if (ServerQuestFile.INSTANCE == null || !FTBQuests.canEdit(player) || !Double.isFinite(x) || !Double.isFinite(y)) return;
        Chapter target = ServerQuestFile.INSTANCE.getChapter(chapter);
        if (chapter != 0 && target == null) return;
        try
        {
            byte[] png = TRANSFERS.accept(new AssetTransfers.Key(player.getUniqueID(), transfer),
                    chapter + ":" + x + ":" + y, total, offset, part);
            if (png == null) return;
            int width, height;
            try (var stream = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(png)))
            {
                var readers = javax.imageio.ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) return;
                var reader = readers.next();
                try { reader.setInput(stream); width = reader.getWidth(0); height = reader.getHeight(0); }
                finally { reader.dispose(); }
            }
            if (width < 1 || height < 1 || (long)width * height > 16_777_216L) return;
            String name = QuestAssets.name(png, "png");
            QuestAssets.store(ServerQuestFile.INSTANCE.getFolder().toPath().resolve("assets"), name, png);
            MessageQuestAsset.broadcast(name, png);
            if (target == null) return;
            net.minecraft.nbt.NBTTagCompound before = com.feed_the_beast.ftbquests.util.EditHistory.snapshot(target);
            ChapterImage image = new ChapterImage(target);
            image.x = x; image.y = y; image.width = 4; image.height = 4D * height / width; image.mediaSource = "asset:" + name;
            target.images.add(image);
            com.feed_the_beast.ftbquests.util.EditHistory.record(ServerQuestFile.INSTANCE, target, before);
            ServerQuestFile.INSTANCE.save();
            new MessageEditObjectResponse(target).sendToAll();
        }
        catch (java.io.IOException | IllegalArgumentException ex) { FTBQuests.LOGGER.warn("Could not paste quest image", ex); }
    }
}
