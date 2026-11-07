package com.feed_the_beast.ftbquests.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Keeps the previous complete directory until a replacement has been written successfully. */
public final class TransactionalStorage
{
    private TransactionalStorage() {}

    @FunctionalInterface
    public interface Writer { void write(Path stage) throws IOException; }

    public static synchronized void saveDirectory(Path directory, Writer writer)
    {
        Path target = directory.toAbsolutePath().normalize();
        Path parent = target.getParent();
        String name = target.getFileName().toString();
        String transaction = UUID.randomUUID().toString();
        Path stage = parent.resolve("." + name + ".stage-" + transaction);
        Path backup = parent.resolve(".backups").resolve(name + "-" + transaction);
        Path journal = parent.resolve("." + name + ".transaction");
        try
        {
            Files.createDirectories(parent);
            recoverDirectory(target);
            if (Files.exists(target)) copyTree(target, stage);
            else Files.createDirectory(stage);
            writer.write(stage);
            // The journal is committed before the old directory is moved. Startup can recover it.
            writeAtomic(journal, transaction.getBytes(java.nio.charset.StandardCharsets.UTF_8), false);
            if (Files.exists(target))
            {
                Files.createDirectories(backup.getParent());
                move(target, backup);
            }
            move(stage, target);
            Files.deleteIfExists(journal);
        }
        catch (Exception failure)
        {
            try
            {
                if (!Files.exists(target) && Files.exists(backup)) move(backup, target);
                if (Files.exists(target)) Files.deleteIfExists(journal);
                if (Files.exists(stage)) deleteTree(stage);
            }
            catch (IOException recovery) { failure.addSuppressed(recovery); }
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw new UncheckedIOException("Could not save quests; the previous data was retained: " + target,
                    (IOException) failure);
        }
    }

    public static synchronized void recoverDirectory(Path directory) throws IOException
    {
        Path target = directory.toAbsolutePath().normalize();
        Path parent = target.getParent();
        String name = target.getFileName().toString();
        Path journal = parent.resolve("." + name + ".transaction");
        if (!Files.exists(journal)) return;
        String transaction = Files.readString(journal).trim();
        // Only UUIDs written by this class may select a recovery path.
        UUID.fromString(transaction);
        Path backup = parent.resolve(".backups").resolve(name + "-" + transaction);
        Path stage = parent.resolve("." + name + ".stage-" + transaction);
        if (!Files.exists(target))
        {
            if (Files.exists(backup)) move(backup, target);
            else if (Files.exists(stage)) move(stage, target);
            else throw new IOException("Quest storage recovery files are missing for " + target);
        }
        if (Files.exists(stage)) deleteTree(stage);
        Files.delete(journal);
    }

    public static synchronized void writeAtomic(Path file, byte[] bytes, boolean backup) throws IOException
    {
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        String transaction = UUID.randomUUID().toString();
        Path temp = target.resolveSibling("." + target.getFileName() + ".tmp-" + transaction);
        try
        {
            Files.write(temp, bytes, StandardOpenOption.CREATE_NEW);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            if (backup && Files.exists(target))
            {
                Path previous = target.getParent().resolve(".backups").resolve(target.getFileName() + "-" + transaction);
                Files.createDirectories(previous.getParent());
                Files.copy(target, previous, StandardCopyOption.COPY_ATTRIBUTES);
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { Files.deleteIfExists(temp); }
    }

    public static void backupFile(Path file) throws IOException
    {
        if (!Files.exists(file)) return;
        Path backup = file.getParent().resolve(".backups").resolve(file.getFileName() + "-" + UUID.randomUUID());
        Files.createDirectories(backup.getParent());
        Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void move(Path from, Path to) throws IOException
    {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(from, to); }
    }

    private static void copyTree(Path from, Path to) throws IOException
    {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>()
        {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                Files.createDirectories(to.resolve(from.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if (attrs.isSymbolicLink()) throw new IOException("Cannot safely replace quest data containing a symbolic link: " + file);
                Files.copy(file, to.resolve(from.relativize(file)), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path root) throws IOException
    {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>()
        {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException
            { if (exception != null) throw exception; Files.delete(dir); return FileVisitResult.CONTINUE; }
        });
    }
}
