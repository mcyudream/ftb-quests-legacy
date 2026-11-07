package com.feed_the_beast.ftbquests.util;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** APNG frame composition using the JRE PNG reader for each independently encoded frame. */
public final class ApngDecoder
{
    private static final byte[] SIGNATURE = {(byte)137,80,78,71,13,10,26,10};
    public static final class Animation
    {
        public final List<BufferedImage> frames = new ArrayList<>();
        public final List<Integer> delays = new ArrayList<>();
    }
    private static final class Frame
    {
        int width,height,x,y,delay,dispose,blend;
        final List<byte[]> compressed = new ArrayList<>();
    }
    private static void chunk(DataOutputStream out, String type, byte[] data) throws IOException
    {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length); out.write(name); out.write(data);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(data); out.writeInt((int)crc.getValue());
    }
    private static BufferedImage copy(BufferedImage image)
    { BufferedImage result = new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB); result.setData(image.getData()); return result; }
    public static Animation decode(byte[] data) throws IOException
    {
        List<Frame> frames = new ArrayList<>(); List<Map.Entry<String,byte[]>> shared = new ArrayList<>();
        byte[] header = null; int width=0,height=0,declared=0; Frame current=null;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data)))
        {
            if(!Arrays.equals(input.readNBytes(8),SIGNATURE)) return null;
            while(input.available()>0)
            {
                int length=input.readInt(); if(length<0 || length>input.available()-8) throw new IOException("Invalid PNG chunk length");
                byte[] name=input.readNBytes(4), body=input.readNBytes(length); int expected=input.readInt();
                CRC32 crc=new CRC32(); crc.update(name); crc.update(body); if((int)crc.getValue()!=expected) throw new IOException("PNG checksum mismatch");
                String type=new String(name,StandardCharsets.US_ASCII);
                DataInputStream content=new DataInputStream(new ByteArrayInputStream(body));
                switch(type)
                {
                    case "IHDR": header=body; width=content.readInt(); height=content.readInt(); break;
                    case "acTL": declared=content.readInt(); if(declared<1 || declared>512) throw new IOException("Too many APNG frames"); break;
                    case "fcTL":
                        content.readInt(); current=new Frame(); current.width=content.readInt(); current.height=content.readInt(); current.x=content.readInt(); current.y=content.readInt();
                        int numerator=content.readUnsignedShort(),denominator=content.readUnsignedShort();
                        current.delay=Math.max(20,(int)(1000L*numerator/(denominator==0?100:denominator)));
                        current.dispose=content.readUnsignedByte(); current.blend=content.readUnsignedByte();
                        if(current.width<1 || current.height<1 || current.x<0 || current.y<0 || (long)current.x+current.width>width || (long)current.y+current.height>height || frames.size()>=512 || current.dispose>2 || current.blend>1) throw new IOException("Invalid APNG frame");
                        frames.add(current); break;
                    case "IDAT": if(current!=null) current.compressed.add(body); break;
                    case "fdAT": if(current==null || length<4) throw new IOException("Missing APNG frame control"); current.compressed.add(Arrays.copyOfRange(body,4,body.length)); break;
                    case "PLTE": case "tRNS": case "gAMA": case "sRGB": shared.add(new AbstractMap.SimpleImmutableEntry<>(type,body)); break;
                    case "IEND": break;
                    default: break;
                }
            }
        }
        if(declared==0) return null;
        if(header==null || frames.size()!=declared || width<1 || height<1 || (long)width*height*declared>33_554_432L) throw new IOException("APNG dimensions exceed the animation budget");
        Animation result=new Animation(); BufferedImage canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(Frame frame:frames)
        {
            byte[] frameHeader=header.clone(); java.nio.ByteBuffer.wrap(frameHeader).putInt(frame.width).putInt(frame.height);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(buffer);
            out.write(SIGNATURE); chunk(out,"IHDR",frameHeader); for(var entry:shared) chunk(out,entry.getKey(),entry.getValue());
            for(byte[] compressed:frame.compressed) chunk(out,"IDAT",compressed); chunk(out,"IEND",new byte[0]);
            BufferedImage image=ImageIO.read(new ByteArrayInputStream(buffer.toByteArray())); if(image==null) throw new IOException("Invalid APNG frame image");
            BufferedImage previous=frame.dispose==2?copy(canvas):null;
            var graphics=canvas.createGraphics(); graphics.setComposite(frame.blend==0?AlphaComposite.Src:AlphaComposite.SrcOver); graphics.drawImage(image,frame.x,frame.y,null); graphics.dispose();
            result.frames.add(copy(canvas)); result.delays.add(frame.delay);
            if(frame.dispose==2) canvas=previous;
            else if(frame.dispose==1) { graphics=canvas.createGraphics(); graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(frame.x,frame.y,frame.width,frame.height); graphics.dispose(); }
        }
        return result;
    }
}
