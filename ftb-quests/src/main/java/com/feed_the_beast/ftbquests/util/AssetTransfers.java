package com.feed_the_beast.ftbquests.util;

import java.util.*;

/** Bounded, expiring assembly of 1.12 custom payloads, with replay suppression. */
public final class AssetTransfers
{
    public static final int CHUNK_SIZE = 24 * 1024;
    private static final long TIMEOUT = 30_000;
    public record Key(UUID owner, UUID transfer) {}
    private record Incoming(String metadata, byte[] bytes, BitSet received, long started) {}
    private final Map<Key, Incoming> incoming = new HashMap<>();
    private final Map<Key, Long> completed = new LinkedHashMap<>();

    public static void validate(int total, int offset, int length)
    {
        if (total < 1 || total > QuestAssets.MAX_BYTES || offset < 0 || offset >= total
                || offset % CHUNK_SIZE != 0 || length != Math.min(CHUNK_SIZE, total - offset))
            throw new IllegalArgumentException("Invalid quest asset chunk");
    }

    public synchronized byte[] accept(Key key, String metadata, int total, int offset, byte[] bytes)
    {
        validate(total, offset, bytes.length);
        long now = System.currentTimeMillis();
        incoming.values().removeIf(value -> now - value.started > TIMEOUT);
        completed.values().removeIf(value -> now - value > TIMEOUT);
        if (completed.containsKey(key)) return null;
        Incoming value = incoming.get(key);
        if (value == null)
        {
            long memory = incoming.values().stream().mapToLong(entry -> entry.bytes.length).sum();
            long owned = incoming.keySet().stream().filter(entry -> entry.owner.equals(key.owner)).count();
            if (incoming.size() >= 16 || owned >= 2 || memory + total > 32 * 1024 * 1024)
                throw new IllegalArgumentException("Too many quest asset transfers");
            value = new Incoming(metadata, new byte[total], new BitSet(), now);
            incoming.put(key, value);
        }
        if (value.bytes.length != total || !value.metadata.equals(metadata))
        {
            incoming.remove(key);
            throw new IllegalArgumentException("Quest asset metadata changed during transfer");
        }
        int part = offset / CHUNK_SIZE;
        if (value.received.get(part))
        {
            for (int i = 0; i < bytes.length; i++) if (value.bytes[offset + i] != bytes[i])
                throw new IllegalArgumentException("Conflicting quest asset chunk");
        }
        else
        {
            System.arraycopy(bytes, 0, value.bytes, offset, bytes.length);
            value.received.set(part);
        }
        if (value.received.cardinality() != (total + CHUNK_SIZE - 1) / CHUNK_SIZE) return null;
        incoming.remove(key);
        completed.put(key, now);
        while (completed.size() > 256) completed.remove(completed.keySet().iterator().next());
        return value.bytes;
    }

    public synchronized void clear() { incoming.clear(); completed.clear(); }
}
