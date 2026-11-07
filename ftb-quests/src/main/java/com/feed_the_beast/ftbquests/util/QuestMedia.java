package com.feed_the_beast.ftbquests.util;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Immutable, content addressed imports. The shared media directory is outside quest save transactions. */
public final class QuestMedia
{
    public static final int IMAGE_LIMIT = 8 * 1024 * 1024, VIDEO_LIMIT = 256 * 1024 * 1024;
    public static final int CHUNK = 24 * 1024, WINDOW = 4;
    public static final Set<String> IMAGES = Set.of("png", "apng", "gif", "jpg", "jpeg", "bmp");
    public static final Set<String> VIDEOS = Set.of("mp4", "m4v", "mov", "mkv", "webm", "avi", "ogv", "mpeg", "mpg");
    private QuestMedia() {}
    public static String extension(String name) { return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT); }
    public static boolean validName(String name)
    { return name != null && name.matches("[0-9a-f]{64}\\.(png|gif|mp4|m4v|mov|mkv|webm|avi|ogv|mpeg|mpg)"); }
    public static boolean video(String name) { return VIDEOS.contains(extension(name)); }
    public static boolean selectable(Path path, boolean video) { return (video ? VIDEOS : IMAGES).contains(extension(path.getFileName().toString())); }
    public static int limit(String name) { return video(name) ? VIDEO_LIMIT : IMAGE_LIMIT; }
    public static String hash(Path path) throws IOException
    {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void validateChunk(String name, int total, int offset, int size)
    {
        if (!validName(name) || total < 1 || total > limit(name) || offset < 0 || offset >= total
                || offset % CHUNK != 0 || size != Math.min(CHUNK, total - offset)) throw new IllegalArgumentException("invalid");
    }
    public static void validate(Path file, String name) throws IOException
    {
        if (!validName(name) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) < 1 || Files.size(file) > limit(name)
                || !hash(file).equals(name.substring(0, 64))) throw new IOException("invalid");
        if (!video(name))
        {
            try (var input = ImageIO.createImageInputStream(file.toFile()))
            {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("format");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!extension(name).equals(format)) throw new IOException("format");
                    int width = reader.getWidth(0), height = reader.getHeight(0), count = reader.getNumImages(true);
                    if (width < 1 || height < 1 || count < 1 || count > 512 || (long)width * height > 16_777_216L
                            || (long)width * height * count > 33_554_432L) throw new IOException("dimensions");
                } finally { reader.dispose(); }
            }
        }
        else
        {
            byte[] header;
            try (InputStream input = Files.newInputStream(file)) { header = input.readNBytes(16); }
            String ascii = new String(header, StandardCharsets.ISO_8859_1), ext = extension(name);
            boolean supported = switch (ext) {
                case "mp4", "m4v", "mov" -> ascii.length() >= 8 && (ascii.substring(4, 8).equals("ftyp") || ext.equals("mov") && Set.of("moov", "mdat", "wide").contains(ascii.substring(4, 8)));
                case "avi" -> ascii.startsWith("RIFF") && ascii.length() >= 12 && ascii.substring(8, 12).equals("AVI ");
                case "ogv" -> ascii.startsWith("OggS");
                case "mkv", "webm" -> header.length >= 4 && (header[0] & 255) == 0x1a && (header[1] & 255) == 0x45 && (header[2] & 255) == 0xdf && (header[3] & 255) == 0xa3;
                case "mpeg", "mpg" -> header.length >= 4 && header[0] == 0 && header[1] == 0 && header[2] == 1 && ((header[3] & 255) == 0xba || (header[3] & 255) == 0xb3);
                default -> false;
            };
            if (!supported) throw new IOException("format");
        }
    }
    public static void commit(Path temporary, Path directory, String name) throws IOException
    {
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.createDirectories(directory);
        Path target = directory.resolve(name);
        if (Files.isSymbolicLink(target)) throw new IOException("invalid");
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    /** Copies the selected file; never changes or moves the original. JPEG/BMP become PNG, animation bytes stay intact. */
    public static Path prepare(Path source, boolean video, Path cache, BooleanSupplier cancelled) throws IOException
    {
        if (!Files.isRegularFile(source) || !selectable(source, video)) throw new IOException("format");
        int limit = video ? VIDEO_LIMIT : IMAGE_LIMIT;
        if (Files.size(source) < 1 || Files.size(source) > limit) throw new IOException("size");
        Files.createDirectories(cache);
        Path temporary = Files.createTempFile(cache, ".import-", ".part");
        try
        {
            try (InputStream in = Files.newInputStream(source); OutputStream out = Files.newOutputStream(temporary))
            {
                byte[] buffer = new byte[65536]; int n, total = 0;
                while ((n = in.read(buffer)) != -1) { if (cancelled.getAsBoolean()) throw new IOException("cancelled"); total += n; if (total > limit) throw new IOException("size"); out.write(buffer, 0, n); }
            }
            String extension = extension(source.getFileName().toString());
            if (!video && !Set.of("png", "apng", "gif").contains(extension))
            {
                java.awt.image.BufferedImage image;
                try (var in = ImageIO.createImageInputStream(temporary.toFile()))
                {
                    var readers = ImageIO.getImageReaders(in); if (!readers.hasNext()) throw new IOException("format");
                    var reader = readers.next();
                    try {
                        reader.setInput(in); int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width < 1 || height < 1 || (long)width * height > 16_777_216L) throw new IOException("dimensions");
                        image = reader.read(0);
                    } finally { reader.dispose(); }
                }
                if (!ImageIO.write(image,"png",temporary.toFile())) throw new IOException("format");
                extension = "png";
            }
            if (extension.equals("apng")) extension = "png";
            String name = hash(temporary) + "." + extension;
            validate(temporary, name);
            if (cancelled.getAsBoolean()) throw new IOException("cancelled");
            commit(temporary, cache, name); return cache.resolve(name);
        }
        finally { Files.deleteIfExists(temporary); }
    }
}
