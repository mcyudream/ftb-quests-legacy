package dev.ftbquests.verification;

import com.feed_the_beast.ftbquests.util.TransactionalStorage;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Native Windows sharing tests; no Minecraft, mocks, or administrator privileges. */
public final class WindowsStorageCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        require(System.getProperty("os.name").startsWith("Windows"), "Run on actual Windows");
        Path root = Files.createTempDirectory("ftbq-storage-windows-");
        try {
            Path live = Files.createDirectory(root.resolve("normal"));
            Files.writeString(live.resolve("file.snbt"), "{title:\"old\",unknown:9000000001L}");
            Path asset = Files.writeString(live.resolve("addon.txt"), "unchanged addon data");
            Process holder = new ProcessBuilder("cmd.exe", "/d", "/q", "/c", "echo READY & set /p WAIT=")
                    .directory(live.toFile()).redirectErrorStream(true).start();
            try {
                require(new BufferedReader(new InputStreamReader(holder.getInputStream())).readLine().trim().equals("READY"), "Directory holder not ready");
                boolean denied = false;
                try { Files.move(live, root.resolve("renamed")); }
                catch (FileSystemException expected) { denied = true; }
                require(denied, "Native directory lock did not reproduce the old rename failure");
                System.out.println("PASS: reproduced AccessDenied directory rename while another process uses normal");
                try (FileChannel lock = FileChannel.open(asset, StandardOpenOption.READ,
                        ExtendedOpenOption.NOSHARE_DELETE, ExtendedOpenOption.NOSHARE_WRITE)) {
                    TransactionalStorage.saveDirectory(live, stage -> Files.writeString(stage.resolve("file.snbt"), "{title:\"new\",unknown:9000000001L}"));
                    require(Files.readString(live.resolve("file.snbt")).contains("title:\"new\""), "New quest data missing");
                    require(Files.readString(asset).equals("unchanged addon data"), "Unchanged locked addon file changed");
                }
                System.out.println("PASS: save under a native directory lock and unchanged NOSHARE_DELETE/NOSHARE_WRITE addon file");
                Path a = Files.writeString(live.resolve("a.snbt"), "old a");
                Path z = Files.writeString(live.resolve("z.snbt"), "old z");
                try (FileChannel lock = FileChannel.open(z, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
                    boolean failed = false;
                    try {
                        TransactionalStorage.saveDirectory(live, stage -> {
                            Files.writeString(stage.resolve("a.snbt"), "new a");
                            Files.writeString(stage.resolve("n.snbt"), "new file");
                            Files.writeString(stage.resolve("z.snbt"), "new z");
                        });
                    } catch (UncheckedIOException expected) { failed = true; }
                    require(failed, "Locked edited file unexpectedly allowed replacement");
                    require(Files.readString(a).equals("old a"), "Earlier file was not rolled back");
                    require(Files.readString(z).equals("old z"), "Locked original was lost");
                    require(!Files.exists(live.resolve("n.snbt")), "New file survived failed transaction");
                    require(!Files.exists(root.resolve(".normal.transaction")), "Completed rollback left an active journal");
                    try (var paths = Files.list(root)) {
                        require(paths.anyMatch(p -> p.getFileName().toString().startsWith(".normal.stage-") && Files.exists(p.resolve("n.snbt"))), "Unsaved snapshot was discarded");
                    }
                }
                TransactionalStorage.saveDirectory(live, stage -> {
                    Files.writeString(stage.resolve("a.snbt"), "retried a");
                    Files.writeString(stage.resolve("z.snbt"), "retried z");
                });
                require(Files.readString(a).equals("retried a") && Files.readString(z).equals("retried z"), "Retry after releasing the lock failed");
                System.out.println("PASS: native edited-file lock rolls back prior changes, preserves snapshot, then retries successfully");
            } finally {
                holder.getOutputStream().close();
                if (!holder.waitFor(5, TimeUnit.SECONDS)) holder.destroyForcibly().waitFor();
            }
            System.out.println("WINDOWS_STORAGE_PASS");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
