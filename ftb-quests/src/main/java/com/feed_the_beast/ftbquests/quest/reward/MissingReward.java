package com.feed_the_beast.ftbquests.quest.reward;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public final class MissingReward extends Reward
{
    public String originalType = "";

    public MissingReward(Quest quest) { super(quest); }
    @Override public RewardType getType() { return FTBQuestsRewards.MISSING; }
    @Override public String getTypeForNBT() { return originalType; }
    @Override public boolean isAvailable() { return false; }
    @Override public String getAltTitle() { return new net.minecraft.util.text.TextComponentTranslation("ftbquests.reward.unavailable",originalType).getUnformattedText(); }
    @Override public void claim(EntityPlayerMP player, boolean notify) {}
    @Override public void readData(NBTTagCompound nbt) { super.readData(nbt); originalType = nbt.getString("type"); }
    @Override public void writeNetData(DataOut out) { super.writeNetData(out); out.writeString(originalType); }
    @Override public void readNetData(DataIn in) { super.readNetData(in); originalType = in.readString(); }
}
