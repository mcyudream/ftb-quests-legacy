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
    @Test void savesWithoutReplacingTheDirectoryOrUnchangedAddonFiles() throws Exception
    {
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "old");
        Path addon = Files.writeString(dir.resolve("addon.txt"), "unmanaged");
        Object directoryKey = Files.readAttributes(dir, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        Object addonKey = Files.readAttributes(addon, java.nio.file.attribute.BasicFileAttributes.class).fileKey();
        var time = Files.getLastModifiedTime(addon);
        TransactionalStorage.saveDirectory(dir, stage -> Files.writeString(stage.resolve("file.snbt"), "new"));
        assertEquals(directoryKey, Files.readAttributes(dir, java.nio.file.attribute.BasicFileAttributes.class).fileKey());
        assertEquals(addonKey, Files.readAttributes(addon, java.nio.file.attribute.BasicFileAttributes.class).fileKey());
        assertEquals(time, Files.getLastModifiedTime(addon));
        assertEquals("new", Files.readString(dir.resolve("file.snbt")));
    }

    @Test void commitsAddedAndDeletedFilesAndPreservesCompleteBackup() throws Exception
    {
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("deleted.snbt"), "old quest");
        Files.writeString(dir.resolve("addon.txt"), "preserved");
        TransactionalStorage.saveDirectory(dir, stage -> {
            Files.delete(stage.resolve("deleted.snbt"));
            Files.createDirectories(stage.resolve("new"));
            Files.writeString(stage.resolve("new/quest.snbt"), "new quest");
        });
        assertFalse(Files.exists(dir.resolve("deleted.snbt")));
        assertEquals("new quest", Files.readString(dir.resolve("new/quest.snbt")));
        assertEquals("preserved", Files.readString(dir.resolve("addon.txt")));
        try (var backups = Files.list(root.resolve(".backups"))) {
            Path backup = backups.findFirst().orElseThrow();
            assertEquals("old quest", Files.readString(backup.resolve("deleted.snbt")));
            assertEquals("preserved", Files.readString(backup.resolve("addon.txt")));
        }
    }

    private String fileJournal(String id, String... operations)
    {
        StringBuilder out = new StringBuilder("files-v1\n").append(id).append('\n');
        for (String operation : operations) out.append(operation.charAt(0)).append(' ')
                .append(java.util.Base64.getEncoder().encodeToString(operation.substring(2).getBytes(java.nio.charset.StandardCharsets.UTF_8))).append('\n');
        return out.toString();
    }

    @Test void interruptedFileCommitRestoresOldFilesAndRetainsUnsavedSnapshot() throws Exception
    {
        String id = UUID.randomUUID().toString();
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "partially committed");
        Files.writeString(dir.resolve("created.snbt"), "uncommitted new quest");
        Files.writeString(dir.resolve("addon.txt"), "external addon edit");
        Path backup = Files.createDirectories(root.resolve(".backups/normal-" + id));
        Files.writeString(backup.resolve("file.snbt"), "old book");
        Files.writeString(backup.resolve("deleted.snbt"), "old removed quest");
        Path stage = Files.createDirectory(root.resolve(".normal.stage-" + id));
        Files.writeString(stage.resolve("file.snbt"), "unsaved snapshot");
        Files.writeString(root.resolve(".normal.transaction"), fileJournal(id, "R file.snbt", "R deleted.snbt", "N created.snbt"));
        TransactionalStorage.recoverDirectory(dir);
        assertEquals("old book", Files.readString(dir.resolve("file.snbt")));
        assertEquals("old removed quest", Files.readString(dir.resolve("deleted.snbt")));
        assertEquals("external addon edit", Files.readString(dir.resolve("addon.txt")));
        assertFalse(Files.exists(dir.resolve("created.snbt")));
        assertEquals("unsaved snapshot", Files.readString(stage.resolve("file.snbt")));
        assertFalse(Files.exists(root.resolve(".normal.transaction")));
    }

    @Test void failedRollbackKeepsJournalAndCanFinishAfterTheBlockerIsRemoved() throws Exception
    {
        String id = UUID.randomUUID().toString();
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("a.snbt"), "new a");
        Files.createDirectory(dir.resolve("b.snbt"));
        Path backup = Files.createDirectories(root.resolve(".backups/normal-" + id));
        Files.writeString(backup.resolve("a.snbt"), "old a");
        Files.writeString(backup.resolve("b.snbt"), "old b");
        Files.createDirectory(root.resolve(".normal.stage-" + id));
        Path journal = Files.writeString(root.resolve(".normal.transaction"), fileJournal(id, "R a.snbt", "R b.snbt"));
        assertThrows(IOException.class, () -> TransactionalStorage.recoverDirectory(dir));
        assertTrue(Files.exists(journal));
        assertTrue(Files.exists(backup.resolve("a.snbt")));
        Files.delete(dir.resolve("b.snbt"));
        TransactionalStorage.recoverDirectory(dir);
        assertEquals("old a", Files.readString(dir.resolve("a.snbt")));
        assertEquals("old b", Files.readString(dir.resolve("b.snbt")));
        assertFalse(Files.exists(journal));
    }

    @Test void missingOriginalBackupCannotBeMistakenForANewFile() throws Exception
    {
        String id = UUID.randomUUID().toString();
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "last available copy");
        Files.createDirectories(root.resolve(".backups/normal-" + id));
        Path journal = Files.writeString(root.resolve(".normal.transaction"), fileJournal(id, "R file.snbt"));
        assertThrows(IOException.class, () -> TransactionalStorage.recoverDirectory(dir));
        assertEquals("last available copy", Files.readString(dir.resolve("file.snbt")));
        assertTrue(Files.exists(journal));
    }

    @Test void invalidRecoveryDoesNotDeleteJournalOrStartAnotherSave() throws Exception
    {
        String id = UUID.randomUUID().toString();
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "original");
        Path backup = Files.createDirectories(root.resolve(".backups/normal-" + id));
        Files.writeString(backup.resolve("file.snbt"), "backup");
        String record = fileJournal(id, "R file.snbt", "N ../outside.txt");
        Path journal = Files.writeString(root.resolve(".normal.transaction"), record);
        assertThrows(RuntimeException.class, () -> TransactionalStorage.saveDirectory(dir, stage -> fail("writer called after failed recovery")));
        assertEquals(record, Files.readString(journal));
        assertEquals("original", Files.readString(dir.resolve("file.snbt")));
    }

    @Test void externalChangesDuringPreparationAreNotOverwritten() throws Exception
    {
        Path dir = Files.createDirectory(root.resolve("normal"));
        Files.writeString(dir.resolve("file.snbt"), "original");
        RuntimeException failure = assertThrows(RuntimeException.class, () -> TransactionalStorage.saveDirectory(dir, stage -> {
            Files.writeString(stage.resolve("file.snbt"), "game edit");
            Files.writeString(dir.resolve("file.snbt"), "external editor");
        }));
        assertEquals("external editor", Files.readString(dir.resolve("file.snbt")));
        assertTrue(failure.getMessage().contains("unsaved snapshot"));
        assertFalse(Files.exists(root.resolve(".normal.transaction")));
    }

}
