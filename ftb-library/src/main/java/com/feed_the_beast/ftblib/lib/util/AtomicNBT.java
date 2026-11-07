package com.feed_the_beast.ftblib.lib.util;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;

/** Durable team/player saves, with a copy of the previous file retained for recovery. */
public final class AtomicNBT
{
    private AtomicNBT() {}

    private static void checkPath(Path path) throws IOException
    {
        for (Path part = path.toAbsolutePath(); part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IOException("FTB data path contains a symbolic link: " + part);
    }

    public static synchronized void backup(Path path) throws IOException
    {
        path = path.toAbsolutePath();
        checkPath(path);
        checkPath(path.getParent().resolve(".backups"));
        if (!Files.exists(path)) return;
        Path previous = path.getParent().resolve(".backups").resolve(path.getFileName() + "-" + UUID.randomUUID());
        Files.createDirectories(previous.getParent());
        Files.copy(path, previous, StandardCopyOption.COPY_ATTRIBUTES);
    }

    public static synchronized void write(File file, NBTTagCompound tag)
    {
        Path path = file.toPath().toAbsolutePath();
        Path temp = path.resolveSibling("." + path.getFileName() + ".tmp-" + UUID.randomUUID());
        try
        {
            checkPath(path);
            Files.createDirectories(path.getParent());
            try (OutputStream stream = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW))
            { CompressedStreamTools.writeCompressed(tag, stream); }
            try (InputStream stream = Files.newInputStream(temp))
            { if (!tag.equals(CompressedStreamTools.readCompressed(stream))) throw new IOException("NBT validation failed: " + path); }
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            backup(path);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        }
        catch (IOException ex) { throw new UncheckedIOException("Could not save FTB data; previous file retained: " + file, ex); }
        finally { try { Files.deleteIfExists(temp); } catch (IOException ignored) {} }
    }

    public static NBTTagCompound read(File file)
    {
        try { checkPath(file.toPath()); }
        catch (IOException ex) { throw new UncheckedIOException(ex); }
        if (!file.exists()) return null;
        try (InputStream stream = new FileInputStream(file)) { return CompressedStreamTools.readCompressed(stream); }
        catch (IOException first)
        {
            try { return CompressedStreamTools.read(file); }
            catch (IOException second)
            {
                second.addSuppressed(first);
                throw new UncheckedIOException("FTB data is unreadable; refusing to create empty team/player data: " + file, second);
            }
        }
    }
}
