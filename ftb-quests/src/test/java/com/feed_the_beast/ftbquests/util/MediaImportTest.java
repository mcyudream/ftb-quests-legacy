package com.feed_the_beast.ftbquests.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class MediaImportTest
{
    @TempDir Path root;
    private Path image(String name, String format) throws Exception
    {
        Path path = root.resolve(name); BufferedImage image = new BufferedImage(13,7,BufferedImage.TYPE_INT_RGB);
        image.setRGB(5,3,0x12abef); assertTrue(ImageIO.write(image,format,path.toFile())); return path;
    }
    private byte[] video(int size)
    { byte[] bytes = new byte[size]; new Random(4).nextBytes(bytes); System.arraycopy("RIFF0000AVI ".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,bytes,0,12); return bytes; }
    private String name(Path file, String extension) throws Exception { return QuestMedia.hash(file)+"."+extension; }
    private long parts(Path directory) throws Exception { if (!Files.isDirectory(directory.resolve(".incoming"))) return 0; try(var files = Files.list(directory.resolve(".incoming"))) { return files.count(); } }

    @Test void choosingImagesCopiesOriginalBytesConvertsJpegAndDeduplicates() throws Exception
    {
        Path png=image("原图.PNG","png"),jpg=image("照片.jpeg","jpeg"),cache=root.resolve("cache"); byte[] original=Files.readAllBytes(png),originalJpeg=Files.readAllBytes(jpg);
        Path imported=QuestMedia.prepare(png,false,cache,()->false);
        assertArrayEquals(original,Files.readAllBytes(imported)); assertArrayEquals(original,Files.readAllBytes(png));
        assertEquals(imported,QuestMedia.prepare(png,false,cache,()->false));
        Path converted=QuestMedia.prepare(jpg,false,cache,()->false); assertTrue(converted.toString().endsWith(".png"));
        assertEquals(13,ImageIO.read(converted.toFile()).getWidth()); assertArrayEquals(originalJpeg,Files.readAllBytes(jpg));
        QuestMedia.validate(imported,imported.getFileName().toString());
        try(var files=Files.list(cache)) { assertEquals(2,files.count()); }
    }
    @Test void animationAndVideoStayByteExactAndCancelledCopyLeavesNoTemporaryFile() throws Exception
    {
        Path gif=image("animated.gif","gif"),cache=root.resolve("cache");
        assertArrayEquals(Files.readAllBytes(gif),Files.readAllBytes(QuestMedia.prepare(gif,false,cache,()->false)));
        Path avi=root.resolve("movie.AVI"); byte[] bytes=video(300_017);Files.write(avi,bytes);
        assertArrayEquals(bytes,Files.readAllBytes(QuestMedia.prepare(avi,true,cache,()->false)));
        assertThrows(IOException.class,()->QuestMedia.prepare(avi,true,cache,()->true)); assertArrayEquals(bytes,Files.readAllBytes(avi));
        try(var files=Files.list(cache)) { assertFalse(files.anyMatch(file->file.toString().endsWith(".part"))); }
    }
    @Test void diskTransferSpansWindowsAndConfirmsOnlyAfterHashAndCommit() throws Exception
    {
        byte[] bytes=video(QuestMedia.CHUNK*9+71);Path source=root.resolve("source.avi");Files.write(source,bytes);String name=name(source,"avi");Path server=root.resolve("server");
        try(var transfers=new DiskMediaTransfers())
        {
            var key=new DiskMediaTransfers.Key(UUID.randomUUID(),UUID.randomUUID());assertEquals(0,transfers.begin(key,server,name,bytes.length));
            for(int offset=0;offset<bytes.length;offset+=QuestMedia.CHUNK)
            {
                assertFalse(Files.exists(server.resolve(name)));
                int end=Math.min(bytes.length,offset+QuestMedia.CHUNK);
                assertEquals(end,transfers.accept(key,name,bytes.length,offset,Arrays.copyOfRange(bytes,offset,end)));
            }
            assertArrayEquals(bytes,Files.readAllBytes(server.resolve(name)));assertEquals(0,parts(server));
            assertEquals(bytes.length,transfers.begin(new DiskMediaTransfers.Key(key.owner(),UUID.randomUUID()),server,name,bytes.length));
        }
    }
    @Test void corruptOrMismatchedTransfersNeverReplaceACompleteResource() throws Exception
    {
        Path source=root.resolve("s.avi");byte[] bytes=video(50_000);Files.write(source,bytes);String name=name(source,"avi");Path server=root.resolve("server");Files.createDirectories(server);
        try(var transfers=new DiskMediaTransfers())
        {
            var key=new DiskMediaTransfers.Key(UUID.randomUUID(),UUID.randomUUID());transfers.begin(key,server,name,bytes.length);
            byte[] bad=Arrays.copyOfRange(bytes,0,QuestMedia.CHUNK);bad[15]^=1;transfers.accept(key,name,bytes.length,0,bad);
            transfers.accept(key,name,bytes.length,QuestMedia.CHUNK,Arrays.copyOfRange(bytes,QuestMedia.CHUNK,QuestMedia.CHUNK*2));
            assertThrows(IOException.class,()->transfers.accept(key,name,bytes.length,QuestMedia.CHUNK*2,Arrays.copyOfRange(bytes,QuestMedia.CHUNK*2,bytes.length)));
            assertFalse(Files.exists(server.resolve(name)));assertEquals(0,parts(server));
            transfers.begin(key,server,name,bytes.length);
            assertThrows(IOException.class,()->transfers.accept(key,name,bytes.length,QuestMedia.CHUNK,Arrays.copyOfRange(bytes,QuestMedia.CHUNK,QuestMedia.CHUNK*2)));
            assertEquals(0,parts(server));
            Files.write(server.resolve(name),bytes);assertEquals(bytes.length,transfers.begin(key,server,name,bytes.length));assertArrayEquals(bytes,Files.readAllBytes(server.resolve(name)));
        }
    }
    @Test void transfersExpireCancelAndEnforceOwnerQuotaAndSizeLimits() throws Exception
    {
        AtomicLong clock=new AtomicLong(100);Path source=root.resolve("s.avi");Files.write(source,video(20));String name=name(source,"avi");Path server=root.resolve("server");UUID owner=UUID.randomUUID();
        var a=new DiskMediaTransfers.Key(owner,UUID.randomUUID());var b=new DiskMediaTransfers.Key(owner,UUID.randomUUID());var c=new DiskMediaTransfers.Key(owner,UUID.randomUUID());
        try(var transfers=new DiskMediaTransfers(clock::get))
        {
            transfers.begin(a,server,name,20);transfers.begin(b,server,name,20);assertThrows(IOException.class,()->transfers.begin(c,server,name,20));assertEquals(2,parts(server));
            transfers.cancel(a);assertEquals(1,parts(server));clock.addAndGet(60_001);transfers.expire();assertEquals(0,parts(server));
            assertThrows(IOException.class,()->transfers.begin(a,server,name,QuestMedia.VIDEO_LIMIT+1));
            transfers.begin(a,server,name,20);transfers.removeOwner(owner);assertEquals(0,parts(server));
        }
    }
    @Test void namesHeadersAndRealFilesAreCheckedBeforePublication() throws Exception
    {
        for(String invalid:List.of("../x.png","a".repeat(64)+"/x.png","A".repeat(64)+".png","f".repeat(64)+".exe"))assertFalse(QuestMedia.validName(invalid));
        Path bad=root.resolve("wrong.mp4");Files.writeString(bad,"This is not an mp4");assertThrows(IOException.class,()->QuestMedia.prepare(bad,true,root.resolve("cache"),()->false));
        Path real=image("good.png","png"),link=root.resolve("link.png");Files.createSymbolicLink(link,real);
        assertThrows(IOException.class,()->QuestMedia.validate(link,name(real,"png")));
        assertThrows(IllegalArgumentException.class,()->QuestMedia.validateChunk(name(real,"png"),QuestMedia.IMAGE_LIMIT+1,0,QuestMedia.CHUNK));
        assertThrows(IllegalArgumentException.class,()->QuestMedia.validateChunk(name(real,"png"),123,1,122));
    }
}
