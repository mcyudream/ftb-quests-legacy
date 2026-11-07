package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import java.util.*;

/** Real description mouse events and native timestamps, for both imported and HTTP video. */
final class AcceptanceTimeline
{
    private final String prefix;
    private final Map<String,Object> checks;
    private final GuiBase gui, previous;
    private final Widget widget;
    private final VideoBackend video;
    private int ticks, stage, wait;
    private long expected, paused, duration;

    AcceptanceTimeline(String source,String prefix,Map<String,Object> checks)
    {
        this.prefix=prefix;this.checks=checks;
        previous=((GuiWrapper)Minecraft.getMinecraft().currentScreen).getGui();
        video=VideoBackend.get(source);
        gui=new GuiBase(){
            @Override public boolean onInit(){setSize(320,248);return true;}
            @Override public void addWidgets(){JsonObject json=new JsonObject();json.addProperty("type","video");json.addProperty("source",source);json.addProperty("width",288);json.addProperty("height",162);DescriptionComponents.add(this,new String[]{json.toString()});}
            @Override public void alignWidgets(){widgets.get(0).setPos(16,24);}
            @Override public void drawBackground(Theme theme,int x,int y,int w,int h){Color4I.rgb(0x18232D).draw(x,y,w,h);theme.drawString("视频进度条 · "+prefix,x+8,y+8,Color4I.WHITE,0);}
        };
        gui.openGui();widget=gui.widgets.get(0);
    }
    boolean tick()
    {
        ticks++;
        if(ticks>600)throw new AssertionError(prefix+" timeline timeout at "+stage+": "+video.status()+" / "+video.positionMillis()+" / "+video.durationMillis());
        if(stage==0&&video.status().isEmpty())
        {
            if(!video.isPlaying())control(.2);
            wait=ticks;stage=1;
        }
        else if(stage==1&&video.hasFrame()&&video.isSeekable()&&video.positionMillis()>300)
        {
            duration=video.durationMillis();AcceptanceMod.require(duration>=19000&&duration<=21000,"Native AVI duration is incorrect: "+duration);
            expected=duration*3/5;long started=System.nanoTime();seek(.6);
            checks.put(prefix+"_timeline_click_submission_ms",(System.nanoTime()-started)/1_000_000D);wait=ticks;stage=2;
        }
        else if(stage==2&&ticks-wait>4&&near(expected,1800))
        {
            AcceptanceMod.require(video.isPlaying(),"Click seek stopped a playing video");
            checks.put(prefix+"_timeline_click_changes_native_time_while_playing",true);
            control(.2);wait=ticks;stage=3;
        }
        else if(stage==3&&ticks-wait>8)
        {
            AcceptanceMod.require(!video.isPlaying(),"Timeline pause button did not pause");
            gui.updateGui(widget.getX()+14,widget.getY()+widget.height-10,0);
            List<String> labels=new ArrayList<>();widget.addMouseOverText(labels);
            AcceptanceMod.require(labels.get(0).equals(net.minecraft.client.resources.I18n.format("ftbquests.video.play")),"Play icon has no localized tooltip: "+labels);
            gui.mousePressed(MouseButton.LEFT);gui.updateGui(widget.getX()-50,widget.getY()-50,0);gui.mouseReleased(MouseButton.LEFT);
            AcceptanceMod.require(!video.isPlaying(),"Icon release outside the control activated playback");
            checks.put(prefix+"_icon_tooltip_and_outside_release_cancel",true);
            paused=video.positionMillis();move(.25,6);gui.mousePressed(MouseButton.LEFT);wait=ticks;stage=4;
        }
        else if(stage==4&&ticks-wait>8)
        {
            AcceptanceMod.require(Math.abs(video.positionMillis()-paused)<500,"Dragging submitted a native seek before release");
            move(.82,6);List<String> tooltip=new ArrayList<>();widget.addMouseOverText(tooltip);
            AcceptanceMod.require(tooltip.get(0).startsWith("0:16 / 0:20"),"Drag does not preview target time: "+tooltip);
            AcceptanceMod.require(tooltip.stream().noneMatch(s->s.contains("ftbquests.video.")),"Timeline localization key leaked");
            move(.82,-100);gui.mouseReleased(MouseButton.LEFT);expected=Math.round(duration*.82);wait=ticks;stage=5;
        }
        else if(stage==5&&ticks-wait>8&&near(expected,600))
        {
            AcceptanceMod.require(!video.isPlaying(),"Dragging resumed a paused video");
            checks.put(prefix+"_drag_previews_then_seeks_once_and_preserves_pause",true);
            shot(prefix+"-timeline-paused.png");
            move(.5,6);gui.mousePressed(MouseButton.LEFT);gui.updateGui(widget.getX()-100,widget.getY()-100,0);gui.mouseReleased(MouseButton.LEFT);
            wait=ticks;stage=6;
        }
        else if(stage==6&&ticks-wait>8&&video.positionMillis()<600)
        {
            AcceptanceMod.require(!video.isPlaying(),"Left boundary seek changed pause state");
            move(.5,6);gui.mousePressed(MouseButton.LEFT);gui.updateGui(widget.getX()+widget.width+100,widget.getY()-100,0);gui.mouseReleased(MouseButton.LEFT);
            wait=ticks;stage=7;
        }
        else if(stage==7&&ticks-wait>8&&video.positionMillis()>=duration-700)
        {
            AcceptanceMod.require(video.positionMillis()<=duration&&!video.isPlaying(),"Right boundary seek is outside duration or resumed playback");
            checks.put(prefix+"_drag_release_outside_panel_clamps_both_edges",true);
            paused=video.positionMillis();move(.2,6);gui.mousePressed(MouseButton.LEFT);widget.onClosed();move(.4,6);gui.mouseReleased(MouseButton.LEFT);
            wait=ticks;stage=8;
        }
        else if(stage==8&&ticks-wait>8)
        {
            AcceptanceMod.require(Math.abs(paused-video.positionMillis())<500,"Closing a view committed its unfinished drag");
            checks.put(prefix+"_closed_view_cancels_unfinished_drag",true);
            control(.9);wait=ticks;stage=9;
        }
        else if(stage==9&&ticks-wait>4&&video.isPlaying()&&video.positionMillis()<2500)
        {
            checks.put(prefix+"_replay_button_still_works",true);video.toggle();
            gui.setWidth(112);DescriptionComponents.resizeMedia(gui);widget.setPos(0,24);
            wait=ticks;stage=10;
        }
        else if(stage==10&&ticks-wait>8)
        {
            AcceptanceMod.require(widget.width==112&&org.lwjgl.opengl.GL11.glGetError()==0,"Narrow timeline layout failed");
            shot(prefix+"-timeline-narrow.png");checks.put(prefix+"_narrow_controls_render_without_gl_error",true);
            previous.openGui();return true;
        }
        return false;
    }
    private boolean near(long position,long tolerance){return Math.abs(video.positionMillis()-position)<tolerance;}
    private void move(double fraction,int localY){gui.updateGui(widget.getX()+4+(int)Math.round((widget.width-9)*fraction),widget.getY()+widget.height-32+localY,0);}
    private void seek(double fraction){move(fraction,6);gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);}
    private void control(double fraction){gui.updateGui(widget.getX()+(fraction<.5?14:widget.width-14),widget.getY()+widget.height-10,0);gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);}
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}

    static void unavailable(String source,Map<String,Object> checks)
    {
        AcceptanceTimeline timeline=new AcceptanceTimeline(source,"missing-api",checks);
        AcceptanceMod.require(!timeline.video.isSeekable()&&timeline.video.durationMillis()==0,"Missing API has an enabled timeline");
        timeline.seek(.6);
        AcceptanceMod.require(((GuiWrapper)Minecraft.getMinecraft().currentScreen).getGui()==timeline.gui,"Disabled timeline opened setup or toggled another control");
        List<String> tooltip=new ArrayList<>();timeline.move(.6,6);timeline.widget.addMouseOverText(tooltip);
        AcceptanceMod.require(tooltip.get(0).equals("0:00 / --:--"),"Unknown duration is not displayed safely");
        checks.put("disabled_unknown_duration_timeline_does_not_activate_other_controls",true);timeline.previous.openGui();
    }
}
