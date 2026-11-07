package com.feed_the_beast.ftbquests.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class QuestAssets
{
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private QuestAssets() {}
    public static boolean validName(String name) { return name.matches("[0-9a-f]{64}\\.(png|gif)"); }
    public static String name(byte[] bytes, String extension)
    {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "." + extension; }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static void store(Path directory, String name, byte[] bytes) throws IOException
    {
        if (!validName(name) || bytes.length > MAX_BYTES || !name(bytes, name.endsWith(".gif") ? "gif" : "png").equals(name))
            throw new IOException("Invalid quest image asset");
        if (!Files.exists(directory.resolve(name))) TransactionalStorage.writeAtomic(directory.resolve(name), bytes, false);
    }
}
