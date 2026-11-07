package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.net.*;
import com.feed_the_beast.ftbquests.net.edit.MessageMediaUpload;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** All disk work runs on one bounded worker; network sends stay on the server thread. */
@Mod.EventBusSubscriber(modid = FTBQuests.MOD_ID)
public final class ServerMediaTransfers
{
    private static final ExecutorService IO = MediaIO.worker("Quest server media");
    private static final DiskMediaTransfers UPLOADS = new DiskMediaTransfers();
    private static final Map<DiskMediaTransfers.Key, Download> DOWNLOADS = new HashMap<>();
    private record Download(String name, Path file, int total, int next, long touched) {}
    private static int ticks;
    private static Path root(ServerQuestFile book) { return book.getFolder().toPath().getParent().resolve("media"); }
    private static void reply(EntityPlayerMP player, ServerQuestFile book, Runnable send)
    { var server = player.getServer(); if (server != null) server.addScheduledTask(() -> { if (player.connection != null && player.connection.netManager.isChannelOpen() && ServerQuestFile.INSTANCE == book) send.run(); }); }
    public static void upload(EntityPlayerMP player, MessageMediaUpload message)
    {
        var book = ServerQuestFile.INSTANCE; var key = new DiskMediaTransfers.Key(player.getUniqueID(), message.id);
        if (book == null) return;
        if(message.offset!=-2&&QuestMedia.video(message.name))
        {new MessageMediaUploadAck(message.id,-1,"local_video").sendTo(player);return;}
        if (message.offset != -2 && !FTBQuests.canEdit(player))
        { new MessageMediaUploadAck(message.id, -1, "permission").sendTo(player); submit(() -> { try { UPLOADS.cancel(key); } catch (IOException ignored) {} }); return; }
        if (!submit(() -> {
            try
            {
                if (message.offset == -2) { UPLOADS.cancel(key); return; }
                int next = message.offset == -1 ? UPLOADS.begin(key, root(book), message.name, message.total)
                        : UPLOADS.accept(key, message.name, message.total, message.offset, message.part);
                if (message.offset == -1 || next == message.total || next % (QuestMedia.CHUNK * QuestMedia.WINDOW) == 0)
                    reply(player, book, () -> new MessageMediaUploadAck(message.id, next, "").sendTo(player));
            }
            catch (IOException | IllegalArgumentException ex) { try { UPLOADS.cancel(key); } catch (IOException ignored) {} reply(player, book, () -> new MessageMediaUploadAck(message.id, -1, MediaIO.error(ex)).sendTo(player)); }
        })) new MessageMediaUploadAck(message.id, -1, "busy").sendTo(player);
    }
    public static void download(EntityPlayerMP player, MessageMediaRequest message)
    {
        var book = ServerQuestFile.INSTANCE; if (book == null) return;
        var key = new DiskMediaTransfers.Key(player.getUniqueID(), message.id);
        if (!submit(() -> {
            try
            {
                expire();
                if (message.offset == -1) { DOWNLOADS.remove(key); return; }
                if(QuestMedia.video(message.name))throw new IOException("local_video");
                Download value = DOWNLOADS.get(key);
                if (message.offset == 0 && value == null)
                {
                    if (DOWNLOADS.size() >= 16 || DOWNLOADS.keySet().stream().filter(k -> k.owner().equals(key.owner())).count() >= 2) throw new IOException("busy");
                    Path file = root(book).resolve(message.name);
                    if (!QuestMedia.validName(message.name) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("missing");
                    long size = Files.size(file); if (size < 1 || size > QuestMedia.limit(message.name)) throw new IOException("size");
                    value = new Download(message.name, file, (int)size, 0, System.currentTimeMillis());
                }
                if (value == null || value.next != message.offset || !value.name.equals(message.name)) throw new IOException("invalid");
                List<MessageMediaData> parts = new ArrayList<>(); int next = value.next;
                try (RandomAccessFile file = new RandomAccessFile(value.file.toFile(), "r"))
                {
                    file.seek(next);
                    for (int i = 0; i < QuestMedia.WINDOW && next < value.total; i++)
                    { byte[] bytes = new byte[Math.min(QuestMedia.CHUNK, value.total - next)]; file.readFully(bytes); parts.add(new MessageMediaData(message.id, message.name, value.total, next, bytes)); next += bytes.length; }
                }
                if (next == value.total) DOWNLOADS.remove(key);
                else DOWNLOADS.put(key, new Download(value.name, value.file, value.total, next, System.currentTimeMillis()));
                reply(player, book, () -> parts.forEach(part -> part.sendTo(player)));
            }
            catch (IOException | IllegalArgumentException ex) { DOWNLOADS.remove(key); reply(player, book, () -> new MessageMediaData(message.id, message.name, MediaIO.error(ex)).sendTo(player)); }
        })) new MessageMediaData(message.id, message.name, "busy").sendTo(player);
    }
    private static boolean submit(Runnable task) { try { IO.execute(task); return true; } catch (RejectedExecutionException ex) { return false; } }
    private static void expire() throws IOException
    { UPLOADS.expire(); long now = System.currentTimeMillis(); DOWNLOADS.values().removeIf(value -> now - value.touched > 60_000); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event)
    { if (event.phase == TickEvent.Phase.END && ++ticks % 200 == 0) submit(() -> { try { expire(); } catch (IOException ex) { FTBQuests.LOGGER.warn("Could not clean quest media transfers", ex); } }); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event)
    { UUID owner = event.player.getUniqueID(); submit(() -> { try { UPLOADS.removeOwner(owner); } catch (IOException ignored) {} DOWNLOADS.keySet().removeIf(key -> key.owner().equals(owner)); }); }
    public static void stop() { submit(() -> { try { UPLOADS.close(); } catch (IOException ignored) {} DOWNLOADS.clear(); }); }
}
