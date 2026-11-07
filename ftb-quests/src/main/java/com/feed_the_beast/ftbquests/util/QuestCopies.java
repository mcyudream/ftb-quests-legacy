package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Copies definitions only; progress and claim records remain attached to their original IDs. */
public final class QuestCopies
{
    public static NBTTagCompound capture(Collection<? extends Movable> selection)
    {
        NBTTagCompound result = new NBTTagCompound();
        result.setInteger("ftbquests_clipboard", 1);
        NBTTagList quests = new NBTTagList(), images = new NBTTagList();
        double x = Double.POSITIVE_INFINITY, y = Double.POSITIVE_INFINITY;
        for (Movable object : selection)
        {
            NBTTagCompound tag = new NBTTagCompound();
            if (object instanceof Quest)
            {
                Quest quest = (Quest)object; quest.writeData(tag); tag.setInteger("source_uid", quest.id);
                NBTTagList tasks = new NBTTagList(), rewards = new NBTTagList();
                for (Task task : quest.tasks)
                { NBTTagCompound t = new NBTTagCompound(); task.writeData(t); t.setString("type", task.getTypeForNBT()); t.setInteger("source_uid", task.id); tasks.appendTag(t); }
                for (Reward reward : quest.rewards)
                { NBTTagCompound t = new NBTTagCompound(); reward.writeData(t); t.setString("type", reward.getTypeForNBT()); t.setInteger("source_uid", reward.id); rewards.appendTag(t); }
                tag.setTag("tasks", tasks); tag.setTag("rewards", rewards); quests.appendTag(tag);
            }
            else if (object instanceof ChapterImage) { ((ChapterImage)object).writeData(tag); images.appendTag(tag); }
            else continue;
            x = Math.min(x, object.getX()); y = Math.min(y, object.getY());
        }
        result.setTag("quests", quests); result.setTag("images", images);
        result.setDouble("origin_x", Double.isFinite(x) ? x : 0); result.setDouble("origin_y", Double.isFinite(y) ? y : 0);
        return result;
    }
    private static int fresh(QuestFile file, Set<Integer> reserved)
    { int value; do { value = file.newID(); } while (!reserved.add(value)); return value; }

    private static void remap(NBTTagCompound tag, Map<Integer, Integer> ids, QuestFile file)
    {
        NBTBase dependencies = tag.getTag("dependencies");
        if (dependencies instanceof NBTTagIntArray)
        {
            int[] array = ((NBTTagIntArray)dependencies).getIntArray().clone();
            for (int i = 0; i < array.length; i++) array[i] = ids.getOrDefault(array[i], array[i]);
            tag.setIntArray("dependencies", array);
        }
        else if (dependencies instanceof NBTTagList)
        {
            NBTTagList list = (NBTTagList)dependencies;
            for (int i = 0; i < list.tagCount(); i++)
            {
                if (list.get(i) instanceof NBTTagString)
                { int old = file.getID(list.getStringTagAt(i)); if (ids.containsKey(old)) list.set(i, new NBTTagString(QuestObjectBase.getCodeString(ids.get(old)))); }
                else if (list.get(i) instanceof NBTTagCompound)
                { NBTTagCompound child = list.getCompoundTagAt(i); int old = child.getTag("id") instanceof NBTTagString ? file.getID(child.getString("id")) : child.getInteger("id"); if (ids.containsKey(old)) { if (child.getTag("id") instanceof NBTTagString) child.setString("id", QuestObjectBase.getCodeString(ids.get(old))); else child.setInteger("id", ids.get(old)); } }
            }
        }
        if (tag.hasKey("dependency")) { int old = tag.getTag("dependency") instanceof NBTTagString ? file.getID(tag.getString("dependency")) : tag.getInteger("dependency"); if (ids.containsKey(old)) { if (tag.getTag("dependency") instanceof NBTTagString) tag.setString("dependency", QuestObjectBase.getCodeString(ids.get(old))); else tag.setInteger("dependency", ids.get(old)); } }
        for (String key : new String[]{"excluded_quests", "hidden_dependency_lines", "hover_dependency_lines"})
        {
            if (!tag.hasKey(key)) continue;
            int[] array = tag.getIntArray(key).clone();
            for (int i = 0; i < array.length; i++) array[i] = ids.getOrDefault(array[i], array[i]);
            tag.setIntArray(key, array);
        }
    }
    public static List<QuestObjectBase> paste(Chapter chapter, NBTTagCompound source, double x, double y)
    {
        return paste(chapter, source, x, y, true);
    }
    public static List<QuestObjectBase> paste(Chapter chapter, NBTTagCompound source, double x, double y, boolean copyExternalText)
    {
        QuestFile file = chapter.file;
        if (source.getInteger("ftbquests_clipboard") != 1 || !Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Invalid quest clipboard");
        NBTTagCompound copy = source.copy();
        NBTTagList list = copy.getTagList("quests", 10), images = copy.getTagList("images", 10);
        if (list.tagCount() + images.tagCount() > 256) throw new IllegalArgumentException("At most 256 canvas objects may be pasted at once");
        Map<String,String> elementIds=new HashMap<>();
        Map<Integer, Integer> ids = new HashMap<>(); Set<Integer> reserved = new HashSet<>();
        List<QuestObjectBase> created = new ArrayList<>(); List<Quest> quests = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++)
        {
            NBTTagCompound input = list.getCompoundTagAt(i);
            int old = input.getInteger("source_uid");
            if (ids.containsKey(old)) throw new IllegalArgumentException("Duplicate quest in clipboard");
            Quest quest = new Quest(chapter); quest.id = fresh(file, reserved); ids.put(old, quest.id); quests.add(quest); created.add(quest);
            for (String key : new String[]{"tasks", "rewards"})
            {
                NBTTagList children = input.getTagList(key, 10);
                if (children.tagCount() > 256) throw new IllegalArgumentException("Too many quest children");
                for (int j = 0; j < children.tagCount(); j++)
                {
                    NBTTagCompound child = children.getCompoundTagAt(j); int oldChild = child.getInteger("source_uid");
                    if (ids.containsKey(oldChild)) throw new IllegalArgumentException("Duplicate child in clipboard");
                    QuestObjectBase object = key.equals("tasks") ? TaskType.createTask(quest, child.getString("type")) : RewardType.createReward(quest, child.getString("type"));
                    object.id = fresh(file, reserved); ids.put(oldChild, object.id); if (object instanceof Task) quest.tasks.add((Task)object); else quest.rewards.add((Reward)object); created.add(object);
                }
            }
        }
        for(int i=0;i<images.tagCount();i++)
        {
            NBTTagCompound tag=images.getCompoundTagAt(i);String old=tag.getString("element_id");String fresh=java.util.UUID.randomUUID().toString();
            if(!old.isEmpty())elementIds.put(old,fresh);tag.setString("element_id",fresh);
        }
        for(int i=0;i<images.tagCount();i++) for(String key:new String[]{"line_start","line_end"})
        {
            NBTTagCompound tag=images.getCompoundTagAt(i);String ref=tag.getString(key);
            if(ref.startsWith("element:"))tag.setString(key,"element:"+elementIds.getOrDefault(ref.substring(8),ref.substring(8)));
            else if(ref.startsWith("quest:")){int old=file.getID(ref.substring(6));if(ids.containsKey(old))tag.setString(key,"quest:"+QuestObjectBase.getCodeString(ids.get(old)));}
        }
        double dx = x - copy.getDouble("origin_x"), dy = y - copy.getDouble("origin_y");
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) throw new IllegalArgumentException("Invalid clipboard origin");
        List<ChapterImage> addedImages = new ArrayList<>();
        List<java.nio.file.Path> copiedText = new ArrayList<>();
        chapter.quests.addAll(quests); file.refreshIDMap();
        try
        {
            for (int i = 0; i < quests.size(); i++)
            {
                Quest quest = quests.get(i); NBTTagCompound tag = list.getCompoundTagAt(i);
                remap(tag, ids, file); tag.removeTag("source_uid"); quest.readDataPreservingUnknown(tag);
                quest.x += dx; quest.y += dy;
                if (!Double.isFinite(quest.x) || !Double.isFinite(quest.y)) throw new IllegalArgumentException("Invalid quest position");
                for (String key : new String[]{"tasks", "rewards"})
                {
                    NBTTagList children = tag.getTagList(key, 10);
                    for (int j = 0; j < children.tagCount(); j++)
                    {
                        NBTTagCompound child = children.getCompoundTagAt(j); child.removeTag("source_uid"); remap(child, ids, file);
                        (key.equals("tasks") ? quest.tasks.get(j) : quest.rewards.get(j)).readDataPreservingUnknown(child);
                    }
                }
            }
            for (Quest quest : quests) if (!quest.verifyDependencies(false)) throw new IllegalArgumentException("Clipboard contains a dependency cycle");
            for (int i = 0; i < images.tagCount(); i++)
            {
                ChapterImage image = new ChapterImage(chapter); image.readData(images.getCompoundTagAt(i)); image.x += dx; image.y += dy;
                if (!Double.isFinite(image.x) || !Double.isFinite(image.y) || !Double.isFinite(image.width) || !Double.isFinite(image.height)) throw new IllegalArgumentException("Invalid image position");
                addedImages.add(image);
            }
            if (!file.isClient() && copyExternalText)
            {
                java.nio.file.Path text = file.getFolder().toPath().resolve("text");
                if (java.nio.file.Files.isDirectory(text)) try (var languages = java.nio.file.Files.list(text))
                {
                    for (var language : languages.filter(path -> java.nio.file.Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList())
                        for (var entry : ids.entrySet())
                        {
                            var original = language.resolve(QuestObjectBase.getCodeString(entry.getKey()) + ".txt");
                            if (!java.nio.file.Files.isRegularFile(original, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                            var target = language.resolve(QuestObjectBase.getCodeString(entry.getValue()) + ".txt");
                            java.nio.file.Files.copy(original, target); copiedText.add(target);
                        }
                }
            }
            chapter.images.addAll(addedImages);
            for (Quest quest : quests) for (Task task : quest.tasks)
            {
                for (QuestData data : file.getAllData()) data.createTaskData(task);
                if (task instanceof CustomTask) new com.feed_the_beast.ftbquests.events.CustomTaskEvent((CustomTask)task).post();
            }
            return created;
        }
        catch (Exception ex)
        {
            chapter.quests.removeAll(quests); chapter.images.removeAll(addedImages); file.refreshIDMap();
            for (var path : copiedText) try { java.nio.file.Files.deleteIfExists(path); } catch (java.io.IOException cleanup) { ex.addSuppressed(cleanup); }
            throw new IllegalArgumentException("Could not paste quest definitions", ex);
        }
    }
}
