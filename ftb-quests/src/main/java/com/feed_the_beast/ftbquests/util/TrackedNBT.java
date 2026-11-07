package com.feed_the_beast.ftbquests.util;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.HashSet;
import java.util.Set;

/** Records which root fields a reader understands, leaving extension fields untouched. */
public final class TrackedNBT extends NBTTagCompound
{
    private final Set<String> consumed = new HashSet<>();

    public TrackedNBT(NBTTagCompound source)
    {
        for (String key : source.getKeySet()) setTag(key, source.getTag(key).copy());
    }

    public NBTTagCompound unknown(String... structural)
    {
        NBTTagCompound result = new NBTTagCompound();
        java.util.Collections.addAll(consumed, structural);
        for (String key : getKeySet()) if (!consumed.contains(key)) result.setTag(key, super.getTag(key).copy());
        return result;
    }

    @Override public NBTBase getTag(String key) { consumed.add(key); return super.getTag(key); }
    @Override public byte getTagId(String key) { consumed.add(key); return super.getTagId(key); }
    @Override public boolean hasKey(String key) { consumed.add(key); return super.hasKey(key); }
    @Override public boolean hasKey(String key, int type) { consumed.add(key); return super.hasKey(key, type); }
    @Override public byte getByte(String key) { consumed.add(key); return super.getByte(key); }
    @Override public short getShort(String key) { consumed.add(key); return super.getShort(key); }
    @Override public int getInteger(String key) { consumed.add(key); return super.getInteger(key); }
    @Override public long getLong(String key) { consumed.add(key); return super.getLong(key); }
    @Override public float getFloat(String key) { consumed.add(key); return super.getFloat(key); }
    @Override public double getDouble(String key) { consumed.add(key); return super.getDouble(key); }
    @Override public boolean getBoolean(String key) { consumed.add(key); return super.getBoolean(key); }
    @Override public String getString(String key) { consumed.add(key); return super.getString(key); }
    @Override public byte[] getByteArray(String key) { consumed.add(key); return super.getByteArray(key); }
    @Override public int[] getIntArray(String key) { consumed.add(key); return super.getIntArray(key); }
    @Override public NBTTagCompound getCompoundTag(String key) { consumed.add(key); return super.getCompoundTag(key); }
    @Override public NBTTagList getTagList(String key, int type) { consumed.add(key); return super.getTagList(key, type); }

    public static void mergeMissing(NBTTagCompound into, NBTTagCompound extras)
    {
        for (String key : extras.getKeySet()) if (!into.hasKey(key)) into.setTag(key, extras.getTag(key).copy());
    }
}
