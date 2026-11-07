package dev.yudream.media.api.client;
import java.util.concurrent.Callable;
/** Build-only signature. Install the independent Media API runtime to play video. */
public final class MediaAPI {
 public static VideoPlayer open(String source) { throw new UnsupportedOperationException("Compile-only SDK"); }
 public static VideoPlayer openAsync(Callable<VideoSource> source) { throw new UnsupportedOperationException("Compile-only SDK"); }
}
