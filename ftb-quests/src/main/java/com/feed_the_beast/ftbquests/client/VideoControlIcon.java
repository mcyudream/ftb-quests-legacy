package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.icon.Color4I;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Small vector glyphs, independent of the selected font and resource pack. */
enum VideoControlIcon
{
    PLAY, PAUSE, REPLAY, WEB, SETUP;

    void draw(int x,int y,int size,Color4I color)
    {
        if(size<=0)return;
        boolean culling=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if(culling)GlStateManager.disableCull();
        GlStateManager.disableTexture2D();
        Tessellator tessellator=Tessellator.getInstance();BufferBuilder buffer=tessellator.getBuffer();
        buffer.begin(GL11.GL_TRIANGLES,DefaultVertexFormats.POSITION_COLOR);
        switch(this)
        {
            case PLAY -> triangle(buffer,x,y,size,color,3,1.5,3,10.5,10,6);
            case PAUSE -> {rect(buffer,x,y,size,color,2.5,2,2.2,8);rect(buffer,x,y,size,color,7.3,2,2.2,8);}
            case REPLAY -> {
                arc(buffer,x,y,size,color,6.5,6.5,3.8,1.3,-120,180);
                triangle(buffer,x,y,size,color,1.3,2,5.3,2,1.3,6);
            }
            case WEB -> {
                rect(buffer,x,y,size,color,1,4,1.3,7);rect(buffer,x,y,size,color,1,9.7,7,1.3);
                rect(buffer,x,y,size,color,1,4,3.5,1.3);rect(buffer,x,y,size,color,6.7,7.5,1.3,3.5);
                line(buffer,x,y,size,color,5,7,10.3,1.7,1.4);
                rect(buffer,x,y,size,color,6.7,1,4.3,1.3);rect(buffer,x,y,size,color,9.7,1,1.3,4.3);
            }
            case SETUP -> {
                arc(buffer,x,y,size,color,6,6,3.6,1.6,0,360);
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4;
                    line(buffer,x,y,size,color,6+3*Math.cos(a),6+3*Math.sin(a),6+5*Math.cos(a),6+5*Math.sin(a),1.8);
                }
            }
        }
        tessellator.draw();GlStateManager.enableTexture2D();
        if(culling)GlStateManager.enableCull();
    }
    private static void rect(BufferBuilder b,int x,int y,int s,Color4I c,double a,double d,double w,double h)
    {triangle(b,x,y,s,c,a,d,a,d+h,a+w,d+h);triangle(b,x,y,s,c,a,d,a+w,d+h,a+w,d);}
    private static void line(BufferBuilder b,int x,int y,int s,Color4I c,double ax,double ay,double bx,double by,double thickness)
    {
        double length=Math.hypot(bx-ax,by-ay),dx=(by-ay)/length*thickness/2,dy=(ax-bx)/length*thickness/2;
        triangle(b,x,y,s,c,ax-dx,ay-dy,bx-dx,by-dy,bx+dx,by+dy);
        triangle(b,x,y,s,c,ax-dx,ay-dy,bx+dx,by+dy,ax+dx,ay+dy);
    }
    private static void arc(BufferBuilder b,int x,int y,int s,Color4I c,double cx,double cy,double radius,double thickness,int start,int end)
    {
        for(int angle=start;angle<end;angle+=10){
            double a=Math.toRadians(angle),d=Math.toRadians(Math.min(end,angle+10)),inner=radius-thickness;
            triangle(b,x,y,s,c,cx+radius*Math.cos(a),cy+radius*Math.sin(a),cx+radius*Math.cos(d),cy+radius*Math.sin(d),cx+inner*Math.cos(d),cy+inner*Math.sin(d));
            triangle(b,x,y,s,c,cx+radius*Math.cos(a),cy+radius*Math.sin(a),cx+inner*Math.cos(d),cy+inner*Math.sin(d),cx+inner*Math.cos(a),cy+inner*Math.sin(a));
        }
    }
    private static void triangle(BufferBuilder b,int x,int y,int s,Color4I c,double ax,double ay,double bx,double by,double cx,double cy)
    {vertex(b,x+ax*s/12,y+ay*s/12,c);vertex(b,x+bx*s/12,y+by*s/12,c);vertex(b,x+cx*s/12,y+cy*s/12,c);}
    private static void vertex(BufferBuilder b,double x,double y,Color4I c)
    {b.pos(x,y,0).color(c.redi(),c.greeni(),c.bluei(),c.alphai()).endVertex();}
}
