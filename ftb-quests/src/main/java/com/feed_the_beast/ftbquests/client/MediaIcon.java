package com.feed_the_beast.ftbquests.client;
import com.feed_the_beast.ftblib.lib.icon.Icon;
public final class MediaIcon extends Icon
{
    private final String source;
    public MediaIcon(String source){this.source=source;}
    @Override public void draw(int x,int y,int width,int height){CanvasMedia.draw(source,x,y,width,height,255);}
}
