package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.universe.UniverseSavedEvent;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.FTBQuestsWorldData;
import net.minecraft.nbt.NBTTagCompound;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Real event delivery and installed-jar persistence; does not alter the user's instance. */
public final class AcceptanceStorage {
    private static boolean checked;
    public static void check(ServerQuestFile file) throws Exception {
        if (checked || !Boolean.getBoolean("ftbquests.acceptance.storage")) return;
        checked = true;
        var original = AcceptanceMod.snapshot(file);
        file.saveNow();
        Quest quest = file.chapters.stream().flatMap(c -> c.quests.stream()).findFirst().orElseThrow();
        int id = quest.id;
        String title = quest.title;
        Path root = file.getFolder().toPath();
        Object directoryKey = Files.readAttributes(root, BasicFileAttributes.class).fileKey();
        Path definition = root.resolve("chapters/" + quest.chapter.getCodeString() + "/" + quest.getCodeString() + ".snbt");
        byte[] previous = Files.readAllBytes(definition);
        quest.title = "保存故障后保留的中文编辑";
        file.save();
        var pending = AcceptanceMod.snapshot(file);
        Path blocker = root.resolveSibling("." + root.getFileName() + ".transaction");
        Files.createDirectory(blocker);
        try {
            FTBQuestsWorldData.onUniverseSaved(new UniverseSavedEvent(file.universe, new NBTTagCompound()));
            AcceptanceMod.require(file.shouldSave, "Autosave failure cleared pending edits");
            AcceptanceMod.require(pending.equals(AcceptanceMod.snapshot(file)), "Autosave failure changed memory");
            AcceptanceMod.require(java.util.Arrays.equals(previous, Files.readAllBytes(definition)), "Autosave failure changed disk data");
        } finally { Files.delete(blocker); }
        Thread.sleep(5100);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent(
                net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END));
        AcceptanceMod.require(!file.shouldSave, "Server tick did not retry after the five-second backoff");
        file.readDataFull(root.toFile());
        AcceptanceMod.require(pending.equals(AcceptanceMod.snapshot(file)), "Retried edits did not survive full reload");
        file.getQuest(id).title = title;
        file.saveNow();
        file.readDataFull(root.toFile());
        AcceptanceMod.require(original.equals(AcceptanceMod.snapshot(file)), "Storage regression changed old quest IDs or unknown data");
        AcceptanceMod.require(java.util.Objects.equals(directoryKey, Files.readAttributes(root, BasicFileAttributes.class).fileKey()), "Save replaced the live directory");
        Files.writeString(Path.of("storage-checks.json"), "{\"result\":\"PASS\",\"world_autosave_io_failure_does_not_escape\":true,\"pending_memory_retained\":true,\"disk_retained_on_failure\":true,\"server_tick_retry_after_five_seconds_and_full_reload\":true,\"original_book_preserved\":true,\"live_directory_retained\":true}");
    }
}
