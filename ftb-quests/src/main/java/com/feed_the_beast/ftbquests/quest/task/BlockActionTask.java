package com.feed_the_beast.ftbquests.quest.task;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockActionTask extends Task
{
    public enum Action { PLACE, BREAK, USE }
    public final Action action;
    public String block = "minecraft:stone";
    public String properties = "";
    public String replacedBlock = "";
    public String tool = "";
    public long value = 1;

    public BlockActionTask(Quest quest, Action action) { super(quest); this.action = action; }
    @Override public TaskType getType() { return action == Action.PLACE ? FTBQuestsTasks.PLACE_BLOCK : action == Action.BREAK ? FTBQuestsTasks.BREAK_BLOCK : FTBQuestsTasks.USE_BLOCK; }
    @Override public long getMaxProgress() { return value; }
    @Override public TaskData createData(QuestData data) { return new TaskData<>(this, data); }
    public static boolean matchesBlock(String selector, IBlockState state)
    {
        if (selector.isEmpty() || selector.equals("*")) return true;
        if (selector.startsWith("ore:"))
        {
            ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
            for (int id : OreDictionary.getOreIDs(stack)) if (OreDictionary.getOreName(id).equals(selector.substring(4))) return true;
            return false;
        }
        return selector.equals(String.valueOf(state.getBlock().getRegistryName()));
    }
    public boolean matches(IBlockState state, IBlockState replaced, ItemStack held)
    {
        if (!matchesBlock(block, state) || !matchesBlock(replacedBlock, replaced)) return false;
        if (!tool.isEmpty() && (held.isEmpty() || !tool.equals(String.valueOf(held.getItem().getRegistryName())))) return false;
        for (String property : properties.split(","))
        {
            if (property.trim().isEmpty()) continue;
            String[] pair = property.trim().split("=", 2);
            if (pair.length != 2) return false;
            boolean found = false;
            for (IProperty<?> key : state.getPropertyKeys())
                if (key.getName().equals(pair[0]) && String.valueOf(state.getValue(key)).equalsIgnoreCase(pair[1])) found = true;
            if (!found) return false;
        }
        return true;
    }
    @Override public void writeData(NBTTagCompound nbt)
    {
        super.writeData(nbt); nbt.setString("block", block); nbt.setString("properties", properties);
        nbt.setString("replaced_block", replacedBlock); nbt.setString("tool", tool); nbt.setLong("value", value);
    }
    @Override public void readData(NBTTagCompound nbt)
    {
        super.readData(nbt); block = nbt.hasKey("block") ? nbt.getString("block") : "minecraft:stone";
        properties = nbt.getString("properties"); replacedBlock = nbt.getString("replaced_block"); tool = nbt.getString("tool");
        value = nbt.hasKey("value") ? Math.max(1, nbt.getLong("value")) : 1;
    }
    @Override public void writeNetData(DataOut out) { super.writeNetData(out); out.writeString(block); out.writeString(properties); out.writeString(replacedBlock); out.writeString(tool); out.writeVarLong(value); }
    @Override public void readNetData(DataIn in) { super.readNetData(in); block = in.readString(); properties = in.readString(); replacedBlock = in.readString(); tool = in.readString(); value = in.readVarLong(); }
    @Override @SideOnly(Side.CLIENT) public void getConfig(ConfigGroup group)
    {
        super.getConfig(group); group.addString("block", () -> block, v -> block = v, "minecraft:stone");
        group.addString("properties", () -> properties, v -> properties = v, "");
        if (action == Action.PLACE) group.addString("replaced_block", () -> replacedBlock, v -> replacedBlock = v, "");
        group.addString("tool", () -> tool, v -> tool = v, "");
        group.addLong("value", () -> value, v -> value = v, 1, 1, Long.MAX_VALUE);
    }
}
