package com.feed_the_beast.ftbquests.integration.jei;

import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.reward.Reward;
import com.feed_the_beast.ftbquests.quest.reward.RewardAutoClaim;
import com.feed_the_beast.ftbquests.quest.task.Task;
import java.util.*;

public enum QuestRegistry
{
    INSTANCE;

    public final ArrayList<QuestWrapper> list = new ArrayList<>();
    private final Set<Integer> pending = new LinkedHashSet<>();
    private boolean rebuild;

    public void queue(QuestObjectBase object)
    {
        Quest quest = object instanceof Quest ? (Quest)object : object instanceof Task ? ((Task)object).quest
                : object instanceof Reward ? ((Reward)object).quest : null;
        if (quest != null && quest.getQuestFile().getQuest(quest.id) == quest) pending.add(quest.id);
        else if (quest != null && quest.invalid && quest.id != 0) pending.add(quest.id);
        else { rebuild = true; pending.clear(); }
    }

    public void flush()
    {
        if (rebuild) { rebuild = false; pending.clear(); refresh(); return; }
        long deadline = System.nanoTime() + 2_000_000L;
        int count = 0;
        Iterator<Integer> iterator = pending.iterator();
        while (iterator.hasNext())
        {
            int id = iterator.next(); iterator.remove(); refresh(id);
            if (++count >= 8 || System.nanoTime() >= deadline) break;
        }
    }

    @SuppressWarnings("deprecation")
    private void refresh(int id)
    {
        Iterator<QuestWrapper> iterator = list.iterator();
        while (iterator.hasNext())
        {
            QuestWrapper old = iterator.next();
            if (old.quest.id != id) continue;
            FTBQuestsJEIIntegration.runtime.getRecipeRegistry().removeRecipe(old, QuestCategory.UID);
            iterator.remove();
        }
        Quest quest = ClientQuestFile.exists() ? ClientQuestFile.INSTANCE.getQuest(id) : null;
        if (quest != null) add(quest);
    }

    private void add(Quest quest)
    {
        if (quest.rewards.isEmpty() || quest.disableJEI.get(ClientQuestFile.INSTANCE.defaultQuestDisableJEI)) return;
        List<Reward> rewards = new ArrayList<>();
        for (Reward reward : quest.rewards)
            if (reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE && reward.getIngredient() != null) rewards.add(reward);
        if (rewards.isEmpty()) return;
        // Item filters, item stacks and quest caches must be read on the client thread.
        QuestWrapper wrapper = new QuestWrapper(quest, rewards);
        list.add(wrapper);
        FTBQuestsJEIIntegration.runtime.getRecipeRegistry().addRecipe(wrapper, QuestCategory.UID);
    }

    @SuppressWarnings("deprecation")
    public void refresh()
    {
        if (FTBQuestsJEIIntegration.runtime == null) return;
        for (QuestWrapper wrapper : list)
            FTBQuestsJEIIntegration.runtime.getRecipeRegistry().removeRecipe(wrapper, QuestCategory.UID);
        list.clear();
        if (ClientQuestFile.exists())
            for (Chapter chapter : ClientQuestFile.INSTANCE.chapters)
                for (Quest quest : chapter.quests) add(quest);
    }
}
