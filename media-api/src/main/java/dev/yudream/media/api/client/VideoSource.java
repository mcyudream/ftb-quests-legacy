package dev.yudream.media.api.client;

import java.util.*;

/** A resolved URI and per-player VLC options. Options are copied and are never global. */
public record VideoSource(String uri, List<String> options)
{
    public VideoSource { Objects.requireNonNull(uri); options = List.copyOf(options); }
    public VideoSource(String uri) { this(uri, List.of()); }
}
