package com.feed_the_beast.ftbquests.quest.reward;

import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.loot.WeightedReward;
import net.minecraft.entity.player.EntityPlayerMP;

public class AllTableReward extends RandomReward
{
    public AllTableReward(Quest quest) { super(quest); }
    @Override public RewardType getType() { return FTBQuestsRewards.ALL_TABLE; }
    @Override public void claim(EntityPlayerMP player, boolean notify)
    {
        if (getTable() != null)
            for (WeightedReward entry : getTable().rewards) com.feed_the_beast.ftbquests.util.RewardOpening.claim(player,getTable(),entry,notify);
    }
}
