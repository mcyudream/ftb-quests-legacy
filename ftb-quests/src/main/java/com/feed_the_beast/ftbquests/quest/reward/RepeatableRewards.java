package com.feed_the_beast.ftbquests.quest.reward;

import com.feed_the_beast.ftbquests.quest.Quest;

/** Legacy Quests Additions reward modes reset the containing quest after a claim. */
public final class RepeatableRewards
{
    public static final class All extends AllTableReward
    {
        public All(Quest quest){super(quest);}
        @Override public RewardType getType(){return FTBQuestsRewards.REPEATABLE_ALL;}
        @Override public boolean resetsQuestAfterClaim(){return true;}
    }
    public static final class Random extends RandomReward
    {
        public Random(Quest quest){super(quest);}
        @Override public RewardType getType(){return FTBQuestsRewards.REPEATABLE_RANDOM;}
        @Override public boolean resetsQuestAfterClaim(){return true;}
    }
    public static final class Choice extends ChoiceReward
    {
        public Choice(Quest quest){super(quest);}
        @Override public RewardType getType(){return FTBQuestsRewards.REPEATABLE_CHOICE;}
        @Override public boolean resetsQuestAfterClaim(){return true;}
    }
}
