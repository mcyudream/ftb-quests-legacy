package dev.yudream.media.api.client;

/** Client thread API. Keep one handle per view and close it when that view is discarded. */
public interface VideoPlayer extends AutoCloseable
{
    boolean isReady();
    boolean isPlaying();
    /** Uploads the most recent frame on the render thread; returns -1 until a frame exists. */
    int textureId();
    /** Original video dimensions, or 0 until the decoder knows them. No GL upload or blocking I/O. */
    default int width() { return 0; }
    default int height() { return 0; }
    /** Playback position in milliseconds, or 0 before playback/after closing. */
    default long positionMillis() { return 0; }
    /** Total duration in milliseconds; 0 means unknown (for example a live stream). */
    default long durationMillis() { return 0; }
    /** True only when the ready source has a known duration and supports seeking. */
    default boolean isSeekable() { return false; }
    String status();
    PlaybackProblem problem();
    void play();
    void pause();
    void toggle();
    void replay();
    /** Seeks within the source bounds without changing play/pause state. Ignored if not seekable. */
    void seekTo(long milliseconds);
    /** Re-resolves the original source after an error. Does not download any video file. */
    void retry();
    @Override void close();
}
