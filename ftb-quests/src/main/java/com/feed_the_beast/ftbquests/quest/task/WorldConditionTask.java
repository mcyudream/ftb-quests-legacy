package com.feed_the_beast.ftbquests.quest.task;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Native 1.12 implementations of day, elapsed-time and player-NBT conditions. */
public class WorldConditionTask extends Task
{
    public enum Kind { REACH_DAY, TIMER, PLAYER_NBT }
    public final Kind kind;
    public long value = 60;
    public boolean realTime = false;
    public String nbtPattern = "{}";

    public WorldConditionTask(Quest quest, Kind kind) { super(quest); this.kind = kind; }
    @Override public TaskType getType() { return kind == Kind.REACH_DAY ? FTBQuestsTasks.REACH_DAY : kind == Kind.TIMER ? FTBQuestsTasks.TIMER : FTBQuestsTasks.PLAYER_NBT; }
    @Override public long getMaxProgress() { return kind == Kind.PLAYER_NBT ? 1 : value; }
    @Override public int autoSubmitOnPlayerTick() { return 20; }
    @Override public TaskData createData(QuestData data) { return new Data(this, data); }
    @Override public void writeData(NBTTagCompound nbt)
    {
        super.writeData(nbt);
        nbt.setLong("value", value);
        nbt.setBoolean("real_time", realTime);
        if (kind == Kind.PLAYER_NBT) nbt.setString("nbt", nbtPattern);
    }
    @Override public void readData(NBTTagCompound nbt)
    {
        super.readData(nbt);
        value = nbt.hasKey("value") ? Math.max(1, nbt.getLong("value")) : 60;
        realTime = nbt.getBoolean("real_time");
        nbtPattern = nbt.hasKey("nbt") ? nbt.getString("nbt") : "{}";
    }
    @Override public void writeNetData(DataOut out) { super.writeNetData(out); out.writeVarLong(value); out.writeBoolean(realTime); out.writeString(nbtPattern); }
    @Override public void readNetData(DataIn in) { super.readNetData(in); value = in.readVarLong(); realTime = in.readBoolean(); nbtPattern = in.readString(); }
    @Override @SideOnly(Side.CLIENT) public void getConfig(ConfigGroup group)
    {
        super.getConfig(group);
        if (kind != Kind.PLAYER_NBT) group.addLong("value", () -> value, v -> value = v, 60, 1, Long.MAX_VALUE);
        if (kind == Kind.TIMER) group.addBool("real_time", () -> realTime, v -> realTime = v, false);
        if (kind == Kind.PLAYER_NBT) group.addString("nbt", () -> nbtPattern, v -> { try { JsonToNBT.getTagFromJson(v); nbtPattern = v; } catch (NBTException ex) { throw new IllegalArgumentException(ex); } }, "{}");
    }

    public static class Data extends TaskData<WorldConditionTask>
    {
        public Data(WorldConditionTask task, QuestData data) { super(task, data); }
        @Override public void submitTask(EntityPlayerMP player, ItemStack stack)
        {
            if (isComplete() || !task.canStart(data)) return;
            if (task.kind == Kind.REACH_DAY) { setProgress(Math.max(progress, player.world.getWorldTime() / 24000)); return; }
            if (task.kind == Kind.PLAYER_NBT)
            {
                try
                {
                    NBTTagCompound snapshot = player.writeToNBT(new NBTTagCompound());
                    if (NBTUtil.areNBTEquals(JsonToNBT.getTagFromJson(task.nbtPattern), snapshot, true)) setProgress(1);
                }
                catch (NBTException ex) { /* An invalid condition remains incomplete. */ }
                return;
            }
            NBTTagCompound state = data.getTaskState(task.id);
            long tick = player.world.getTotalWorldTime();
            if (state.hasKey("last_tick") && state.getLong("last_tick") == tick) return;
            state.setLong("last_tick", tick);
            if (task.realTime)
            {
                long now = System.currentTimeMillis();
                if (!state.hasKey("started_at")) state.setLong("started_at", now - Math.min(progress, now / 1000) * 1000);
                setProgress(Math.max(progress, Math.max(0, now - state.getLong("started_at")) / 1000));
            }
            else addProgress(1);
            data.markDirty();
        }
    }
}
