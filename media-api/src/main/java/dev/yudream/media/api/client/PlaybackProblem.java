package dev.yudream.media.api.client;

public enum PlaybackProblem
{
    NONE(false, ""), LIBRARY_MISSING(true, "missing_library"), LIBRARY_INVALID(true, "invalid_library"),
    VLC_UNAVAILABLE(true, "missing_vlc"), SOURCE_UNAVAILABLE(false, "source_unavailable"), PLAYBACK_FAILED(false, "playback_error");
    private final boolean runtime;
    private final String key;
    PlaybackProblem(boolean runtime, String key) { this.runtime = runtime; this.key = key; }
    public boolean needsSetup() { return runtime; }
    public String translationKey() { return "media_api.video." + key; }
}
