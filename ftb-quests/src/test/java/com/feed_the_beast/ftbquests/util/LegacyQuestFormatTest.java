package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.FTBQuestsCommon;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyQuestFormatTest
{
    @TempDir Path root;

    @BeforeAll static void bootstrap()
    {
        Bootstrap.register();
        FTBQuests.PROXY = new FTBQuestsCommon();
        TaskType.createRegistry();
        RewardType.createRegistry();
        FTBQuestsTasks.KILL = new TaskType(KillTask::new).setRegistryName("ftbquests:kill");
        FTBQuestsTasks.CHECKMARK = new TaskType(CheckmarkTask::new).setRegistryName("ftbquests:checkmark");
        FTBQuestsTasks.MISSING = new TaskType(MissingTask::new).setRegistryName("ftbquests:missing_task");
        FTBQuestsRewards.MISSING = new RewardType(MissingReward::new).setRegistryName("ftbquests:missing_reward");
        FTBQuestsRewards.RANDOM = new RewardType(RandomReward::new).setRegistryName("ftbquests:random");
        TaskType.getRegistry().registerAll(FTBQuestsTasks.KILL, FTBQuestsTasks.CHECKMARK, FTBQuestsTasks.MISSING);
        RewardType.getRegistry().register(FTBQuestsRewards.MISSING);
        RewardType.getRegistry().register(FTBQuestsRewards.RANDOM);
    }

    private FixtureFile oldBook() throws Exception
    {
        Path folder = root.resolve("normal");
        write(folder.resolve("file.snbt"), "{version:9, title:\"旧任务书\", external_file:{list:[1,2,3]}}");
        write(folder.resolve("chapters/index.snbt"), "{index:[\"12345678\"]}");
        write(folder.resolve("reward_tables/index.snbt"), "{index:[]}");
        write(folder.resolve("chapters/12345678/chapter.snbt"), "{title:\"章节\", external_chapter:42L}");
        write(folder.resolve("chapters/12345678/fffffffe.snbt"), """
            {title:"任务", x:1.0d, y:2.0d, external_quest:{text:"保留"},
             tasks:[{uid:"10000001",type:"kill",entity:"minecraft:zombie",value:4000000000L,external_task:"保留"},
                    {uid:"10000002",type:"uninstalled:task",value:9000000000L,nested:{array:[I;1,2]}}],
             rewards:[{uid:"20000001",type:"uninstalled:reward",amount:64,extension:{name:"保留"}}]}
            """);
        write(folder.resolve("text/zh_cn/fffffffe.txt"), "[description]\n原始文本\n第二行");
        FixtureFile book = new FixtureFile(folder.toFile());
        ServerQuestFile.INSTANCE = book;
        book.readDataFull(folder.toFile());
        return book;
    }

    private static void write(Path path, String text) throws Exception
    { Files.createDirectories(path.getParent()); Files.writeString(path, text); }

    @Test void readsEditsSavesAndReloadsLegacyIDsAndUnavailableAddonData() throws Exception
    {
        FixtureFile first = oldBook();
        Quest quest = first.getQuest(-2);
        assertNotNull(quest);
        assertEquals(4000000000L, quest.tasks.get(0).getMaxProgress());
        assertInstanceOf(MissingTask.class, quest.tasks.get(1));
        assertInstanceOf(MissingReward.class, quest.rewards.get(0));
        assertFalse(quest.rewards.get(0).isAvailable());
        quest.title = "修改后";
        first.writeDataFull(first.getFolder());
        FixtureFile second = new FixtureFile(first.getFolder());
        ServerQuestFile.INSTANCE = second;
        second.readDataFull(second.getFolder());
        assertEquals("修改后", second.getQuest(-2).title);
        assertEquals(0x10000001, second.getQuest(-2).tasks.get(0).id);
        assertEquals(4000000000L, second.getQuest(-2).tasks.get(0).getMaxProgress());
        NBTTagCompound saved = SNBT.read(root.resolve("normal/chapters/12345678/fffffffe.snbt").toFile());
        assertEquals("保留", saved.getCompoundTag("external_quest").getString("text"));
        NBTTagList tasks = saved.getTagList("tasks", 10);
        assertEquals("uninstalled:task", tasks.getCompoundTagAt(1).getString("type"));
        assertEquals(9000000000L, tasks.getCompoundTagAt(1).getLong("value"));
        assertArrayEquals(new int[]{1,2}, tasks.getCompoundTagAt(1).getCompoundTag("nested").getIntArray("array"));
        assertEquals("uninstalled:reward", saved.getTagList("rewards",10).getCompoundTagAt(0).getString("type"));
        assertEquals("[description]\n原始文本\n第二行", Files.readString(root.resolve("normal/text/zh_cn/fffffffe.txt")));
        assertNotNull(SNBT.read(root.resolve("normal/file.snbt").toFile()).getTag("external_file"));
    }

    @Test void clearedKnownOptionalFieldsDoNotReappearFromPreservedData() throws Exception
    {
        FixtureFile book = oldBook();
        Task task = book.getTask(0x10000001);
        NBTTagCompound input = new NBTTagCompound();
        task.writeData(input);
        input.setBoolean("optional_task", true);
        task.readDataPreservingUnknown(input);
        task.optionalTask = false;
        NBTTagCompound output = new NBTTagCompound();
        task.writeData(output);
        assertFalse(output.hasKey("optional_task"));
        assertEquals("保留", output.getString("external_task"));
    }

    @Test void textActionsRequireAnExplicitDeclarationInTheirSource() throws Exception
    {
        Quest quest = oldBook().getQuest(-2);
        quest.description.clear();
        assertFalse(ScriptTextActions.declared(quest, "shop/open"));
        quest.description.add("{\"type\":\"zs\",\"value\":\"shop/open\"}");
        assertTrue(ScriptTextActions.declared(quest, "shop/open"));
        assertFalse(ScriptTextActions.declared(quest, "shop/admin"));
        quest.description.clear();
        quest.description.add("{\"text\":\"Open\",\"extra\":[{\"text\":\"shop\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"zs:shop/open\"}}]}");
        assertTrue(ScriptTextActions.declared(quest, "shop/open"));
        assertFalse(ScriptTextActions.valid("a".repeat(129)));
        assertFalse(ScriptTextActions.valid("command with spaces"));
    }

    @Test void retainsOrphanProgressClaimRecordsAndLargeCounts() throws Exception
    {
        FixtureFile book = oldBook();
        var constructor = ServerQuestData.class.getDeclaredConstructor(ForgeTeam.class);
        constructor.setAccessible(true);
        ServerQuestData data = constructor.newInstance((ForgeTeam) null);
        NBTTagCompound input = JsonToNBT.getTagFromJson("""
            {Tasks:{10000001:3500000000L,10000002:7000000000L,99999999:17},
             ClaimedTeamRewards:[I;536870913],
             ClaimedPlayerRewards:{"00000000-0000-0000-0000-000000000001":[I;536870914]},
             addon_progress:{timer:1234567890123L}}
            """);
        data.readData(input);
        NBTTagCompound output = new NBTTagCompound();
        data.writeData(output);
        for (String key : input.getKeySet()) assertEquals(input.getTag(key), output.getTag(key), key);
        assertEquals(3500000000L, data.getTaskData(book.getTask(0x10000001)).progress);
    }

    @Test void markdownScriptLinksAreDeclaredOnlyWhenTheyRenderAsActions() throws Exception
    {
        Quest quest = oldBook().getQuest(-2);
        var doc = new com.feed_the_beast.ftbquests.texteditor.DescriptionDocument(List.of());
        quest.description.clear();
        quest.description.addAll(doc.encode("[Open](zs:shop/open)\n```\n[Hidden](zs:shop/admin)\n```\n`[Code](zs:shop/code)`", true));
        assertTrue(ScriptTextActions.declared(quest, "shop/open"));
        assertFalse(ScriptTextActions.declared(quest, "shop/admin"));
        assertFalse(ScriptTextActions.declared(quest, "shop/code"));
    }

    @Test void corruptInputCannotBeSilentlyOverwritten() throws Exception
    {
        FixtureFile book = oldBook();
        Path file = root.resolve("normal/chapters/12345678/fffffffe.snbt");
        Files.writeString(file, "{broken");
        assertThrows(IllegalStateException.class, () -> book.readDataFull(book.getFolder()));
        assertEquals("{broken", Files.readString(file));
    }

    @Test void unresolvedDependencyMetadataAndIndexExtensionsSurviveSaveAndReload() throws Exception
    {
        FixtureFile book = oldBook();
        Path index = root.resolve("normal/chapters/index.snbt");
        write(index, "{index:[\"12345678\"],external_order:{hidden:[\"missing\"]}}");
        write(root.resolve("normal/chapters/12345678/60000001.snbt"), "{title:\"前置\"}");
        book.readDataFull(book.getFolder());
        Quest quest = book.getQuest(-2);
        NBTTagCompound definition = EditHistory.snapshot(quest);
        NBTTagList deps = JsonToNBT.getTagFromJson("{d:[{id:\"60000001\",line:{color:12}},{id:\"deadbeef\",addon:42L}]}").getTagList("d",10);
        definition.setTag("dependencies", deps);
        quest.readDataPreservingUnknown(definition);
        assertEquals(1, quest.dependencies.size());
        book.writeDataFull(book.getFolder());
        assertEquals(JsonToNBT.getTagFromJson("{hidden:[\"missing\"]}"), SNBT.read(index.toFile()).getCompoundTag("external_order"));
        FixtureFile second = new FixtureFile(book.getFolder());
        ServerQuestFile.INSTANCE = second;
        second.readDataFull(second.getFolder());
        assertEquals(deps, EditHistory.snapshot(second.getQuest(-2)).getTag("dependencies"));
        second.getQuest(-2).dependencies.clear();
        NBTTagList remaining = EditHistory.snapshot(second.getQuest(-2)).getTagList("dependencies",10);
        assertEquals(1, remaining.tagCount());
        assertEquals("deadbeef", remaining.getCompoundTagAt(0).getString("id"));
    }

    @Test void multiCopyRemapsDependenciesPreservesUnknownChildrenAndCopiesAllTextFiles() throws Exception
    {
        FixtureFile book = oldBook();
        Quest original = book.getQuest(-2);
        Quest dependent = new Quest(original.chapter);
        dependent.id = 0x60000001; dependent.x = 4; dependent.y = 3;
        dependent.dependencies.add(original.tasks.get(0));
        original.chapter.quests.add(dependent); book.refreshIDMap();
        NBTTagCompound clipboard = QuestCopies.capture(Arrays.asList(original, dependent));
        var created = QuestCopies.paste(original.chapter, clipboard, 10, 20);
        Quest copied = (Quest)created.get(0);
        Quest copiedDependent = created.stream().filter(o -> o instanceof Quest && o != copied).map(o -> (Quest)o).findFirst().orElseThrow();
        assertNotEquals(original.id, copied.id);
        assertNotEquals(original.tasks.get(0).id, copied.tasks.get(0).id);
        assertTrue(copiedDependent.dependencies.contains(copied.tasks.get(0)));
        assertFalse(copiedDependent.dependencies.contains(original.tasks.get(0)));
        assertEquals("uninstalled:task", copied.tasks.get(1).getTypeForNBT());
        assertEquals(9000000000L, EditHistory.snapshot(copied.tasks.get(1)).getLong("value"));
        assertEquals(Files.readString(root.resolve("normal/text/zh_cn/fffffffe.txt")), Files.readString(root.resolve("normal/text/zh_cn/" + copied.getCodeString() + ".txt")));
    }

    @Test void failedPasteRollsBackAllObjectsAndImages() throws Exception
    {
        FixtureFile book = oldBook();
        Chapter chapter = book.getQuest(-2).chapter;
        var before = new HashSet<>(book.getAllObjects());
        NBTTagCompound clipboard = QuestCopies.capture(List.of(book.getQuest(-2)));
        NBTTagList images = new NBTTagList();
        NBTTagCompound valid = new NBTTagCompound(); new ChapterImage(chapter).writeData(valid); images.appendTag(valid);
        NBTTagCompound invalid = valid.copy(); invalid.setDouble("x", Double.NaN); images.appendTag(invalid);
        clipboard.setTag("images", images);
        assertThrows(IllegalArgumentException.class, () -> QuestCopies.paste(chapter, clipboard, 0, 0));
        assertEquals(before, new HashSet<>(book.getAllObjects()));
        assertTrue(chapter.images.isEmpty());
    }

    @Test void nestedLootExtensionsAndUnresolvedRewardTablesSurviveTheWire() throws Exception
    {
        FixtureFile book = oldBook();
        var crate = new com.feed_the_beast.ftbquests.quest.loot.LootCrate(new com.feed_the_beast.ftbquests.quest.loot.RewardTable(book));
        crate.readData(JsonToNBT.getTagFromJson("{string_id:\"crate\",external:{x:1L},drops:{monster:2,other:[I;3,4]}}"));
        var buffer = io.netty.buffer.Unpooled.buffer();
        try
        {
            crate.writeNetData(new com.feed_the_beast.ftblib.lib.io.DataOut(buffer));
            var copy = new com.feed_the_beast.ftbquests.quest.loot.LootCrate(crate.table);
            copy.readNetData(new com.feed_the_beast.ftblib.lib.io.DataIn(buffer));
            NBTTagCompound output = new NBTTagCompound(); copy.writeData(output);
            assertEquals(1L, output.getCompoundTag("external").getLong("x"));
            assertArrayEquals(new int[]{3,4}, output.getCompoundTag("drops").getIntArray("other"));
            buffer.clear();
            RandomReward reward = new RandomReward(book.getQuest(-2)); reward.readDataPreservingUnknown(JsonToNBT.getTagFromJson("{table:9876}"));
            reward.writeNetData(new com.feed_the_beast.ftblib.lib.io.DataOut(buffer));
            RandomReward copied = new RandomReward(book.getQuest(-2)); copied.readNetData(new com.feed_the_beast.ftblib.lib.io.DataIn(buffer));
            assertEquals(9876, EditHistory.snapshot(copied).getInteger("table"));
            assertNull(copied.getTable()); assertFalse(copied.isAvailable());
        }
        finally { buffer.release(); }
    }

    @Test void compressedTeamDataIsAtomicBackedUpAndNeverTreatsCorruptionAsEmpty() throws Exception
    {
        Path path = root.resolve("teams/team.dat");
        NBTTagCompound first = JsonToNBT.getTagFromJson("{UID:-1234567,Data:{addon:{progress:9000000000L}}}");
        com.feed_the_beast.ftblib.lib.util.AtomicNBT.write(path.toFile(), first);
        NBTTagCompound second = first.copy(); second.setString("Title", "修改后");
        com.feed_the_beast.ftblib.lib.util.AtomicNBT.write(path.toFile(), second);
        assertEquals(second, com.feed_the_beast.ftblib.lib.util.AtomicNBT.read(path.toFile()));
        try (var backups = Files.list(path.getParent().resolve(".backups")))
        { assertEquals(first, com.feed_the_beast.ftblib.lib.util.AtomicNBT.read(backups.findFirst().orElseThrow().toFile())); }
        Files.writeString(path, "corrupt");
        assertThrows(java.io.UncheckedIOException.class, () -> com.feed_the_beast.ftblib.lib.util.AtomicNBT.read(path.toFile()));
        assertEquals("corrupt", Files.readString(path));
    }

    @Test void missingChapterGroupSurvivesRepeatedReadsAndNetwork() throws Exception
    {
        FixtureFile book = oldBook(); Chapter chapter = book.getChapter(0x12345678);
        NBTTagCompound tag = EditHistory.snapshot(chapter); tag.setString("group", "deadbeef");
        chapter.readDataPreservingUnknown(tag); chapter.readDataPreservingUnknown(EditHistory.snapshot(chapter));
        assertEquals("deadbeef", EditHistory.snapshot(chapter).getString("group"));
        var buffer = io.netty.buffer.Unpooled.buffer();
        try
        {
            chapter.writeNetData(new com.feed_the_beast.ftblib.lib.io.DataOut(buffer));
            Chapter copy = new Chapter(book); copy.readNetData(new com.feed_the_beast.ftblib.lib.io.DataIn(buffer));
            assertEquals(tag.getTag("group"), EditHistory.snapshot(copy).getTag("group"));
        }
        finally { buffer.release(); }
    }

    @Test void teamSaveRejectsSymbolicLinkWithoutChangingTheTarget() throws Exception
    {
        Path original = root.resolve("original.dat"), link = root.resolve("link.dat");
        NBTTagCompound data = JsonToNBT.getTagFromJson("{UID:42}");
        com.feed_the_beast.ftblib.lib.util.AtomicNBT.write(original.toFile(), data);
        Files.createSymbolicLink(link, original);
        assertThrows(java.io.UncheckedIOException.class, () -> com.feed_the_beast.ftblib.lib.util.AtomicNBT.write(link.toFile(), new NBTTagCompound()));
        assertEquals(data, com.feed_the_beast.ftblib.lib.util.AtomicNBT.read(original.toFile()));
    }

    @Test void incrementalCreationKeepsExistingIdsDefinitionsAndSiblingDisplayCaches()throws Exception
    {
        FixtureFile book=oldBook();Quest sibling=book.getQuest(-2);
        var before=EditHistory.snapshot(sibling);var title=QuestObjectBase.class.getDeclaredField("cachedTitle");title.setAccessible(true);
        String cached=new String("cached sibling title");title.set(sibling,cached);
        Quest added=new Quest(sibling.chapter);added.id=book.newID();added.onCreated();book.registerCreatedObject(added);
        assertSame(added,book.getQuest(added.id));assertSame(sibling,book.getQuest(-2));
        assertSame(cached,title.get(sibling));assertEquals(before,EditHistory.snapshot(sibling));
        var task=new com.feed_the_beast.ftbquests.quest.task.CheckmarkTask(added);task.id=book.newID();task.onCreated();book.registerCreatedObject(task);
        assertSame(task,book.getTask(task.id));assertNotEquals(added.id,task.id);assertSame(cached,title.get(sibling));
    }
    @Test void incrementalCreationCannotOverwriteAnExistingIdOrUseReservedIds()throws Exception
    {
        FixtureFile book=oldBook();Quest original=book.getQuest(-2);
        for(int id:new int[]{0,1,-2}){
            Quest duplicate=new Quest(original.chapter);duplicate.id=id;
            assertThrows(IllegalArgumentException.class,()->book.registerCreatedObject(duplicate));assertSame(original,book.getQuest(-2));
        }
        FixtureFile other=new FixtureFile(root.resolve("other").toFile());Quest foreign=new Quest(new Chapter(other));foreign.id=42;
        assertThrows(IllegalArgumentException.class,()->book.registerCreatedObject(foreign));assertNull(book.getBase(42));
    }
    @Test void incrementalCreationSurvivesFullSaveAndReloadWithUnknownLegacyFields()throws Exception
    {
        FixtureFile book=oldBook();Quest added=new Quest(book.getQuest(-2).chapter);added.id=book.newID();
        NBTTagCompound fields=new NBTTagCompound();fields.setString("title","新增");fields.setLong("addon",9_000_000_001L);added.readDataPreservingUnknown(fields);
        added.onCreated();book.registerCreatedObject(added);book.writeDataFull(book.getFolder());
        FixtureFile loaded=new FixtureFile(book.getFolder());loaded.readDataFull(loaded.getFolder());
        assertEquals(EditHistory.snapshot(added),EditHistory.snapshot(loaded.getQuest(added.id)));
        assertNotNull(loaded.getQuest(-2));
    }

    @Test void failedAutosaveRetainsDirtyEditsAndRetriesAfterBackoff() throws Exception
    {
        FixtureFile book = oldBook();
        book.getQuest(-2).title = "等待重试的中文任务";
        Path blocker = Files.createDirectory(root.resolve(".normal.transaction"));
        assertFalse(book.trySaveNow());
        assertTrue(book.shouldSave);
        Files.delete(blocker);
        book.retrySaveIfDue();
        assertTrue(book.shouldSave);
        assertEquals("任务", SNBT.read(root.resolve("normal/chapters/12345678/fffffffe.snbt").toFile()).getString("title"));
        book.clock += java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        book.retrySaveIfDue();
        assertFalse(book.shouldSave);
        FixtureFile reloaded = new FixtureFile(book.getFolder());
        reloaded.readDataFull(reloaded.getFolder());
        assertEquals("等待重试的中文任务", reloaded.getQuest(-2).title);
        assertEquals(EditHistory.snapshot(book.getQuest(-2)), EditHistory.snapshot(reloaded.getQuest(-2)));
    }

    @Test void explicitSaveFailureAlsoRetainsDirtyState() throws Exception
    {
        FixtureFile book = oldBook();
        Path blocker = Files.createDirectory(root.resolve(".normal.transaction"));
        assertThrows(RuntimeException.class, book::saveNow);
        assertTrue(book.shouldSave);
        Files.delete(blocker);
        book.saveNow();
        assertFalse(book.shouldSave);
    }

    static class FixtureFile extends ServerQuestFile
    {
        private final File directory;
        long clock = 1;
        @Override protected long saveTimeNanos() { return clock; }
        FixtureFile(File directory) { super(null); this.directory = directory; }
        @Override public File getFolder() { return directory; }
        @Override public Collection<ServerQuestData> getAllData() { return Collections.emptyList(); }
        @Override public void save() {}
    }
}
