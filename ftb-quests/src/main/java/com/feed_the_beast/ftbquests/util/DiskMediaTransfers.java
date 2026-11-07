package com.feed_the_beast.ftbquests.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Ordered, bounded disk spooling. At most one small packet is held while writing, even for large videos. */
public final class DiskMediaTransfers implements AutoCloseable
{
    public record Key(UUID owner, UUID id) {}
    private static final long TIMEOUT = 60_000;
    private static final class Incoming
    {
        Path temporary, directory; String name; int total, next; long touched;
    }
    private final Map<Key, Incoming> incoming = new HashMap<>();
    private final LongSupplier clock;
    public DiskMediaTransfers() { this(System::currentTimeMillis); }
    public DiskMediaTransfers(LongSupplier clock) { this.clock = clock; }
    public synchronized int begin(Key key, Path directory, String name, int total) throws IOException
    {
        expire();
        if (!QuestMedia.validName(name) || total < 1 || total > QuestMedia.limit(name) || incoming.containsKey(key)) throw new IOException("invalid");
        if (incoming.size() >= 8 || incoming.keySet().stream().filter(k -> k.owner.equals(key.owner)).count() >= 2
                || incoming.values().stream().mapToLong(v -> v.total).sum() + total > 512L * 1024 * 1024) throw new IOException("busy");
        Path existing = directory.resolve(name);
        if (Files.isRegularFile(existing, LinkOption.NOFOLLOW_LINKS) && Files.size(existing) == total)
        {
            try { QuestMedia.validate(existing, name); return total; } catch (IOException ignored) { /* Replace a corrupt cache with the checked upload. */ }
        }
        Files.createDirectories(directory.resolve(".incoming"));
        Incoming value = new Incoming(); value.directory = directory; value.name = name; value.total = total; value.touched = clock.getAsLong();
        value.temporary = Files.createTempFile(directory.resolve(".incoming"), "media-", ".part");
        incoming.put(key, value); return 0;
    }
    public synchronized int accept(Key key, String name, int total, int offset, byte[] part) throws IOException
    {
        expire(); Incoming value = incoming.get(key);
        try
        {
            QuestMedia.validateChunk(name, total, offset, part.length);
            if (value == null || !value.name.equals(name) || total != value.total || offset != value.next) throw new IOException("invalid");
            Files.write(value.temporary, part, StandardOpenOption.APPEND);
            value.next += part.length; value.touched = clock.getAsLong();
            if (value.next == total)
            {
                QuestMedia.validate(value.temporary, name); QuestMedia.commit(value.temporary, value.directory, name); incoming.remove(key);
            }
            return value.next;
        }
        catch (IOException | IllegalArgumentException ex) { cancel(key); throw ex; }
    }
    public synchronized void cancel(Key key) throws IOException
    { Incoming value = incoming.remove(key); if (value != null) Files.deleteIfExists(value.temporary); }
    public synchronized void removeOwner(UUID owner) throws IOException
    { for (Key key : List.copyOf(incoming.keySet())) if (key.owner.equals(owner)) cancel(key); }
    public synchronized void expire() throws IOException
    { long now = clock.getAsLong(); for (var entry : List.copyOf(incoming.entrySet())) if (now - entry.getValue().touched > TIMEOUT) cancel(entry.getKey()); }
    @Override public synchronized void close() throws IOException
    { for (Key key : List.copyOf(incoming.keySet())) cancel(key); }
}
