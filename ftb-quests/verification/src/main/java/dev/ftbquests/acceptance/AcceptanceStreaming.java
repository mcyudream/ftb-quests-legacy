package dev.ftbquests.acceptance;

import com.feed_the_beast.ftbquests.util.BilibiliVideo;
import com.feed_the_beast.ftbquests.util.QuestMedia;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import dev.yudream.media.api.client.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;

/** A local HTTP fixture verifies streaming and VLC headers; it is not a Bilibili live-site check. */
final class AcceptanceStreaming implements AutoCloseable
{
    private final HttpServer server;
    private final VideoPlayer player;
    private final String source;
    private final Path mediaRoot;
    private final Map<String,String> before;
    private final boolean douyin;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicBoolean referer = new AtomicBoolean(), userAgent = new AtomicBoolean();
    private static final String PAGE = "https://www.bilibili.com/video/BV1B7411m7LV?p=1";

    AcceptanceStreaming(Path fixture, Path mediaRoot) throws Exception
    {this(fixture,mediaRoot,false);}
    AcceptanceStreaming(Path fixture, Path mediaRoot,boolean douyin) throws Exception
    {
        this.douyin=douyin;
        this.mediaRoot = mediaRoot;
        before = snapshot(mediaRoot);
        byte[] bytes = Files.readAllBytes(fixture);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool(task -> { Thread thread = new Thread(task,"Acceptance HTTP video"); thread.setDaemon(true); return thread; }));
        server.createContext("/stream.avi", exchange -> {
            try (exchange)
            {
                referer.compareAndSet(false, (douyin?"https://www.iesdouyin.com/share/video/7423456789012345678/":PAGE).equals(exchange.getRequestHeaders().getFirst("Referer")));
                String agent = exchange.getRequestHeaders().getFirst("User-Agent");
                userAgent.compareAndSet(false, agent != null && agent.contains("Mozilla/5.0"));
                String range = exchange.getRequestHeaders().getFirst("Range");
                int start = 0, end = bytes.length - 1;
                if (range != null && range.matches("bytes=[0-9]+-[0-9]*"))
                {
                    String[] parts = range.substring(6).split("-",-1);
                    start = Integer.parseInt(parts[0]);
                    if (!parts[1].isEmpty()) end = Math.min(end,Integer.parseInt(parts[1]));
                }
                exchange.getResponseHeaders().set("Content-Type","video/x-msvideo");
                exchange.getResponseHeaders().set("Accept-Ranges","bytes");
                exchange.getResponseHeaders().set("Cache-Control","no-store");
                if (start < 0 || start > end || start >= bytes.length) { exchange.sendResponseHeaders(416,-1); return; }
                int length = end - start + 1;
                exchange.getResponseHeaders().set("Content-Length",Integer.toString(length));
                if (range != null) exchange.getResponseHeaders().set("Content-Range","bytes " + start + "-" + end + "/" + bytes.length);
                exchange.sendResponseHeaders(range == null ? 200 : 206,exchange.getRequestMethod().equals("HEAD") ? -1 : length);
                if (!exchange.getRequestMethod().equals("HEAD")) { exchange.getResponseBody().write(bytes,start,length); sent.addAndGet(length); }
            }
            catch (IOException ignored) { /* VLC may close a seek or probe request early. */ }
        });
        server.start();
        String host = server.getAddress().getAddress().getHostAddress();
        if (host.contains(":")) host = "[" + host + "]";
        source="http://" + host + ":" + server.getAddress().getPort() + "/stream.avi";
        JsonObject entry = new JsonObject(); entry.addProperty("url",source);
        JsonArray durl = new JsonArray(); durl.add(entry);
        JsonObject data = new JsonObject(); data.add("durl",durl);
        JsonObject response = new JsonObject(); response.addProperty("code",0); response.add("data",data);
        if(douyin){
            throw new UnsupportedOperationException("The Douyin backend fixture requires the independent Media API runtime source project");
        }else{
            BilibiliVideo.Stream stream = BilibiliVideo.stream(response,BilibiliVideo.parse("BV1B7411m7LV"));
            player = MediaAPI.openAsync(() -> new VideoSource(stream.url(), Arrays.asList(stream.options())));
        }
    }
    String status() { return player.status(); }
    String source() { return source; }
    boolean hasFrame() { return player.textureId() > 0; }
    void play() { player.play(); }
    void finish(Map<String,Object> checks) throws Exception
    {
        player.pause(); player.replay();
        AcceptanceMod.require(sent.get() > 0,"HTTP fixture did not stream any video bytes");
        AcceptanceMod.require(referer.get() && userAgent.get(),"VLC did not pass Bilibili stream headers");
        close();
        AcceptanceMod.require(before.equals(snapshot(mediaRoot)),"Online playback wrote a video file to quest media/cache directories");
        checks.put(douyin?"douyin_metadata_fixture_native_frame_and_headers_without_video_cache":"http_stream_native_frame_headers_pause_replay_without_media_files",true);
    }
    static Map<String,String> snapshot(Path mediaRoot) throws IOException
    {
        Map<String,String> result = new TreeMap<>();
        for (String folder : List.of("media",".media-cache",".video-cache"))
        {
            Path root = mediaRoot.resolve(folder); if (!Files.exists(root)) continue;
            try (var files = Files.walk(root))
            { for (Path file : files.filter(Files::isRegularFile).toList()) result.put(mediaRoot.relativize(file).toString(),QuestMedia.hash(file)); }
        }
        return result;
    }
    @Override public void close() { player.close(); server.stop(0); }
}
