package com.feed_the_beast.ftbquests.quest.task;

import com.feed_the_beast.ftblib.lib.client.ClientUtils;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.gui.WrappedIngredient;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.util.StringUtils;
import com.feed_the_beast.ftbquests.events.CustomTaskEvent;
import com.feed_the_beast.ftbquests.events.ObjectCompletedEvent;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.integration.jei.FTBQuestsJEIHelper;
import com.feed_the_beast.ftbquests.net.MessageDisplayCompletionToast;
import com.feed_the_beast.ftbquests.net.MessageSubmitTask;
import com.feed_the_beast.ftbquests.quest.ChangeProgress;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.QuestFile;
import com.feed_the_beast.ftbquests.quest.QuestObject;
import com.feed_the_beast.ftbquests.quest.QuestObjectType;
import com.feed_the_beast.ftbquests.tile.TileTaskScreenCore;
import com.feed_the_beast.ftbquests.tile.TileTaskScreenPart;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author LatvianModder
 */
public abstract class Task extends QuestObject
{
	public final Quest quest;
	public boolean optionalTask = false;
	public boolean invisible = false;

	@Override
	public void writeData(net.minecraft.nbt.NBTTagCompound nbt) {
		super.writeData(nbt);
		if (optionalTask) nbt.setBoolean("optional_task", true);
		if (invisible) nbt.setBoolean("invisible", true);
	}

	@Override
	public void readData(net.minecraft.nbt.NBTTagCompound nbt) {
		super.readData(nbt);
		optionalTask = nbt.getBoolean("optional_task");
		invisible = nbt.getBoolean("invisible");
	}

	@Override
	public void writeNetData(com.feed_the_beast.ftblib.lib.io.DataOut out) {
		super.writeNetData(out);
		out.writeBoolean(optionalTask);
		out.writeBoolean(invisible);
	}

	@Override
	public void readNetData(com.feed_the_beast.ftblib.lib.io.DataIn in) {
		super.readNetData(in);
		optionalTask = in.readBoolean();
		invisible = in.readBoolean();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getConfig(ConfigGroup config) {
		super.getConfig(config);
		config.addBool("optional_task", () -> optionalTask, v -> optionalTask = v, false).setDisplayName(new net.minecraft.util.text.TextComponentTranslation("ftbquests.task.optional_task"));
		config.addBool("invisible", () -> invisible, v -> invisible = v, false).setDisplayName(new net.minecraft.util.text.TextComponentTranslation("ftbquests.task.invisible"));
	}

	public boolean canStart(QuestData data) {
		if (!quest.canStartTasks(data)) return false;
		if (quest.requiresSequentialTasks()) {
			for (Task previous : quest.tasks) {
				if (previous == this) break;
				if (!previous.optionalTask && !previous.isComplete(data)) return false;
			}
		}
		return true;
	}

	public Task(Quest q)
	{
		quest = q;
	}

	@Override
	public final QuestObjectType getObjectType()
	{
		return QuestObjectType.TASK;
	}

	@Override
	public final QuestFile getQuestFile()
	{
		return quest.chapter.file;
	}

	@Override
	public final Chapter getQuestChapter()
	{
		return quest.chapter;
	}

	@Override
	public final int getParentID()
	{
		return quest.id;
	}

	public abstract TaskType getType();
	public String getTypeForNBT() { return getType().getTypeForNBT(); }

	public abstract TaskData createData(QuestData data);

	@Override
	public final int getRelativeProgressFromChildren(QuestData data)
	{
		return data.getTaskData(this).getRelativeProgress();
	}

	@Override
	public final void onCompleted(QuestData data, List<EntityPlayerMP> onlineMembers, List<EntityPlayerMP> notifiedPlayers)
	{
		super.onCompleted(data, onlineMembers, notifiedPlayers);
		new ObjectCompletedEvent.TaskEvent(data, this, onlineMembers, notifiedPlayers).post();
		boolean questComplete = quest.isComplete(data) && !data.completedQuests.contains(quest.id);

		if (quest.tasks.size() > 1 && !questComplete && !disableToast)
		{
			new MessageDisplayCompletionToast(id).sendTo(notifiedPlayers);
		}

		if (questComplete)
		{
			quest.onCompleted(data, onlineMembers, notifiedPlayers);
		}
	}

	public long getMaxProgress()
	{
		return 1L;
	}

	public String getMaxProgressString()
	{
		return StringUtils.formatDouble(getMaxProgress(), true);
	}

	@Override
	public final void changeProgress(QuestData data, ChangeProgress type)
	{
		if (type.reset) data.taskStates.put(id, new net.minecraft.nbt.NBTTagCompound());
		data.getTaskData(this).setProgress(type.reset ? 0L : getMaxProgress());
	}

	@Override
	public final void deleteSelf()
	{
		quest.tasks.remove(this);

		for (QuestData data : quest.chapter.file.getAllData())
		{
			data.removeTask(this);
		}

		super.deleteSelf();
	}

	@Override
	public final void deleteChildren()
	{
		for (QuestData data : quest.chapter.file.getAllData())
		{
			data.removeTask(this);
		}

		super.deleteChildren();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void editedFromGUI()
	{
		com.feed_the_beast.ftbquests.client.ClientQuestFile.INSTANCE.refreshObject(this);
	}

	@Override
	public final void onCreated()
	{
		quest.tasks.add(this);

		for (QuestData data : quest.chapter.file.getAllData())
		{
			data.createTaskData(this);
		}

		if (this instanceof CustomTask)
		{
			new CustomTaskEvent((CustomTask) this).post();
		}
	}

	@Override
	public Icon getAltIcon()
	{
		return getType().getIcon();
	}

	@Override
	public String getAltTitle()
	{
		return getType().getDisplayName();
	}

	@Override
	public final ConfigGroup createSubGroup(ConfigGroup group)
	{
		TaskType type = getType();
		return group.getGroup(getObjectType().getId()).getGroup(type.getRegistryName().getNamespace()).getGroup(type.getRegistryName().getPath());
	}

	public Class<? extends TileTaskScreenCore> getScreenCoreClass()
	{
		return TileTaskScreenCore.class;
	}

	public Class<? extends TileTaskScreenPart> getScreenPartClass()
	{
		return TileTaskScreenPart.class;
	}

	public TileTaskScreenCore createScreenCore(World world)
	{
		return new TileTaskScreenCore();
	}

	public TileTaskScreenPart createScreenPart(World world)
	{
		return new TileTaskScreenPart();
	}

	public void drawGUI(@Nullable TaskData data, int x, int y, int w, int h)
	{
		getIcon().draw(x, y, w, h);
	}

	public void drawScreen(@Nullable TaskData data)
	{
		getIcon().draw3D();
	}

	public boolean canInsertItem()
	{
		return false;
	}

	public boolean consumesResources()
	{
		return canInsertItem();
	}

	public boolean hideProgressNumbers()
	{
		return getMaxProgress() <= 1L;
	}

	@SideOnly(Side.CLIENT)
	public void addMouseOverText(List<String> list, @Nullable TaskData data)
	{
		if (consumesResources())
		{
			list.add("");
			list.add(TextFormatting.YELLOW.toString() + TextFormatting.UNDERLINE + I18n.format("ftbquests.task.click_to_submit"));
		}
	}

	@SideOnly(Side.CLIENT)
	public boolean addTitleInMouseOverText()
	{
		return true;
	}

	@SideOnly(Side.CLIENT)
	public void onButtonClicked(boolean canClick)
	{
		if (canClick && autoSubmitOnPlayerTick() <= 0)
		{
			GuiHelper.playClickSound();
			new MessageSubmitTask(id).sendToServer();
		}
	}

	public boolean submitItemsOnInventoryChange()
	{
		return false;
	}

	@Nullable
	public Object getIngredient()
	{
		if (addTitleInMouseOverText())
		{
			return getIcon().getIngredient();
		}

		return new WrappedIngredient(getIcon().getIngredient()).tooltip();
	}

	@Override
	public final int refreshJEI()
	{
		return FTBQuestsJEIHelper.QUESTS;
	}

	@SideOnly(Side.CLIENT)
	public String getButtonText()
	{
		return getMaxProgress() > 1L || consumesResources() ? getMaxProgressString() : "";
	}

	public int autoSubmitOnPlayerTick()
	{
		return 0;
	}

	@Override
	public boolean cacheProgress()
	{
		return false;
	}
}
