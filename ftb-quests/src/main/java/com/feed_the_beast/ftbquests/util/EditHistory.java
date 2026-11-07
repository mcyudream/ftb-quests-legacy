package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.quest.*;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** A single ordered history is shared by editors, so clients cannot replay stale edits. */
public final class EditHistory
{
    private static final Map<ServerQuestFile, EditHistory> BOOKS = new WeakHashMap<>();
    private final Deque<Entry> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private static final class Entry
    {
        final Map<Integer, NBTTagCompound> before, after;
        Entry(Map<Integer, NBTTagCompound> before, Map<Integer, NBTTagCompound> after) { this.before = before; this.after = after; }
    }
    public static NBTTagCompound snapshot(QuestObjectBase object)
    { NBTTagCompound tag = new NBTTagCompound(); object.writeData(tag); return tag; }
    public static void clear(ServerQuestFile file) { BOOKS.remove(file); }
    public static void record(ServerQuestFile file, QuestObjectBase object, NBTTagCompound before)
    { record(file, Collections.singletonMap(object.id, before)); }
    public static void record(ServerQuestFile file, Map<Integer, NBTTagCompound> before)
    {
        Map<Integer, NBTTagCompound> changed = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        for (var pair : before.entrySet())
        {
            QuestObjectBase object = file.getBase(pair.getKey());
            if (object == null) { clear(file); return; }
            NBTTagCompound current = snapshot(object);
            if (!current.equals(pair.getValue())) { changed.put(pair.getKey(), pair.getValue().copy()); after.put(pair.getKey(), current); }
        }
        if (changed.isEmpty()) return;
        EditHistory history = BOOKS.computeIfAbsent(file, ignored -> new EditHistory());
        history.undo.addLast(new Entry(changed, after)); history.redo.clear();
        while (history.undo.size() > 64) history.undo.removeFirst();
    }
    public static List<QuestObjectBase> restore(ServerQuestFile file, boolean forward)
    {
        EditHistory history = BOOKS.get(file);
        if (history == null) return Collections.emptyList();
        Deque<Entry> from = forward ? history.redo : history.undo, to = forward ? history.undo : history.redo;
        if (from.isEmpty()) return Collections.emptyList();
        Entry entry = from.peekLast();
        Map<Integer, NBTTagCompound> expected = forward ? entry.before : entry.after, target = forward ? entry.after : entry.before;
        List<QuestObjectBase> objects = new ArrayList<>();
        for (var pair : expected.entrySet())
        {
            QuestObjectBase object = file.getBase(pair.getKey());
            if (object == null || !snapshot(object).equals(pair.getValue()))
            { clear(file); throw new IllegalStateException("Quest book changed after this edit; history was cleared"); }
            objects.add(object);
        }
        try
        {
            for (QuestObjectBase object : objects) object.readDataPreservingUnknown(target.get(object.id));
            file.validateChapterGroups();
            for (QuestObjectBase object : file.getAllObjects())
                if (object instanceof Quest && !((Quest)object).verifyDependencies(false)) throw new IllegalStateException("Edit introduces a dependency loop");
        }
        catch (RuntimeException ex)
        { for (QuestObjectBase object : objects) object.readDataPreservingUnknown(expected.get(object.id)); throw ex; }
        file.clearCachedData(); file.save(); from.removeLast(); to.addLast(entry);
        return objects;
    }
}
