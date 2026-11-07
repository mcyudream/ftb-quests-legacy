package com.feed_the_beast.ftbquests.util;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BilibiliVideoTest
{
    private JsonObject json(String text) { return new JsonParser().parse(text).getAsJsonObject(); }
    @Test void normalEmbedAndBareIdsPreservePageAndStartWithoutTrackingParameters() throws Exception
    {
        var video=BilibiliVideo.parse("https://www.bilibili.com/video/BV1B7411m7LV/?p=2&t=35&spm_id_from=tracking");
        assertEquals("bvid=BV1B7411m7LV",video.query());assertEquals(2,video.page());assertEquals(35,video.seconds());assertFalse(video.pageUrl().contains("tracking"));
        assertEquals(video,BilibiliVideo.parse(video.embedUrl()));assertEquals("aid=170001",BilibiliVideo.parse("av170001").query());
        assertEquals("BV1B7411m7LV",BilibiliVideo.parse("BV1B7411m7LV").id());
        assertTrue(BilibiliVideo.matches("https://b23.tv/abc"));
    }
    @Test void unrelatedHostsMalformedLinksAndInvalidPagesAreRejected() throws Exception
    {
        for(String source:List.of("https://www.bilibili.com.evil.example/video/BV1B7411m7LV","https://www.bilibili.com@evil.example/video/BV1B7411m7LV","https://www.bilibili.com/video/BV1B7411m7LV?p=-1","file://www.bilibili.com/video/BV1B7411m7LV","https://www.bilibili.com:123/video/BV1B7411m7LV","https://live.bilibili.com/123","https://www.bilibili.com/bangumi/play/ep1"))
            assertThrows(IOException.class,()->BilibiliVideo.parse(source),source);
        assertFalse(BilibiliVideo.matches("https://www.bilibili.com.evil.example/video/BV1B7411m7LV"));
    }
    @Test void publicResponseSelectsRequestedPageAndVlcStreamsWithoutFileOptions() throws Exception
    {
        assertEquals(99,BilibiliVideo.cid(json("{\"code\":0,\"data\":[{\"cid\":1},{\"cid\":99}]}"),2));
        var stream=BilibiliVideo.stream(json("{\"code\":0,\"data\":{\"durl\":[{\"url\":\"https://cdn.bilivideo.com/video.mp4?deadline=123\"}]}}"),BilibiliVideo.parse("BV1B7411m7LV"));
        assertTrue(stream.url().startsWith("https://cdn.bilivideo.com/"));assertTrue(List.of(stream.options()).stream().anyMatch(option->option.startsWith(":http-referrer=")));
        assertTrue(List.of(stream.options()).stream().noneMatch(option->option.contains("sout")||option.contains("file:")));
    }
    @Test void missingRestrictedAndSplitResponsesAreNotSilentlyPlayedAsCompleteVideos() throws Exception
    {
        var video=BilibiliVideo.parse("BV1B7411m7LV");
        assertThrows(IOException.class,()->BilibiliVideo.stream(json("{\"code\":-10403,\"message\":\"login required\"}"),video));
        assertThrows(IOException.class,()->BilibiliVideo.stream(json("{\"code\":0,\"data\":{\"durl\":[{\"url\":\"https://x/1\"},{\"url\":\"https://x/2\"}]}}"),video));
        assertThrows(IOException.class,()->BilibiliVideo.stream(json("{\"code\":0,\"data\":{\"durl\":[{\"url\":\"file:///tmp/x\"}]}}"),video));
        assertThrows(IOException.class,()->BilibiliVideo.cid(json("{\"code\":0,\"data\":[]}"),1));
    }
}
