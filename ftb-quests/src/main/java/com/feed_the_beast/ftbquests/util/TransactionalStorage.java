package com.feed_the_beast.ftbquests.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Commits files with a rollback journal, keeping the live directory in place on Windows. */
public final class TransactionalStorage
{
    private static final String FILE_TRANSACTION = "files-v1";
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
        boolean prepared = false;
        boolean journalWritten = false;
        try
        {
            Files.createDirectories(parent);
            // A failed recovery must keep its own journal and snapshots intact.
            recoverDirectory(target);
            checkPath(target);
            checkPath(backup);
            if (Files.exists(target)) copyTree(target, backup);
            else Files.createDirectories(backup);
            copyTree(backup, stage);
            writer.write(stage);
            prepared = true;
            List<Path> changes = changedFiles(backup, stage);
            for (Path relative : changes)
            {
                // Do not overwrite edits made by an external editor during preparation.
                if (!sameFile(backup.resolve(relative), target.resolve(relative)))
                    throw new IOException("Quest file changed while saving: " + target.resolve(relative));
                if (Files.isRegularFile(backup.resolve(relative)))
                    try (FileChannel channel = FileChannel.open(backup.resolve(relative), StandardOpenOption.WRITE)) { channel.force(true); }
            }
            StringBuilder record = new StringBuilder(FILE_TRANSACTION).append('\n').append(transaction).append('\n');
            for (Path relative : changes)
                record.append(Files.exists(backup.resolve(relative)) ? "R " : "N ")
                        .append(Base64.getEncoder().encodeToString(relative.toString().replace(java.io.File.separatorChar, '/').getBytes(StandardCharsets.UTF_8))).append('\n');
            writeAtomic(journal, record.toString().getBytes(StandardCharsets.UTF_8), false);
            journalWritten = true;
            Files.createDirectories(target);
            for (Path relative : changes)
            {
                Path source = stage.resolve(relative), destination = target.resolve(relative);
                if (Files.exists(source)) copyAtomic(source, destination);
                else Files.deleteIfExists(destination);
            }
            // Removing the journal commits the complete file set. Cleanup is not a new transaction.
            Files.delete(journal);
            journalWritten = false;
            deleteTreeQuietly(stage);
        }
        catch (Exception failure)
        {
            boolean recovered = !journalWritten;
            if (journalWritten)
            {
                try { recoverDirectory(target); recovered = true; }
                catch (IOException recovery) { failure.addSuppressed(recovery); }
            }
            // A fully serialized, failed save is retained for manual recovery even after rollback.
            if (!prepared) deleteTreeQuietly(stage);
            String message = "Could not save quests; " + (recovered ? "previous files retained" : "rollback pending; keep the transaction journal and backups")
                    + ": " + target + (prepared ? "; unsaved snapshot: " + stage : "");
            if (failure instanceof IOException) throw new UncheckedIOException(message, (IOException) failure);
            throw new IllegalStateException(message, failure);
        }
    }

    public static synchronized void recoverDirectory(Path directory) throws IOException
    {
        Path target = directory.toAbsolutePath().normalize();
        Path parent = target.getParent();
        String name = target.getFileName().toString();
        Path journal = parent.resolve("." + name + ".transaction");
        if (!Files.exists(journal)) return;
        checkPath(journal);
        List<String> lines = Files.readAllLines(journal, StandardCharsets.UTF_8);
        boolean files = !lines.isEmpty() && FILE_TRANSACTION.equals(lines.get(0));
        String transaction;
        try { transaction = UUID.fromString(files ? lines.get(1) : String.join("", lines).trim()).toString(); }
        catch (IllegalArgumentException | IndexOutOfBoundsException ex) { throw new IOException("Invalid quest transaction journal: " + journal, ex); }
        Path backup = parent.resolve(".backups").resolve(name + "-" + transaction);
        Path stage = parent.resolve("." + name + ".stage-" + transaction);
        checkPath(target); checkPath(backup); checkPath(stage);
        if (files)
        {
            if (!Files.isDirectory(backup)) throw new IOException("Quest rollback backup is missing: " + backup);
            // Parse every path before making any recovery changes.
            List<Path> changes = new ArrayList<>();
            Set<Path> unique = new HashSet<>();
            for (int i = 2; i < lines.size(); i++)
            {
                Path relative;
                try
                {
                    String entry = lines.get(i);
                    if (!entry.startsWith("R ") && !entry.startsWith("N ")) throw new IllegalArgumentException("Invalid recovery operation");
                    String text = new String(Base64.getDecoder().decode(entry.substring(2)), StandardCharsets.UTF_8);
                    relative = target.getFileSystem().getPath(text);
                    if (text.isEmpty() || relative.isAbsolute() || !relative.normalize().equals(relative)
                            || relative.startsWith("..") || !target.resolve(relative).normalize().startsWith(target) || !unique.add(relative))
                        throw new IllegalArgumentException("Unsafe or duplicate recovery path");
                    if (entry.startsWith("R ") && !Files.isRegularFile(backup.resolve(relative), LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Original quest file is missing from rollback backup: " + backup.resolve(relative));
                    if (entry.startsWith("N ") && Files.exists(backup.resolve(relative), LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Unexpected file in rollback backup: " + backup.resolve(relative));
                }
                catch (IllegalArgumentException ex) { throw new IOException("Invalid quest recovery path in " + journal, ex); }
                checkPath(backup.resolve(relative)); checkPath(target.resolve(relative));
                changes.add(relative);
            }
            Files.createDirectories(target);
            for (Path relative : changes)
            {
                Path previous = backup.resolve(relative), destination = target.resolve(relative);
                if (sameFile(previous, destination)) continue;
                if (Files.exists(previous)) copyAtomic(previous, destination);
                else Files.deleteIfExists(destination);
            }
            // Keep the uncommitted stage as an emergency snapshot, not as live quest data.
            Files.delete(journal);
        }
        else
        {
            // Read the UUID-only journals written by modern.16 and older versions.
            if (!Files.exists(target))
            {
                if (Files.exists(backup)) move(backup, target);
                else if (Files.exists(stage)) move(stage, target);
                else throw new IOException("Quest storage recovery files are missing for " + target);
            }
            if (Files.exists(stage)) deleteTree(stage);
            Files.delete(journal);
        }
    }

    private static List<Path> changedFiles(Path before, Path after) throws IOException
    {
        Set<Path> paths = new TreeSet<>();
        collectFiles(before, paths); collectFiles(after, paths);
        List<Path> changes = new ArrayList<>();
        for (Path relative : paths)
            if (!sameFile(before.resolve(relative), after.resolve(relative))) changes.add(relative);
        return changes;
    }

    private static void collectFiles(Path root, Set<Path> paths) throws IOException
    {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>()
        {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                if (!attrs.isRegularFile()) throw new IOException("Not a regular quest data file: " + file);
                paths.add(root.relativize(file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean sameFile(Path first, Path second) throws IOException
    {
        checkPath(first); checkPath(second);
        boolean a = Files.exists(first, LinkOption.NOFOLLOW_LINKS), b = Files.exists(second, LinkOption.NOFOLLOW_LINKS);
        if (a && !Files.isRegularFile(first)) throw new IOException("Not a regular quest file: " + first);
        if (b && !Files.isRegularFile(second)) throw new IOException("Not a regular quest file: " + second);
        return a == b && (!a || Files.mismatch(first, second) == -1);
    }

    private static void checkPath(Path path) throws IOException
    {
        for (Path part = path; part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IOException("Quest storage path contains a symbolic link: " + part);
    }

    private static void copyAtomic(Path source, Path target) throws IOException
    {
        checkPath(source); checkPath(target);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try
        {
            Files.copy(source, temp);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { Files.deleteIfExists(temp); }
    }

    public static synchronized void writeAtomic(Path file, byte[] bytes, boolean backup) throws IOException
    {
        Path target = file.toAbsolutePath().normalize();
        checkPath(target);
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
                checkPath(previous);
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
        checkPath(backup);
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
                if (!attrs.isRegularFile()) throw new IOException("Cannot safely save non-regular quest data: " + file);
                Files.copy(file, to.resolve(from.relativize(file)), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTreeQuietly(Path root)
    { try { if (Files.exists(root)) deleteTree(root); } catch (IOException ignored) { /* Live data is already committed or rolled back. */ } }

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
