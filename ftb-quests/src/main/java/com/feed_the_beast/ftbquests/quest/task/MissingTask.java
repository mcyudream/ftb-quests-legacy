package com.feed_the_beast.ftbquests.quest.task;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import net.minecraft.nbt.NBTTagCompound;

/** An unavailable addon must never turn a task into a completable checkmark. */
public final class MissingTask extends Task
{
    public String originalType = "";

    public MissingTask(Quest quest) { super(quest); }
    @Override public TaskType getType() { return FTBQuestsTasks.MISSING; }
    @Override public String getTypeForNBT() { return originalType; }
    @Override public long getMaxProgress() { return 0; }
    @Override public boolean canStart(QuestData data) { return false; }
    @Override public TaskData createData(QuestData data) { return new TaskData<>(this, data); }
    @Override public String getAltTitle() { return new net.minecraft.util.text.TextComponentTranslation("ftbquests.task.unavailable",originalType).getUnformattedText(); }
    @Override public void readData(NBTTagCompound nbt) { super.readData(nbt); originalType = nbt.getString("type"); }
    @Override public void writeNetData(DataOut out) { super.writeNetData(out); out.writeString(originalType); }
    @Override public void readNetData(DataIn in) { super.readNetData(in); originalType = in.readString(); }
}
