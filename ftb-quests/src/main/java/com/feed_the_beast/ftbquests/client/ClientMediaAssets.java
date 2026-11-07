package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.net.*;
import com.feed_the_beast.ftbquests.net.edit.MessageMediaUpload;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Main-thread protocol state, bounded background disk IO and explicit server confirmation. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = FTBQuests.MOD_ID, value = Side.CLIENT)
public final class ClientMediaAssets
{
    private static final ExecutorService IO = MediaIO.worker("Quest client media");
    private static final UUID SERVER = new UUID(0, 0);
    private static final DiskMediaTransfers INCOMING = new DiskMediaTransfers();
    private static final Map<UUID, Import> IMPORTS = new HashMap<>();
    private static final Map<UUID, Download> DOWNLOADS = new HashMap<>();
    private static final Map<String, CompletableFuture<Path>> RESOLVING = new ConcurrentHashMap<>();
    private static volatile int generation;
    public static Path cache() { return Loader.instance().getConfigDir().toPath().resolve("ftbquests/.media-cache"); }
    private static void main(Runnable action) { Minecraft.getMinecraft().addScheduledTask(action); }
    public static final class Import
    {
        public final UUID id = UUID.randomUUID();
        public final CompletableFuture<String> result = new CompletableFuture<>();
        private volatile Path file;
        private volatile String name = "";
        private volatile boolean cancelled;
        private boolean localVideo;
        private int total, sent, acknowledged;
        private long touched = System.currentTimeMillis();
        public String phase() { return localVideo ? "copying_video" : name.isEmpty() ? "preparing" : "uploading"; }
        public int percent() { return total == 0 ? 0 : (int)(100L * acknowledged / total); }
        public void cancel()
        {
            cancelled = true; IMPORTS.remove(id); result.cancel(false);
            if (!localVideo && !name.isEmpty() && Minecraft.getMinecraft().getConnection() != null) new MessageMediaUpload(id, "", 0, -2, new byte[0]).sendToServer();
        }
    }
    private static final class Download
    {
        final UUID id = UUID.randomUUID(); final String name; final CompletableFuture<Path> result;
        int total, received; volatile boolean finished;
        long touched = System.currentTimeMillis();
        Download(String name, CompletableFuture<Path> result) { this.name = name; this.result = result; }
    }
    /** Called from an editor on the client thread. */
    public static Import importFile(Path selected, boolean video)
    {
        Import operation = new Import(); operation.localVideo=video;
        if (IMPORTS.size() >= 2) { operation.result.completeExceptionally(new IOException("busy")); return operation; }
        IMPORTS.put(operation.id, operation); int session = generation;
        work(() -> {
            try
            {
                Path directory=video?LocalQuestVideo.directory(Loader.instance().getConfigDir().toPath()):cache();
                Path file = QuestMedia.prepare(selected, video, directory, () -> operation.cancelled || session != generation);
                main(() -> {
                    if (operation.cancelled || session != generation) return;
                    try {
                        operation.file = file; operation.name = file.getFileName().toString(); operation.total = (int)Files.size(file);
                        operation.touched = System.currentTimeMillis();
                        if(video){operation.acknowledged=operation.total;IMPORTS.remove(operation.id);operation.result.complete("media:"+operation.name);return;}
                        if (Minecraft.getMinecraft().getConnection() == null) throw new IOException("missing");
                        new MessageMediaUpload(operation.id, operation.name, operation.total, -1, new byte[0]).sendToServer();
                    } catch (Exception ex) { fail(operation, ex); }
                });
            }
            catch (Exception ex) { main(() -> fail(operation, ex)); }
        }, () -> fail(operation, new IOException("busy")));
        return operation;
    }
    private static void fail(Import operation, Throwable error)
    {
        IMPORTS.remove(operation.id); operation.cancelled = true; operation.result.completeExceptionally(error);
        if (!operation.localVideo && !operation.name.isEmpty() && Minecraft.getMinecraft().getConnection() != null) new MessageMediaUpload(operation.id,"",0,-2,new byte[0]).sendToServer();
    }
    public static void acknowledge(UUID id, int next, String error)
    {
        Import operation = IMPORTS.get(id); if (operation == null || operation.cancelled) return;
        if (!error.isEmpty()) { fail(operation, new IOException(error)); return; }
        if (next != operation.sent && !(next == operation.total && operation.sent == 0)) { fail(operation, new IOException("invalid")); return; }
        operation.acknowledged = next; operation.touched = System.currentTimeMillis();
        if (next == operation.total)
        { IMPORTS.remove(id); operation.result.complete("media:" + operation.name); return; }
        int end = Math.min(operation.total, next + QuestMedia.CHUNK * QuestMedia.WINDOW), session = generation;
        operation.sent = end;
        work(() -> {
            try
            {
                List<MessageMediaUpload> parts = new ArrayList<>();
                try (RandomAccessFile file = new RandomAccessFile(operation.file.toFile(), "r"))
                {
                    file.seek(next);
                    for (int offset = next; offset < end; offset += QuestMedia.CHUNK)
                    { byte[] part = new byte[Math.min(QuestMedia.CHUNK, end - offset)]; file.readFully(part); parts.add(new MessageMediaUpload(id, operation.name, operation.total, offset, part)); }
                }
                main(() -> { if (!operation.cancelled && !operation.result.isDone() && session == generation) parts.forEach(MessageMediaUpload::sendToServer); });
            }
            catch (Exception ex) { main(() -> fail(operation, ex)); }
        }, () -> fail(operation, new IOException("busy")));
    }
    /** May be called by the video resolver or renderer; identical requests share a future. */
    public static CompletableFuture<Path> resolve(String name)
    {
        if (!QuestMedia.validName(name)) return CompletableFuture.failedFuture(new IOException("invalid"));
        if(QuestMedia.video(name)){
            CompletableFuture<Path> local=new CompletableFuture<>();
            work(()->{try{local.complete(LocalQuestVideo.resolve(Loader.instance().getConfigDir().toPath(),"media:"+name));}catch(Exception error){local.completeExceptionally(error);}},()->local.completeExceptionally(new IOException("busy")));
            return local;
        }
        CompletableFuture<Path> result = new CompletableFuture<>(), previous = RESOLVING.putIfAbsent(name, result);
        if (previous != null) return previous;
        result.whenComplete((path, error) -> { if (error != null) RESOLVING.remove(name, result); });
        int session = generation;
        work(() -> {
            Path file = cache().resolve(name);
            try
            {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                {
                    try { QuestMedia.validate(file, name); if (session == generation) { result.complete(file); return; } }
                    catch (IOException ex) { Files.deleteIfExists(file); }
                }
                main(() -> {
                    if (session != generation || result.isDone()) return;
                    if (DOWNLOADS.size() >= 2 || Minecraft.getMinecraft().getConnection() == null) { result.completeExceptionally(new IOException("busy")); return; }
                    Download download = new Download(name, result); DOWNLOADS.put(download.id, download);
                    new MessageMediaRequest(download.id, name, 0).sendToServer();
                });
            }
            catch (Exception ex) { result.completeExceptionally(ex); }
        }, () -> result.completeExceptionally(new IOException("busy")));
        return result;
    }
    public static void receive(MessageMediaData message)
    {
        Download value = DOWNLOADS.get(message.id); if (value == null || value.finished) return;
        if (!message.name.equals(value.name) || !message.error.isEmpty()) { fail(value, new IOException(message.error.isEmpty() ? "invalid" : message.error)); return; }
        if (message.offset != value.received || value.total != 0 && message.total != value.total) { fail(value, new IOException("invalid")); return; }
        value.total = message.total; value.received += message.part.length; value.touched = System.currentTimeMillis();
        int session = generation; var key = new DiskMediaTransfers.Key(SERVER, value.id);
        work(() -> {
            if (value.finished || session != generation) return;
            try
            {
                int next;
                if (message.offset == 0 && INCOMING.begin(key, cache(), value.name, message.total) == message.total) next = message.total;
                else next = INCOMING.accept(key, value.name, message.total, message.offset, message.part);
                if (next == message.total)
                {
                    value.finished = true;
                    main(() -> { if (session == generation) { DOWNLOADS.remove(value.id); value.result.complete(cache().resolve(value.name)); } });
                }
                else if (next % (QuestMedia.CHUNK * QuestMedia.WINDOW) == 0)
                    main(() -> { if (!value.finished && session == generation) new MessageMediaRequest(value.id, value.name, next).sendToServer(); });
            }
            catch (Exception ex) { main(() -> fail(value, ex)); }
        }, () -> fail(value, new IOException("busy")));
    }
    private static void fail(Download value, Throwable error)
    {
        value.finished = true; DOWNLOADS.remove(value.id); value.result.completeExceptionally(error);
        if (Minecraft.getMinecraft().getConnection() != null) new MessageMediaRequest(value.id, value.name, -1).sendToServer();
        work(() -> { try { INCOMING.cancel(new DiskMediaTransfers.Key(SERVER, value.id)); } catch (IOException ignored) {} }, () -> {});
    }
    public static int progress(String name)
    { for (Download value : DOWNLOADS.values()) if (value.name.equals(name)) return value.total == 0 ? 0 : (int)(100L * value.received / value.total); return -1; }
    private static void work(Runnable task, Runnable rejected)
    { try { IO.execute(task); } catch (RejectedExecutionException ex) { main(rejected); } }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        for (Import operation : List.copyOf(IMPORTS.values())) if (!operation.name.isEmpty() && now - operation.touched > 60_000) fail(operation,new IOException("timeout"));
        for (Download value : List.copyOf(DOWNLOADS.values())) if (now - value.touched > 60_000) fail(value, new IOException("timeout"));
    }
    @SubscribeEvent public static void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { main(ClientMediaAssets::clear); }
    public static void clear()
    {
        generation++;
        for (Import operation : IMPORTS.values()) { operation.cancelled = true; operation.result.completeExceptionally(new IOException("cancelled")); }
        IMPORTS.clear();
        for (Download value : DOWNLOADS.values()) { value.finished = true; value.result.completeExceptionally(new IOException("cancelled")); }
        DOWNLOADS.clear(); for (var future : List.copyOf(RESOLVING.values())) future.completeExceptionally(new IOException("cancelled")); RESOLVING.clear();
        work(() -> { try { INCOMING.close(); } catch (IOException ignored) {} }, () -> {});
    }
}
