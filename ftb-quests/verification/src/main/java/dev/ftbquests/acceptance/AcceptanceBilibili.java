package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Real Bilibili guest stream, using the actual description controls, with per-process proxies disabled. */
@SideOnly(Side.CLIENT)
public final class AcceptanceBilibili
{
    private static final String SOURCE="https://www.bilibili.com/video/BV1B7411m7LV?p=1";
    private int step,ticks,wait;
    private long target;
    private VideoBackend video;
    private GuiBase gui;
    private Map<String,String> before;
    private final Map<String,Object> checks=new LinkedHashMap<>();
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceBilibili());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        try
        {
            if(ticks>2000)throw new AssertionError("Bilibili timeout at "+step+": "+(video==null?"":video.status()));
            if(step==0&&mc.currentScreen instanceof GuiMainMenu)
            {step=1;mc.launchIntegratedServer("bilibili-acceptance","Bilibili streaming",new WorldSettings(3,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1&&mc.player!=null&&ClientQuestFile.exists())
            {
                CanvasMedia.invalidate();before=AcceptanceStreaming.snapshot(ClientMediaAssets.cache().getParent());
                video=VideoBackend.get(SOURCE);
                gui=new GuiBase(){
                    @Override public boolean onInit(){setSize(320,248);return true;}
                    @Override public void addWidgets(){JsonObject json=new JsonObject();json.addProperty("type","video");json.addProperty("source",SOURCE);json.addProperty("width",288);json.addProperty("height",162);DescriptionComponents.add(this,new String[]{json.toString()});}
                    @Override public void alignWidgets(){for(Widget widget:widgets)widget.setPos(16,24);}
                    @Override public void drawBackground(Theme theme,int x,int y,int w,int h){Color4I.rgb(0x18232D).draw(x,y,w,h);theme.drawString("B 站直连在线播放验收",x+12,y+8,Color4I.WHITE,0);}
                };gui.openGui();wait=ticks;step=2;
            }
            else if(step==2&&video.status().isEmpty())
            {click(.2);wait=ticks;step=3;}
            else if(step==3&&video.hasFrame())
            {checks.put("bilibili_live_guest_native_frame",true);wait=ticks;step=4;}
            else if(step==4&&ticks-wait>20)
            {
                shot("bilibili-live-stream.png");click(.2);wait=ticks;step=5;
            }
            else if(step==5&&ticks-wait>20)
            {
                AcceptanceMod.require(!playing(),"Bilibili description pause control did not pause");checks.put("actual_description_pause_control",true);
                AcceptanceMod.require(video.durationMillis()>0&&video.isSeekable(),"Bilibili stream has no seekable native timeline");
                Widget widget=gui.widgets.get(0);target=Math.round(video.durationMillis()*.35);
                gui.updateGui(widget.getX()+4+(int)Math.round((widget.width-9)*.35),widget.getY()+widget.height-32+6,0);
                long started=System.nanoTime();gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);
                checks.put("bilibili_timeline_seek_submission_ms",(System.nanoTime()-started)/1_000_000D);wait=ticks;step=51;
            }
            else if(step==51&&ticks-wait>12&&Math.abs(video.positionMillis()-target)<2000)
            {
                AcceptanceMod.require(!playing(),"Bilibili timeline seek resumed paused playback");
                checks.put("bilibili_description_timeline_seeks_native_stream_and_preserves_pause",true);
                Widget widget=gui.widgets.get(0);
                for(String key:List.of("retry","web"))
                {
                    gui.updateGui(widget.getX()+widget.width-(key.equals("retry")?34:14),widget.getY()+widget.height-10,0);
                    List<String> tooltip=new ArrayList<>();widget.addMouseOverText(tooltip);
                    AcceptanceMod.require(tooltip.get(0).equals(net.minecraft.client.resources.I18n.format("ftbquests.video."+key)),"Bilibili icon tooltip missing: "+tooltip);
                }
                checks.put("bilibili_retry_and_web_icon_tooltips",true);
                shot("bilibili-timeline-paused.png");
                click(.6);video=VideoBackend.get(SOURCE);wait=ticks;step=6;
            }
            else if(step==6&&video.status().isEmpty())
            {click(.2);wait=ticks;step=7;}
            else if(step==7&&video.hasFrame())
            {checks.put("retry_resolves_and_decodes_fresh_live_stream",true);wait=ticks;step=8;}
            else if(step==8&&ticks-wait>20)
            {
                shot("bilibili-retry-stream.png");CanvasMedia.invalidate();
                AcceptanceMod.require(before.equals(AcceptanceStreaming.snapshot(ClientMediaAssets.cache().getParent())),"Bilibili playback wrote a video file to quest media/cache directories");
                checks.put("no_video_files_in_media_or_cache",true);
                Field source=VideoBackend.class.getDeclaredField("source");source.setAccessible(true);
                AcceptanceMod.require(SOURCE.equals(source.get(video)),"Live CDN URL replaced the original Bilibili source");checks.put("original_bilibili_source_preserved",true);
                Files.writeString(Path.of("bilibili-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                AcceptanceMod.report("PASS","Bilibili real guest stream decoded; description play/pause, seeking and retry; original URL retained; no video files in quest media/cache. Proxy conditions are recorded in runtime.json and proxy-trap.json.",null);step=100;mc.shutdown();
            }
            if(step>=2&&step!=8&&step!=100&&ticks-wait>600)throw new AssertionError("Bilibili stage "+step+" failed: "+video.status());
        }
        catch(Throwable error){int at=step;step=100;CanvasMedia.invalidate();AcceptanceMod.report("FAIL","Bilibili stage "+at,error);mc.shutdown();}
    }
    private void click(double fraction)
    {
        Widget widget=gui.widgets.get(0);gui.updateGui(widget.getX()+(fraction<.45?14:widget.width-(fraction<.75?34:14)),widget.getY()+widget.height-10,0);
        gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);
    }
    private boolean playing() { return video.isPlaying(); }
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
