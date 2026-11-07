package com.feed_the_beast.ftbquests.client;

import dev.yudream.media.api.client.*;
import dev.yudream.media.client.VideoSupportScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;

/** Loaded only when media_api is present, keeping every API reference outside the fallback path. */
final class MediaApiBridge implements VideoBackend.PlayerBridge
{
    private final VideoPlayer video;
    MediaApiBridge(String source)
    {
        VideoRuntime.addFallbackLibrary(Loader.instance().getConfigDir().toPath().resolve("ftbquests/video-libs/watermedia-1.3.43.jar"));
        video = VideoBackend.isOnline(source) ? MediaAPI.open(source) : MediaAPI.openAsync(() -> new VideoSource(VideoBackend.resolve(source)));
    }
    @Override public int texture() { return video.textureId(); }
    @Override public int width() { return video.width(); }
    @Override public int height() { return video.height(); }
    @Override public long positionMillis() { return video.positionMillis(); }
    @Override public long durationMillis() { return video.durationMillis(); }
    @Override public boolean seekable() { return video.isSeekable(); }
    @Override public void seekTo(long milliseconds) { video.seekTo(milliseconds); }
    @Override public String status() { return video.status(); }
    @Override public VideoBackend.SupportIssue issue()
    {
        return switch (video.problem()) {
            case LIBRARY_MISSING -> VideoBackend.SupportIssue.LIBRARY;
            case LIBRARY_INVALID -> VideoBackend.SupportIssue.INVALID_LIBRARY;
            case VLC_UNAVAILABLE -> VideoBackend.SupportIssue.VLC;
            default -> VideoBackend.SupportIssue.NONE;
        };
    }
    @Override public boolean playing() { return video.isPlaying(); }
    @Override public void toggle() { video.toggle(); }
    @Override public void replay() { if(video.problem()==PlaybackProblem.SOURCE_UNAVAILABLE){video.retry();video.play();}else video.replay(); }
    @Override public void support() { Minecraft mc=Minecraft.getMinecraft(); mc.displayGuiScreen(new VideoSupportScreen(mc.currentScreen,video.problem())); }
    @Override public void close() { video.close(); }
}
