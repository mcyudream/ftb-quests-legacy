package com.feed_the_beast.ftbquests.integration.customnpcs;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.controllers.PlayerQuestController;

/**
 * @author LatvianModder
 */
public class NPCQuestTask extends Task
{
	public int npcQuest = 0;
	public boolean checkActive = false;

	public NPCQuestTask(Quest quest)
	{
		super(quest);
	}

	@Override
	public TaskType getType()
	{
		return CustomNPCsIntegration.QUEST_TASK;
	}

	@Override
	public void writeData(NBTTagCompound nbt)
	{
		super.writeData(nbt);
		nbt.setInteger("npc_quest", npcQuest);
		nbt.setBoolean("check_active", checkActive);
	}

	@Override
	public void readData(NBTTagCompound nbt)
	{
		super.readData(nbt);
		npcQuest = nbt.getInteger("npc_quest");
		checkActive = nbt.getBoolean("check_active");
	}

	@Override
	public void writeNetData(DataOut data)
	{
		super.writeNetData(data);
		data.writeVarInt(npcQuest);
		data.writeBoolean(checkActive);
	}

	@Override
	public void readNetData(DataIn data)
	{
		super.readNetData(data);
		npcQuest = data.readVarInt();
		checkActive = data.readBoolean();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getConfig(ConfigGroup config)
	{
		super.getConfig(config);
		config.addInt("id", () -> npcQuest, v -> npcQuest = v, 0, 0, Integer.MAX_VALUE);
		config.addBool("check_active", () -> checkActive, v -> checkActive = v, false);
	}

	@Override
	public TaskData createData(QuestData data)
	{
		return new Data(this, data);
	}

	public static class Data extends BooleanTaskData<NPCQuestTask>
	{
		private Data(NPCQuestTask task, QuestData data)
		{
			super(task, data);
		}

		@Override
		public boolean canSubmit(EntityPlayerMP player)
		{
			return task.npcQuest > 0 && (task.checkActive ? PlayerQuestController.isQuestActive(player, task.npcQuest) : PlayerQuestController.isQuestFinished(player, task.npcQuest));
		}
	}
}