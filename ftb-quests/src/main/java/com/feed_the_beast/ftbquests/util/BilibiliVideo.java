package com.feed_the_beast.ftbquests.util;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** URL parsing and API response selection only. No video data is downloaded or written here. */
public final class BilibiliVideo
{
    private static final Pattern BV = Pattern.compile("BV[0-9A-Za-z]{10}"), AV = Pattern.compile("av[1-9][0-9]{0,17}");
    public record Video(String parameter, String id, int page, int seconds)
    {
        public String query() { return parameter + "=" + id; }
        public String pageUrl() { return "https://www.bilibili.com/video/" + (parameter.equals("aid") ? "av" : "") + id + "?p=" + page; }
        public String embedUrl() { return "https://player.bilibili.com/player.html?" + query() + "&p=" + page + "&autoplay=0&t=" + seconds; }
    }
    public record Stream(String url, String[] options) {}
    private BilibiliVideo() {}
    public static boolean matches(String source)
    {
        if (BV.matcher(source.trim()).matches() || AV.matcher(source.trim()).matches()) return true;
        try { URI uri = URI.create(source.trim()); return Set.of("bilibili.com","www.bilibili.com","m.bilibili.com","player.bilibili.com","b23.tv","www.b23.tv").contains(uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT)); }
        catch (RuntimeException ex) { return false; }
    }
    public static boolean shortLink(URI uri) { return uri.getHost() != null && Set.of("b23.tv","www.b23.tv").contains(uri.getHost().toLowerCase(Locale.ROOT)); }
    public static Video parse(String source) throws IOException
    {
        String text = source.trim(); String id = text, parameter = "bvid"; Map<String,String> query = new HashMap<>();
        try
        {
            if (!BV.matcher(text).matches() && !AV.matcher(text).matches())
            {
                URI uri = URI.create(text);
                if (!matches(text) || shortLink(uri) || uri.getUserInfo() != null || uri.getPort() != -1 || !Set.of("http","https").contains(uri.getScheme())) throw new IOException("link");
                if (uri.getRawQuery() != null) for (String entry : uri.getRawQuery().split("&")) { String[] pair = entry.split("=",2); if (pair.length == 2) query.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8)); }
                if (uri.getHost().equalsIgnoreCase("player.bilibili.com")) id = query.containsKey("bvid") ? query.get("bvid") : "av" + query.getOrDefault("aid","");
                else { var match = Pattern.compile("^/video/(BV[0-9A-Za-z]{10}|av[1-9][0-9]{0,17})/?$").matcher(uri.getPath()); if (!match.matches()) throw new IOException("link"); id = match.group(1); }
            }
            if (AV.matcher(id).matches()) { parameter = "aid"; id = id.substring(2); }
            else if (!BV.matcher(id).matches()) throw new IOException("link");
            int page = Integer.parseInt(query.getOrDefault("p","1")), seconds = Integer.parseInt(query.getOrDefault("t","0"));
            if (page < 1 || page > 10000 || seconds < 0 || seconds > 86400) throw new IOException("link");
            return new Video(parameter,id,page,seconds);
        }
        catch (IllegalArgumentException ex) { throw new IOException("link",ex); }
    }
    public static JsonElement data(JsonObject response) throws IOException
    {
        if (!response.has("code") || response.get("code").getAsInt() != 0 || !response.has("data") || response.get("data").isJsonNull()) throw new IOException("unavailable");
        return response.get("data");
    }
    public static long cid(JsonObject response, int page) throws IOException
    {
        try { JsonArray pages = data(response).getAsJsonArray(); if (page > pages.size()) throw new IOException("page"); long cid = pages.get(page-1).getAsJsonObject().get("cid").getAsLong(); if (cid < 1) throw new IOException("unavailable"); return cid; }
        catch (RuntimeException ex) { throw new IOException("unavailable",ex); }
    }
    private static String url(String value) throws IOException
    {
        try {
            URI uri = URI.create(value.startsWith("//") ? "https:" + value : value);
            if (!Set.of("http","https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getRawFragment() != null) throw new IOException("unavailable");
            return uri.toString();
        } catch (RuntimeException ex) { throw new IOException("unavailable",ex); }
    }
    public static Stream stream(JsonObject response, Video video) throws IOException
    {
        try
        {
            JsonObject data = data(response).getAsJsonObject();
            List<String> options = new ArrayList<>(List.of(":http-referrer=" + video.pageUrl(), ":http-user-agent=Mozilla/5.0", ":network-caching=1500"));
            if (video.seconds > 0) options.add(":start-time=" + video.seconds);
            JsonArray durl = data.has("durl") && data.get("durl").isJsonArray() ? data.getAsJsonArray("durl") : new JsonArray();
            if (durl.size() > 1) throw new IOException("segments");
            if (durl.size() == 1) return new Stream(url(durl.get(0).getAsJsonObject().get("url").getAsString()), options.toArray(String[]::new));
            // Some responses expose separate DASH tracks. VLC plays the audio slave directly, without merging files.
            JsonObject dash = data.getAsJsonObject("dash"); JsonObject selected = null; long bandwidth = Long.MAX_VALUE;
            for (JsonElement entry : dash.getAsJsonArray("video"))
            {
                JsonObject candidate = entry.getAsJsonObject(); if (candidate.has("codecid") && candidate.get("codecid").getAsInt() != 7) continue;
                long rate = candidate.get("bandwidth").getAsLong(); if (rate < bandwidth) { selected = candidate; bandwidth = rate; }
            }
            if (selected == null || !dash.has("audio") || dash.getAsJsonArray("audio").size() == 0) throw new IOException("unavailable");
            JsonObject audio = dash.getAsJsonArray("audio").get(0).getAsJsonObject();
            options.add(":input-slave=" + url(audio.has("baseUrl") ? audio.get("baseUrl").getAsString() : audio.get("base_url").getAsString()));
            return new Stream(url(selected.has("baseUrl") ? selected.get("baseUrl").getAsString() : selected.get("base_url").getAsString()), options.toArray(String[]::new));
        }
        catch (RuntimeException ex) { throw new IOException("unavailable",ex); }
    }
}
