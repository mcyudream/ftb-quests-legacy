package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.util.BilibiliVideo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.*;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import java.nio.file.*;
import java.util.*;

/** Quest URI adapter. Decoder and runtime installation belong to the optional Media API mod. */
public final class VideoBackend
{
    public enum SupportIssue { NONE, MOD, LIBRARY, INVALID_LIBRARY, VLC }
    interface PlayerBridge {
        int texture(); int width(); int height(); String status(); SupportIssue issue(); boolean playing();
        long positionMillis(); long durationMillis(); boolean seekable(); void seekTo(long milliseconds);
        void toggle(); void replay(); void support(); void close();
    }
    private static final Map<String,VideoBackend> PLAYERS = new HashMap<>();
    private static final Map<String,CanvasMedia.Dimensions> SIZES = new LinkedHashMap<>(32,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,CanvasMedia.Dimensions> entry) { return size() > 1024; }
    };
    private final String source;
    private PlayerBridge player;
    private long lastDraw = System.currentTimeMillis();
    private VideoBackend(String source)
    {
        this.source = source;
        if (net.minecraftforge.fml.common.Loader.isModLoaded("media_api"))
            try { player = new MediaApiBridge(source); }
            catch (LinkageError ex) { FTBQuests.LOGGER.warn("Could not initialize Media API integration", ex); }
    }
    static String tr(String key, Object... args) { return net.minecraft.client.resources.I18n.format("ftbquests.video."+key,args); }
    public SupportIssue supportIssue() { return player == null ? SupportIssue.MOD : player.issue(); }
    public void openSupport()
    {
        if (player != null) { player.support(); return; }
        new GuiBase() {
            @Override public boolean onInit() { setSize(Math.min(390,getScreen().getScaledWidth()-20),160); return true; }
            @Override public void addWidgets() {
                add(new TextField(this).setMaxWidth(width-24).setText(tr("mod_help")));
                add(new SimpleTextButton(this,tr("back"),Icon.EMPTY) { @Override public void onClicked(MouseButton button) { if (button.isLeft()) closeGui(true); } });
            }
            @Override public void alignWidgets() { widgets.get(0).setPos(12,34); widgets.get(1).setPosAndSize(12,height-30,width-24,20); }
            @Override public void drawBackground(Theme theme,int x,int y,int w,int h) { Color4I.rgb(0x18232D).draw(x,y,w,h); theme.drawString(tr("support_title"),x+12,y+12,Color4I.WHITE,0); }
        }.openGui();
    }
    public String controls() {
        if (supportIssue() != SupportIssue.NONE) return tr(isOnline(source)?"support_online_controls":"support_controls");
        return tr(isOnline(source)?"online_controls":"local_controls");
    }
    public static boolean isBilibili(String source) { return BilibiliVideo.matches(source); }
    public static boolean isOnline(String source) { return isBilibili(source)||com.feed_the_beast.ftbquests.util.DouyinLink.matches(source); }
    public static void retry(String source) { VideoBackend old=PLAYERS.remove(source);if(old!=null)old.release(); }
    public static String webPage(String source) { if(com.feed_the_beast.ftbquests.util.DouyinLink.matches(source))return com.feed_the_beast.ftbquests.util.DouyinLink.page(source);try {return BilibiliVideo.parse(source).embedUrl();}catch(java.io.IOException ex){return BilibiliVideo.matches(source)&&source.startsWith("https://")?source:"";} }

    static String resolve(String source) throws Exception
    {
        if(source.startsWith("http://") || source.startsWith("https://"))return source;
        if(source.startsWith("media:")||source.startsWith("local:"))return com.feed_the_beast.ftbquests.util.LocalQuestVideo.resolve(net.minecraftforge.fml.common.Loader.instance().getConfigDir().toPath(),source).toUri().toString();
        Path root=net.minecraftforge.fml.common.Loader.instance().getConfigDir().toPath().resolve("ftbquests/.video-cache");Files.createDirectories(root);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String extension=source.substring(source.lastIndexOf('.')+1);if(!extension.matches("[a-zA-Z0-9]{1,8}"))extension="mp4";Path file=root.resolve(hash+"."+extension);
        if(!Files.isRegularFile(file))try(var resource=Minecraft.getMinecraft().getResourceManager().getResource(new net.minecraft.util.ResourceLocation(source)))
        {
            Path temporary=Files.createTempFile(root,"video-",".tmp");
            try
            {
                try(var input=resource.getInputStream();var out=Files.newOutputStream(temporary))
                {byte[] buffer=new byte[65536];long size=0;int read;while((read=input.read(buffer))!=-1){size+=read;if(size>268435456)throw new java.io.IOException("Quest video exceeds 256 MiB");out.write(buffer,0,read);}}
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
            finally{Files.deleteIfExists(temporary);}
        }
        return file.toAbsolutePath().toUri().toString();
    }
    public static VideoBackend get(String source) { return PLAYERS.computeIfAbsent(source,VideoBackend::new); }
    public void draw(int x,int y,int width,int height,String cover)
    {
        lastDraw=System.currentTimeMillis();
        int texture=player==null?-1:player.texture();
        if(texture>0){GlStateManager.bindTexture(texture);GuiHelper.drawTexturedRect(x,y,width,height,Color4I.WHITE,0,0,1,1);return;}
        if(!cover.isEmpty())CanvasMedia.draw(cover,x,y,width,height,255);
        String label=status();
        if(!label.isEmpty())Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(Minecraft.getMinecraft().fontRenderer.trimStringToWidth(label,Math.max(8,width-8)),x+4,y+4,0xFFFFFF);
    }
    public void toggle(){if(supportIssue()!=SupportIssue.NONE){openSupport();return;}player.toggle();}
    public void replay(){if(supportIssue()!=SupportIssue.NONE){openSupport();return;}player.replay();}
    public boolean hasFrame(){return player!=null&&player.texture()>0;}
    public boolean isPlaying(){return player!=null&&player.playing();}
    public long positionMillis(){return player==null?0:player.positionMillis();}
    public long durationMillis(){return player==null?0:player.durationMillis();}
    public boolean isSeekable(){return player!=null&&player.seekable();}
    public void seekTo(long milliseconds){if(player!=null&&player.seekable())player.seekTo(milliseconds);}
    public CanvasMedia.Dimensions dimensions() {
        CanvasMedia.Dimensions size = player == null ? new CanvasMedia.Dimensions(0,0) : new CanvasMedia.Dimensions(player.width(),player.height());
        if (size.known()) SIZES.put(source,size);
        return size.known() ? size : SIZES.getOrDefault(source,size);
    }
    public static CanvasMedia.Dimensions dimensions(String source) { VideoBackend video = PLAYERS.get(source); return video == null ? SIZES.getOrDefault(source,new CanvasMedia.Dimensions(0,0)) : video.dimensions(); }
    public String status(){return player==null?tr("missing_mod"):player.status();}
    private void release(){if(player!=null){player.close();player=null;}}
    public static void closeAll(){for(VideoBackend video:PLAYERS.values())video.release();PLAYERS.clear();SIZES.clear();}
    public static void expire(){var iterator=PLAYERS.values().iterator();while(iterator.hasNext()){var video=iterator.next();if(System.currentTimeMillis()-video.lastDraw>1000){video.release();iterator.remove();}}}
}
