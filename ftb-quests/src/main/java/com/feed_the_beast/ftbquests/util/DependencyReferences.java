package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.quest.QuestFile;
import com.feed_the_beast.ftbquests.quest.QuestObject;
import com.feed_the_beast.ftbquests.quest.QuestObjectBase;
import net.minecraft.nbt.*;
import java.util.*;

/** Keeps unresolved references and per-reference metadata when editing an older book. */
public final class DependencyReferences
{
    private NBTBase original = new NBTTagList();
    private final Set<Integer> resolved = new HashSet<>();
    private boolean unresolved;

    private static int id(NBTBase value, QuestFile file)
    {
        if (value instanceof NBTTagString) return file.getID(((NBTTagString)value).getString());
        if (value instanceof NBTTagCompound) return ((NBTTagCompound)value).getTag("id") instanceof NBTTagString ? file.getID(((NBTTagCompound)value).getString("id")) : ((NBTTagCompound)value).getInteger("id");
        if (value instanceof NBTPrimitive) return ((NBTPrimitive)value).getInt();
        return 0;
    }

    private static List<NBTBase> entries(NBTBase tag)
    {
        List<NBTBase> list = new ArrayList<>();
        if (tag instanceof NBTTagIntArray)
            for (int value : ((NBTTagIntArray)tag).getIntArray()) list.add(new NBTTagInt(value));
        else if (tag instanceof NBTTagList)
            for (NBTBase value : (NBTTagList)tag) list.add(value);
        else throw new IllegalArgumentException("Unsupported dependency data: " + tag);
        return list;
    }

    public void read(NBTTagCompound nbt, QuestFile file, Set<QuestObject> into)
    {
        NBTBase tag = nbt.getTag("dependencies");
        if (tag == null) tag = nbt.hasKey("dependency") ? new NBTTagIntArray(new int[]{id(nbt.getTag("dependency"), file)}) : new NBTTagList();
        original = tag.copy();
        into.clear(); resolved.clear(); unresolved = false;
        for (NBTBase entry : entries(original))
        {
            int id = id(entry, file);
            QuestObject object = file.get(id);
            if (object == null) unresolved = true;
            else { into.add(object); resolved.add(id); }
        }
    }

    public boolean hasUnresolved() { return unresolved; }

    public NBTTagCompound write(QuestFile file, Set<QuestObject> current)
    {
        Set<Integer> ids = new LinkedHashSet<>();
        for (QuestObject object : current) if (!object.invalid) ids.add(object.id);
        List<NBTBase> kept = new ArrayList<>();
        Set<Integer> emitted = new HashSet<>();
        for (NBTBase entry : entries(original))
        {
            int id = id(entry, file);
            if (!resolved.contains(id) || ids.contains(id)) { kept.add(entry.copy()); emitted.add(id); }
        }
        int type = original instanceof NBTTagIntArray ? 3 : ((NBTTagList)original).getTagType();
        for (int id : ids) if (emitted.add(id))
        {
            if (type == 10) { NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("id", id); kept.add(tag); }
            else if (type == 3) kept.add(new NBTTagInt(id));
            else kept.add(new NBTTagString(QuestObjectBase.getCodeString(id)));
        }
        NBTTagCompound result = new NBTTagCompound();
        if (original instanceof NBTTagIntArray)
            result.setIntArray("dependencies", kept.stream().mapToInt(e -> ((NBTPrimitive)e).getInt()).toArray());
        else
        {
            NBTTagList list = new NBTTagList();
            for (NBTBase entry : kept) list.appendTag(entry);
            if (!list.isEmpty()) result.setTag("dependencies", list);
        }
        return result;
    }
}
