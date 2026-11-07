package com.feed_the_beast.ftbquests.util;

import java.util.concurrent.*;

public final class MediaIO
{
    private MediaIO() {}
    public static ExecutorService worker(String name)
    {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), task -> {
            Thread thread = new Thread(task, name); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public static String error(Throwable failure)
    {
        while (failure.getCause() != null) failure = failure.getCause();
        String key = failure.getMessage();
        return java.util.Set.of("format", "dimensions", "size", "busy", "invalid", "cancelled", "permission", "missing", "timeout", "local_video").contains(key == null ? "" : key) ? key : "io";
    }
}
