package com.feed_the_beast.ftbquests.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-local request ordering; no revisions are written into the quest book. */
public final class ClientEditSync
{
    public static final UUID UNSOLICITED = new UUID(0, 0);
    public final UUID session = UUID.randomUUID();
    private long sequence;
    private final Map<Integer, Long> latest = new HashMap<>();
    private final Set<Integer> pending = new HashSet<>();

    public long begin(int id)
    {
        long revision = ++sequence;
        latest.put(id, revision);
        pending.add(id);
        return revision;
    }

    /** The latest reply is authoritative, including a rejected edit's restored data. */
    public boolean receive(int id, UUID source, long revision)
    {
        if (session.equals(source))
        {
            if (revision != latest.getOrDefault(id, 0L)) return false;
            return pending.remove(id);
        }
        // Server broadcasts precede our later request's reply on the same ordered channel.
        return !pending.contains(id);
    }
}
