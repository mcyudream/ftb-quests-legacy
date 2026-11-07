package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Width follows the description; decoded metadata supplies the missing height without changing saved JSON. */
final class DescriptionMediaWidget extends Widget
{
    private static final int TIMELINE_HEIGHT = 10, FOOTER_HEIGHT = 32;
    private enum Action { NONE, TOGGLE, REPLAY, RETRY, WEB, SETUP }
    private final String source, cover;
    private final boolean video;
    private final int requestedWidth, requestedHeight;
    private VideoBackend dragging;
    private VideoBackend pressedPlayer;
    private Action pressed = Action.NONE;

    DescriptionMediaWidget(Panel parent, JsonObject json, boolean video)
    {
        super(parent);
        this.video = video;
        source = json.has("source") ? json.get("source").getAsString() : "";
        cover = json.has("cover") ? json.get("cover").getAsString() : "";
        requestedWidth = dimension(json,"width"); requestedHeight = dimension(json,"height");
        resize();
    }
    private static int dimension(JsonObject json, String key)
    { return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : 0; }

    boolean resize()
    {
        if (parent.width <= 0) return false;
        int w = Math.min(parent.width, requestedWidth > 0 ? Math.max(8,requestedWidth) : parent.width);
        int h;
        if (requestedHeight > 0) h = Math.max(8,Math.min(512,requestedHeight));
        else
        {
            CanvasMedia.Dimensions size = video ? VideoBackend.dimensions(source) : CanvasMedia.dimensions(source);
            if (!size.known() && video && !cover.isEmpty()) size = CanvasMedia.dimensions(cover);
            // Until asynchronous metadata arrives, reserve a normal video-shaped placeholder.
            long calculated = size.known() ? Math.round(w * (double)size.height() / size.width()) : Math.round(w * 9D / 16D);
            h = (int)Math.max(1,Math.min(1_000_000L,calculated));
        }
        if (video) h += FOOTER_HEIGHT;
        if (width == w && height == h) return false;
        setSize(w,h); return true;
    }
    @Override public void draw(Theme theme, int x, int y, int w, int h)
    {
        int mediaHeight = Math.max(1,h - (video ? FOOTER_HEIGHT : 0));
        if (video)
        {
            VideoBackend player = VideoBackend.get(source);
            player.draw(x,y,w,mediaHeight,cover);
            if (dragging != player || !player.isSeekable()) dragging = null;
            drawControls(theme,player,x,y+mediaHeight,w);
        }
        else CanvasMedia.drawFit(source,x,y,w,mediaHeight,255);
    }

    private int trackInset() { return Math.min(4,width/4); }
    private int trackWidth() { return Math.max(1,width-2*trackInset()); }
    private long target(VideoBackend player)
    {
        double fraction = (getMouseX()-getX()-trackInset())/(double)Math.max(1,trackWidth()-1);
        return Math.round(Math.max(0,Math.min(1,fraction))*player.durationMillis());
    }
    private static String time(long milliseconds)
    {
        long seconds = Math.max(0,milliseconds)/1000;
        String tail = (seconds%60<10?"0":"")+seconds%60;
        if (seconds<3600) return seconds/60+":"+tail;
        long minutes = seconds/60%60;
        return seconds/3600+":"+(minutes<10?"0":"")+minutes+":"+tail;
    }
    private String timestamp(VideoBackend player)
    {
        return time(dragging==player?target(player):player.positionMillis())+" / "+
                (player.durationMillis()>0?time(player.durationMillis()):"--:--");
    }
    private int controlInset() { return Math.min(4,width/8); }
    private int buttonSize() { return Math.min(20,Math.max(0,(width-2*controlInset())/(VideoBackend.isOnline(source)?3:2))); }
    private Action primary(VideoBackend player) { return player.supportIssue()==VideoBackend.SupportIssue.NONE?Action.TOGGLE:Action.SETUP; }
    private int rightCount(VideoBackend player) { return VideoBackend.isOnline(source)?(primary(player)==Action.SETUP?1:2):(primary(player)==Action.SETUP?0:1); }
    private Action actionAt(VideoBackend player)
    {
        int localX=getMouseX()-getX(),localY=getMouseY()-getY();
        if(localY<height-FOOTER_HEIGHT)return primary(player);
        if(localY<height-FOOTER_HEIGHT+TIMELINE_HEIGHT||localY>=height-2)return Action.NONE;
        int inset=controlInset(),size=buttonSize(),right=width-inset-size;
        if(localX>=inset&&localX<inset+size)return primary(player);
        if(rightCount(player)>0&&localX>=right&&localX<right+size)return VideoBackend.isOnline(source)?Action.WEB:Action.REPLAY;
        if(rightCount(player)>1&&localX>=right-size&&localX<right)return Action.RETRY;
        return Action.NONE;
    }
    private static String actionKey(Action action,VideoBackend player)
    { return switch(action){case TOGGLE -> player.isPlaying()?"pause":"play";case REPLAY -> "replay";case RETRY -> "retry";case WEB -> "web";case SETUP -> "setup";default -> "";}; }
    private void button(VideoBackend player,Action action,VideoControlIcon icon,int x,int y,int size)
    {
        boolean hover=isMouseOver()&&getMouseX()>=x&&getMouseX()<x+size&&getMouseY()>=y&&getMouseY()<y+20;
        boolean down=hover&&pressed==action;
        if(hover||action==Action.TOGGLE)Color4I.rgb(down?0x315D70:hover?0x2A4352:0x20323D).draw(x,y,size,20);
        int glyph=Math.min(12,size-4);
        icon.draw(x+(size-glyph)/2,y+(20-glyph)/2,glyph,Color4I.rgb(hover?0x72DBF5:0xD7E5EF));
    }
    private void drawControls(Theme theme,VideoBackend player,int x,int y,int w)
    {
        Color4I.rgb(0x18232D).draw(x,y,w,FOOTER_HEIGHT);
        boolean enabled = player.isSeekable();
        long duration = player.durationMillis(), position = dragging==player?target(player):player.positionMillis();
        int trackX=x+trackInset(), trackW=trackWidth();
        boolean hover=isMouseOver()&&getMouseY()>=y&&getMouseY()<y+TIMELINE_HEIGHT;
        Color4I.rgb(enabled?0x526976:0x34434D).draw(trackX,y+4,trackW,hover?3:2);
        if (duration>0)
        {
            int filled = (int)Math.round(Math.max(0,Math.min(1,position/(double)duration))*(trackW-1));
            Color4I.rgb(enabled?0x52C7EA:0x617785).draw(trackX,y+4,filled+1,hover?3:2);
            if (enabled) Color4I.WHITE.draw(trackX+Math.max(0,Math.min(trackW-3,filled-1)),y+(hover?2:3),Math.min(3,trackW),hover?7:5);
        }
        int inset=controlInset(),size=buttonSize(),right=w-inset-size,controlsY=y+TIMELINE_HEIGHT;
        Action first=primary(player);
        button(player,first,first==Action.SETUP?VideoControlIcon.SETUP:player.isPlaying()?VideoControlIcon.PAUSE:VideoControlIcon.PLAY,x+inset,controlsY,size);
        if(rightCount(player)>0)button(player,VideoBackend.isOnline(source)?Action.WEB:Action.REPLAY,VideoBackend.isOnline(source)?VideoControlIcon.WEB:VideoControlIcon.REPLAY,x+right,controlsY,size);
        if(rightCount(player)>1)button(player,Action.RETRY,VideoControlIcon.REPLAY,x+right-size,controlsY,size);
        int textLeft=x+inset+size+4,textWidth=w-2*inset-size*(1+rightCount(player))-8;
        if(textWidth>0)
        {
            var font=Minecraft.getMinecraft().fontRenderer;String text=timestamp(player);
            if(font.getStringWidth(text)>textWidth)text=time(position);
            text=font.trimStringToWidth(text,textWidth);
            theme.drawString(text,textLeft+(textWidth-font.getStringWidth(text))/2,controlsY+6,Color4I.rgb(0xB8CAD6),0);
        }
    }
    @Override public void addMouseOverText(List<String> list)
    {
        if (!video) return;
        VideoBackend player=VideoBackend.get(source);
        int localY=getMouseY()-getY();
        if(localY>=height-FOOTER_HEIGHT&&localY<height-FOOTER_HEIGHT+TIMELINE_HEIGHT)
        {list.add(timestamp(player));list.add(VideoBackend.tr(player.isSeekable()?"seek_hint":"seek_unavailable"));}
        else
        {
            Action action=actionAt(player);
            list.add(action==Action.NONE?timestamp(player):VideoBackend.tr(actionKey(action,player)));
        }
    }
    @Override public boolean mousePressed(MouseButton button)
    {
        if (!video || !isMouseOver() || !button.isLeft()) return false;
        pressed=Action.NONE;pressedPlayer=null;
        int localY=getMouseY()-getY();
        int mediaHeight=height-FOOTER_HEIGHT;
        // Consume the whole timeline row, including disabled/unknown-length media,
        // so a seek cannot accidentally toggle playback or retry a Bilibili source.
        if (localY>=mediaHeight && localY<mediaHeight+TIMELINE_HEIGHT)
        {
            VideoBackend player=VideoBackend.get(source);
            if (player.isSeekable()) dragging=player;
            return true;
        }
        pressedPlayer=VideoBackend.get(source);pressed=actionAt(pressedPlayer);
        return true;
    }
    @Override public void mouseReleased(MouseButton button)
    {
        if (!button.isLeft()) return;
        if(dragging!=null)
        {
            VideoBackend player=dragging;dragging=null;
            // Releases outside the viewport still commit one clamped native seek.
            if(player.isSeekable())player.seekTo(target(player));
        }
        else if(pressedPlayer!=null)
        {
            VideoBackend player=pressedPlayer;Action action=pressed;pressedPlayer=null;pressed=Action.NONE;
            if(!isMouseOver()||action==Action.NONE||actionAt(player)!=action)return;
            GuiHelper.playClickSound();
            switch(action){
                case TOGGLE -> player.toggle();
                case REPLAY -> player.replay();
                case RETRY -> VideoBackend.retry(source);
                case SETUP -> player.openSupport();
                case WEB -> {String url=VideoBackend.webPage(source);if(!url.isEmpty())handleClick(url);}
            }
        }
    }
    @Override public boolean mouseScrolled(int scroll) { return dragging!=null; }
    @Override public void onClosed() { dragging=null;pressed=Action.NONE;pressedPlayer=null; }
}
