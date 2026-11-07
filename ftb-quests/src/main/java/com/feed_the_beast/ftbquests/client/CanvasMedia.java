package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.icon.ImageIcon;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.net.MessageQuestAsset;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import org.w3c.dom.Node;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** PNG/GIF decoding is asynchronous; GL texture creation stays on the render thread. */
public final class CanvasMedia
{
    public record Dimensions(int width, int height) { public boolean known() { return width > 0 && height > 0; } }
    private static final ExecutorService DECODER = Executors.newFixedThreadPool(2, runnable -> { Thread t = new Thread(runnable, "Quest media decoder"); t.setDaemon(true); return t; });
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(32, .75F, true);
    private static final Map<String, Dimensions> SIZES = new LinkedHashMap<>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Dimensions> entry) { return size() > 1024; }
    };
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static final long MAX_CACHE_BYTES = 256L * 1024 * 1024;
    private static class Entry
    {
        CompletableFuture<Frames> pending;
        Frames frames;
        final Map<Integer, ResourceLocation> textures = new HashMap<>();
        final long started = System.currentTimeMillis();
    }
    private static class Frames
    {
        final List<BufferedImage> images = new ArrayList<>();
        final List<Integer> delays = new ArrayList<>();
        int duration;
    }
    private static long memory(Entry entry)
    {
        if (entry.frames == null) return 0;
        long bytes = 0;
        for (BufferedImage frame : entry.frames.images) bytes += (long)frame.getWidth() * frame.getHeight() * 4;
        for (int frame : entry.textures.keySet())
        { BufferedImage image = entry.frames.images.get(frame); bytes += (long)image.getWidth() * image.getHeight() * 4; }
        return bytes;
    }
    private static void release(Entry entry)
    {
        if (entry.pending != null) entry.pending.cancel(true);
        for (ResourceLocation texture : entry.textures.values()) Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);
    }
    private static void trim(String current)
    {
        long bytes = CACHE.values().stream().mapToLong(CanvasMedia::memory).sum();
        var iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext() && (CACHE.size() > 16 || bytes > MAX_CACHE_BYTES))
        {
            var item = iterator.next(); if (item.getKey().equals(current)) continue;
            bytes -= memory(item.getValue()); release(item.getValue()); iterator.remove();
        }
    }
    public static void drawFit(String source,int x,int y,int width,int height,int opacity)
    {
        Dimensions size=dimensions(source);
        if(size.known())
        {
            double scale=Math.min(width/(double)size.width(),height/(double)size.height());
            int w=(int)(size.width()*scale),h=(int)(size.height()*scale);draw(source,x+(width-w)/2,y+(height-h)/2,w,h,opacity);
        }
        else draw(source,x,y,width,height,opacity);
    }
    public static void invalidate()
    {
        for (Entry entry : CACHE.values()) release(entry);
        CACHE.clear(); SIZES.clear(); REQUESTED.clear(); VideoBackend.closeAll(); com.feed_the_beast.ftbquests.net.MessageQuestAsset.clearTransfers(); QuestFonts.clear();
    }
    public static void draw(String source, int x, int y, int width, int height, int opacity)
    { draw(source,x,y,width,height,opacity,0xFFFFFF); }
    public static void draw(String source, int x, int y, int width, int height, int opacity, int color)
    {
        if (source.isEmpty()) return;
        Entry entry = load(source);
        if (entry == null || entry.frames == null || entry.frames.images.isEmpty()) { new ImageIcon(ImageIcon.MISSING_IMAGE).withColor(Color4I.WHITE.withAlpha(opacity)).draw(x,y,width,height); return; }
        long elapsed = (System.currentTimeMillis() - entry.started) % entry.frames.duration;
        int frame = 0;
        while (frame < entry.frames.images.size() - 1 && elapsed >= entry.frames.delays.get(frame)) elapsed -= entry.frames.delays.get(frame++);
        final int index = frame;
        ResourceLocation texture = entry.textures.computeIfAbsent(index, key -> Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("quest_media", new DynamicTexture(entry.frames.images.get(index))));
        trim(source);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GuiHelper.drawTexturedRect(x, y, width, height, Color4I.rgb(color).withAlpha(opacity), 0, 0, 1, 1);
    }
    /** Only reads available metadata; scrolling/layout must not start decoders for off-screen media. */
    public static Dimensions dimensions(String source)
    {
        Dimensions known = SIZES.get(source);
        if (known != null) return known;
        Entry entry = CACHE.get(source);
        if (entry != null && entry.frames == null && entry.pending.isDone()) entry.frames = entry.pending.join();
        if (entry == null || entry.frames == null || entry.frames.images.isEmpty()) return new Dimensions(0,0);
        BufferedImage image = entry.frames.images.get(0);
        Dimensions size = new Dimensions(image.getWidth(),image.getHeight()); SIZES.put(source,size); trim(source); return size;
    }
    private static Entry load(String source)
    {
        if (source.isEmpty()) return null;
        if (source.startsWith("media:"))
        {
            String name = source.substring(6);
            if (!com.feed_the_beast.ftbquests.util.QuestMedia.validName(name) || com.feed_the_beast.ftbquests.util.QuestMedia.video(name)) return null;
            if (!Files.isRegularFile(ClientMediaAssets.cache().resolve(name)))
            {
                long now = System.currentTimeMillis();
                if (now - REQUESTED.getOrDefault(source, 0L) > 10000)
                { REQUESTED.put(source, now); ClientMediaAssets.resolve(name); }
                return null;
            }
        }
        if (source.startsWith("asset:"))
        {
            String name = source.substring(6);
            if (!QuestAssets.validName(name)) return null;
            Path file = Loader.instance().getConfigDir().toPath().resolve("ftbquests/.asset-cache").resolve(name);
            if (!Files.exists(file))
            {
                long now = System.currentTimeMillis();
                if (now - REQUESTED.getOrDefault(name, 0L) > 10000)
                { REQUESTED.put(name, now); new MessageQuestAsset.Request(name).sendToServer(); }
                return null;
            }
        }
        Entry entry = CACHE.computeIfAbsent(source, key -> {
            Entry value = new Entry();
            value.pending = CompletableFuture.supplyAsync(() -> { try { return decode(key); } catch (Exception ex) { FTBQuests.LOGGER.warn("Could not decode quest media " + key, ex); return new Frames(); } }, DECODER);
            return value;
        });
        if (entry.frames == null && entry.pending.isDone()) entry.frames = entry.pending.join();
        trim(source);
        return entry;
    }
    private static byte[] read(String source) throws IOException
    {
        InputStream stream;
        if (source.startsWith("media:")) stream = Files.newInputStream(ClientMediaAssets.cache().resolve(source.substring(6)));
        else if (source.startsWith("asset:")) stream = Files.newInputStream(Loader.instance().getConfigDir().toPath().resolve("ftbquests/.asset-cache").resolve(source.substring(6)));
        else if (source.startsWith("https://") || source.startsWith("http://"))
        {
            var connection = URI.create(source).toURL().openConnection(); connection.setConnectTimeout(10000); connection.setReadTimeout(10000); stream = connection.getInputStream();
        }
        else stream = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(source)).getInputStream();
        try (InputStream input = stream)
        {
            byte[] data = input.readNBytes(QuestAssets.MAX_BYTES + 1);
            if (data.length > QuestAssets.MAX_BYTES) throw new IOException("Quest media exceeds 8 MiB");
            return data;
        }
    }
    private static String attribute(Node node, String name, String fallback)
    { Node value = node.getAttributes().getNamedItem(name); return value == null ? fallback : value.getNodeValue(); }
    private static Node child(Node node, String name)
    { for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling()) if (c.getNodeName().equals(name)) return c; return null; }
    private static Frames decode(String source) throws IOException
    {
        Frames result = new Frames();
        byte[] bytes=read(source);
        var apng=com.feed_the_beast.ftbquests.util.ApngDecoder.decode(bytes);
        if(apng!=null) { result.images.addAll(apng.frames); result.delays.addAll(apng.delays); for(int delay:apng.delays) result.duration+=delay; return result; }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)))
        {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input);
                int count = reader.getNumImages(true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (reader.getFormatName().equalsIgnoreCase("gif") && reader.getStreamMetadata() != null)
                {
                    Node screen = child(reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                    if (screen != null) { width = Integer.parseInt(attribute(screen,"logicalScreenWidth","1")); height = Integer.parseInt(attribute(screen,"logicalScreenHeight","1")); }
                }
                if (count > 512 || width < 1 || height < 1 || (long)width * height * count > 33_554_432L) throw new IOException("Quest animation is too large");
                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int i = 0; i < count; i++)
                {
                    BufferedImage frame = reader.read(i);
                    int left = 0, top = 0, delay = 100;
                    String disposal = "none";
                    if (reader.getFormatName().equalsIgnoreCase("gif"))
                    {
                        Node metadata = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                        Node descriptor = child(metadata,"ImageDescriptor"), control = child(metadata,"GraphicControlExtension");
                        left = Integer.parseInt(attribute(descriptor,"imageLeftPosition","0")); top = Integer.parseInt(attribute(descriptor,"imageTopPosition","0"));
                        if (control != null) { delay = Math.max(20, Integer.parseInt(attribute(control,"delayTime","10"))*10); disposal = attribute(control,"disposalMethod","none"); }
                    }
                    BufferedImage previous = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    previous.setData(canvas.getData());
                    var graphics = canvas.createGraphics(); graphics.drawImage(frame,left,top,null); graphics.dispose();
                    BufferedImage composed = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB); composed.setData(canvas.getData());
                    result.images.add(composed); result.delays.add(delay); result.duration += delay;
                    if (disposal.equals("restoreToPrevious")) canvas = previous;
                    else if (disposal.equals("restoreToBackgroundColor"))
                    { graphics = canvas.createGraphics(); graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(left,top,frame.getWidth(),frame.getHeight()); graphics.dispose(); }
                }
            }
            finally { reader.dispose(); }
        }
        return result;
    }
}
