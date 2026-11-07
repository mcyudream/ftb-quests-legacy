package com.feed_the_beast.ftbquests.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransactionalStorageTest
{
    @TempDir Path root;

    @Test void failedWriteRetainsAllOriginalFiles() throws Exception
    {
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "original");
        Files.writeString(dir.resolve("unknown.bin"), "addon data");
        assertThrows(RuntimeException.class, () -> TransactionalStorage.saveDirectory(dir, stage -> {
            Files.writeString(stage.resolve("file.snbt"), "partial");
            throw new IOException("disk failure");
        }));
        assertEquals("original", Files.readString(dir.resolve("file.snbt")));
        assertEquals("addon data", Files.readString(dir.resolve("unknown.bin")));
    }

    @Test void replacementKeepsBackupAndUnmanagedAssets() throws Exception
    {
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "original");
        Files.createDirectories(dir.resolve("text/zh_cn"));
        Files.writeString(dir.resolve("text/zh_cn/abcdef12.txt"), "多行任务\n描述");
        TransactionalStorage.saveDirectory(dir, stage -> Files.writeString(stage.resolve("file.snbt"), "edited"));
        assertEquals("edited", Files.readString(dir.resolve("file.snbt")));
        assertEquals("多行任务\n描述", Files.readString(dir.resolve("text/zh_cn/abcdef12.txt")));
        try (var backups = Files.list(root.resolve(".backups")))
        {
            Path previous = backups.findFirst().orElseThrow();
            assertEquals("original", Files.readString(previous.resolve("file.snbt")));
        }
    }

    @Test void recoversCrashBetweenMovingOldAndCommittingNewDirectory() throws Exception
    {
        String id = UUID.randomUUID().toString();
        Path backup = Files.createDirectories(root.resolve(".backups/normal-" + id));
        Files.writeString(backup.resolve("file.snbt"), "original");
        Path stage = Files.createDirectory(root.resolve(".normal.stage-" + id));
        Files.writeString(stage.resolve("file.snbt"), "uncommitted");
        Files.writeString(root.resolve(".normal.transaction"), id);
        TransactionalStorage.recoverDirectory(root.resolve("normal"));
        assertEquals("original", Files.readString(root.resolve("normal/file.snbt")));
        assertFalse(Files.exists(stage));
        assertFalse(Files.exists(root.resolve(".normal.transaction")));
    }

    @Test void atomicProgressWriteKeepsPreviousVersion() throws Exception
    {
        Path file = root.resolve("team/ftbquests.dat");
        TransactionalStorage.writeAtomic(file, new byte[]{1, 2}, true);
        TransactionalStorage.writeAtomic(file, new byte[]{3, 4}, true);
        assertArrayEquals(new byte[]{3, 4}, Files.readAllBytes(file));
        try (var backups = Files.list(file.getParent().resolve(".backups")))
        { assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(backups.findFirst().orElseThrow())); }
    }
}
