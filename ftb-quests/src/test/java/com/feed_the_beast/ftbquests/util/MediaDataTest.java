package com.feed_the_beast.ftbquests.util;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaDataTest
{
    @Test void largeAssetsReassembleOutOfOrderWithoutAcceptingConflictsOrReplays()
    {
        byte[] asset = new byte[170_013]; new Random(13).nextBytes(asset);
        AssetTransfers transfers = new AssetTransfers();
        var key = new AssetTransfers.Key(UUID.randomUUID(), UUID.randomUUID());
        List<Integer> offsets = new ArrayList<>();
        for (int offset=0; offset<asset.length; offset+=AssetTransfers.CHUNK_SIZE) offsets.add(offset);
        Collections.reverse(offsets);
        byte[] result = null;
        for (int offset : offsets)
        {
            byte[] part = Arrays.copyOfRange(asset, offset, Math.min(asset.length, offset+AssetTransfers.CHUNK_SIZE));
            result = transfers.accept(key,"image",asset.length,offset,part);
        }
        assertArrayEquals(asset,result);
        assertNull(transfers.accept(key,"image",asset.length,0,Arrays.copyOf(asset,AssetTransfers.CHUNK_SIZE)));
        var other = new AssetTransfers.Key(key.owner(),UUID.randomUUID());
        byte[] first = Arrays.copyOf(asset,AssetTransfers.CHUNK_SIZE);
        assertNull(transfers.accept(other,"image",asset.length,0,first));
        first[0] ^= 1;
        assertThrows(IllegalArgumentException.class,()->transfers.accept(other,"image",asset.length,0,first));
        assertThrows(IllegalArgumentException.class,()->transfers.accept(other,"changed",asset.length,0,first));
        assertThrows(IllegalArgumentException.class,()->AssetTransfers.validate(QuestAssets.MAX_BYTES+1,0,AssetTransfers.CHUNK_SIZE));
        assertThrows(IllegalArgumentException.class,()->AssetTransfers.validate(asset.length,1,AssetTransfers.CHUNK_SIZE));
    }

    @Test void apngComposesPartialFramesWithAlphaAndBothDisposalModes() throws Exception
    {
        var previous = ApngDecoder.decode(animation(2));
        assertEquals(List.of(100,200,50), previous.delays);
        assertEquals(0xffff0000, previous.frames.get(0).getRGB(1,0));
        assertEquals(0xff0000ff, previous.frames.get(1).getRGB(1,0));
        assertEquals(0xffff0000, previous.frames.get(2).getRGB(1,0));
        int mixed = previous.frames.get(2).getRGB(0,0);
        assertEquals(255, mixed>>>24);
        assertTrue(((mixed>>>16)&255)>=126 && ((mixed>>>16)&255)<=128);
        assertTrue(((mixed>>>8)&255)>=127 && ((mixed>>>8)&255)<=129);
        var background = ApngDecoder.decode(animation(1));
        assertEquals(0, background.frames.get(2).getRGB(1,0));
        byte[] broken = animation(0); broken[40] ^= 1;
        assertThrows(IOException.class,()->ApngDecoder.decode(broken));
    }

    private static byte[] animation(int dispose) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
        ByteArrayOutputStream header = new ByteArrayOutputStream(); DataOutputStream h = new DataOutputStream(header);
        h.writeInt(2); h.writeInt(1); h.write(new byte[]{8,6,0,0,0}); chunk(out,"IHDR",header.toByteArray());
        chunk(out,"acTL",ints(3,0));
        control(out,0,2,0,1,10,0,0); chunk(out,"IDAT",pixels(new byte[]{(byte)255,0,0,(byte)255,(byte)255,0,0,(byte)255}));
        control(out,1,1,1,2,10,dispose,0);
        ByteArrayOutputStream second = new ByteArrayOutputStream(); second.write(ints(2)); second.write(pixels(new byte[]{0,0,(byte)255,(byte)255})); chunk(out,"fdAT",second.toByteArray());
        control(out,3,1,0,1,20,0,1);
        ByteArrayOutputStream third = new ByteArrayOutputStream(); third.write(ints(4)); third.write(pixels(new byte[]{0,(byte)255,0,(byte)128})); chunk(out,"fdAT",third.toByteArray());
        chunk(out,"IEND",new byte[0]); return bytes.toByteArray();
    }
    private static void control(DataOutputStream out,int sequence,int width,int x,int numerator,int denominator,int dispose,int blend) throws IOException
    {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(bytes);
        data.write(ints(sequence,width,1,x,0));data.writeShort(numerator);data.writeShort(denominator);data.writeByte(dispose);data.writeByte(blend);
        chunk(out,"fcTL",bytes.toByteArray());
    }
    private static byte[] ints(int... values) throws IOException
    { ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);for(int value:values)out.writeInt(value);return bytes.toByteArray(); }
    private static byte[] pixels(byte[] rgba) throws IOException
    { ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DeflaterOutputStream out=new DeflaterOutputStream(bytes)){out.write(0);out.write(rgba);}return bytes.toByteArray(); }
    private static void chunk(DataOutputStream out,String type,byte[] bytes) throws IOException
    { byte[] name=type.getBytes(StandardCharsets.US_ASCII);out.writeInt(bytes.length);out.write(name);out.write(bytes);CRC32 crc=new CRC32();crc.update(name);crc.update(bytes);out.writeInt((int)crc.getValue()); }
}
