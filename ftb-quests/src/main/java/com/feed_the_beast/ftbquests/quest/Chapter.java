package com.feed_the_beast.ftbquests.quest;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.config.ConfigString;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.icon.IconAnimation;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.util.ListUtils;
import com.feed_the_beast.ftbquests.events.ObjectCompletedEvent;
import com.feed_the_beast.ftbquests.net.MessageDisplayCompletionToast;
import com.feed_the_beast.ftbquests.util.ConfigQuestObject;
import com.feed_the_beast.ftbquests.util.SNBTTagCompound;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * @author LatvianModder
 */
public final class Chapter extends QuestObject
{
	public String backgroundImage = "", sidebarImage = "", backgroundShader = "";
	public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
	public boolean requireSequentialTasks = false;
	public boolean hideQuestDetailsUntilStartable = false;
	public ProgressionMode getProgressionMode() {
		return progressionMode == ProgressionMode.DEFAULT ? file.progressionMode : progressionMode;
	}
	public final QuestFile file;
	public final List<Quest> quests;
	public final List<String> subtitle;
	public boolean alwaysInvisible;
	public Chapter group;
	private net.minecraft.nbt.NBTBase unresolvedGroup;
	private int groupID()
	{
		if (group != null && !group.invalid) return group.id;
		if (unresolvedGroup instanceof NBTTagString) return file.getID(((NBTTagString)unresolvedGroup).getString());
		return unresolvedGroup instanceof net.minecraft.nbt.NBTPrimitive ? ((net.minecraft.nbt.NBTPrimitive)unresolvedGroup).getInt() : 0;
	}
	public String defaultQuestShape;
	public final List<ChapterImage> images;
	public final List<Integer> prerequisiteQuests = new ArrayList<>();
	private static final ThreadLocal<java.util.Set<Chapter>> CHECKING_PREREQUISITES = ThreadLocal.withInitial(java.util.HashSet::new);
	public boolean prerequisitesComplete(QuestData data)
	{
		if (prerequisiteQuests.isEmpty()) return true;
		if (data == null || !CHECKING_PREREQUISITES.get().add(this)) return false;
		try
		{
			for (int id : prerequisiteQuests)
			{ Quest quest = file.getQuest(id); if (quest == null || quest.chapter == this || !quest.isComplete(data)) return false; }
			return true;
		}
		finally { CHECKING_PREREQUISITES.get().remove(this); }
	}

	public Chapter(QuestFile f)
	{
		file = f;
		quests = new ArrayList<>();
		subtitle = new ArrayList<>(0);
		alwaysInvisible = false;
		group = null;
		defaultQuestShape = "";
		images = new ArrayList<>();
	}

	@Override
	public QuestObjectType getObjectType()
	{
		return QuestObjectType.CHAPTER;
	}

	@Override
	public QuestFile getQuestFile()
	{
		return file;
	}

	@Override
	public Chapter getQuestChapter()
	{
		return this;
	}

	@Override
	public void writeData(NBTTagCompound nbt)
	{
		super.writeData(nbt);
		if (!prerequisiteQuests.isEmpty()) nbt.setIntArray("chapter_prerequisites", prerequisiteQuests.stream().mapToInt(Integer::intValue).toArray());
        if(!backgroundImage.isEmpty()) nbt.setString("background_image",backgroundImage);
        if(!sidebarImage.isEmpty()) nbt.setString("sidebar_image",sidebarImage);
        if(!backgroundShader.isEmpty()) nbt.setString("background_shader",backgroundShader);
		if (progressionMode != ProgressionMode.DEFAULT) nbt.setString("progression_mode", progressionMode.getId());
		if (requireSequentialTasks) nbt.setBoolean("require_sequential_tasks", true);
		if (hideQuestDetailsUntilStartable) nbt.setBoolean("hide_quest_details_until_startable", true);

		if (!subtitle.isEmpty())
		{
			NBTTagList list = new NBTTagList();

			for (String v : subtitle)
			{
				list.appendTag(new NBTTagString(v));
			}

			nbt.setTag("description", list);
		}

		nbt.setBoolean("always_invisible", alwaysInvisible);
		if (group == null && unresolvedGroup != null) nbt.setTag("group", unresolvedGroup.copy());
		else nbt.setInteger("group", group != null && !group.invalid ? group.id : 0);
		nbt.setString("default_quest_shape", defaultQuestShape);

		if (!images.isEmpty())
		{
			NBTTagList list = new NBTTagList();

			for (ChapterImage image : images)
			{
				NBTTagCompound nbt1 = new SNBTTagCompound();
				image.writeData(nbt1);
				list.appendTag(nbt1);
			}

			nbt.setTag("images", list);
		}
	}

	@Override
	public void readData(NBTTagCompound nbt)
	{
		super.readData(nbt);
		prerequisiteQuests.clear(); for (int id : nbt.getIntArray("chapter_prerequisites")) prerequisiteQuests.add(id);
        backgroundImage=nbt.getString("background_image"); sidebarImage=nbt.getString("sidebar_image"); backgroundShader=nbt.getString("background_shader");
		progressionMode = ProgressionMode.NAME_MAP.get(nbt.getString("progression_mode"));
		requireSequentialTasks = nbt.getBoolean("require_sequential_tasks");
		hideQuestDetailsUntilStartable = nbt.getBoolean("hide_quest_details_until_startable");
		subtitle.clear();

		NBTTagList desc = nbt.getTagList("description", Constants.NBT.TAG_STRING);

		for (int i = 0; i < desc.tagCount(); i++)
		{
			subtitle.add(desc.getStringTagAt(i));
		}

		alwaysInvisible = nbt.getBoolean("always_invisible");
		unresolvedGroup = nbt.hasKey("group") ? nbt.getTag("group").copy() : null;
		group = null;
		group = file.getChapter(groupID());
		if (group != null) unresolvedGroup = null;
		defaultQuestShape = nbt.getString("default_quest_shape");

		if (defaultQuestShape.equals("default"))
		{
			defaultQuestShape = "";
		}

		NBTTagList imgs = nbt.getTagList("images", Constants.NBT.TAG_COMPOUND);

		images.clear();

		for (int i = 0; i < imgs.tagCount(); i++)
		{
			ChapterImage image = new ChapterImage(this);
			image.readData(imgs.getCompoundTagAt(i));
			images.add(image);
		}
	}

	@Override
	public void writeNetData(DataOut data)
	{
		super.writeNetData(data);
		data.writeVarInt(prerequisiteQuests.size()); for (int id : prerequisiteQuests) data.writeInt(id);
        data.writeString(backgroundImage); data.writeString(sidebarImage); data.writeString(backgroundShader);
		ProgressionMode.NAME_MAP.write(data, progressionMode);
		data.writeBoolean(requireSequentialTasks);
		data.writeBoolean(hideQuestDetailsUntilStartable);
		data.writeCollection(subtitle, DataOut.STRING);
		data.writeBoolean(alwaysInvisible);
		data.writeInt(group == null || group.invalid ? 0 : group.id);
		NBTTagCompound missingGroup = new NBTTagCompound();
		if (unresolvedGroup != null) missingGroup.setTag("group", unresolvedGroup);
		data.writeNBT(missingGroup);
		data.writeString(defaultQuestShape);
		data.writeCollection(images, (d, object) -> object.writeNetData(d));
	}

	@Override
	public void readNetData(DataIn data)
	{
		super.readNetData(data);
		int prerequisites = data.readVarInt(); if (prerequisites < 0 || prerequisites > 4096) throw new IllegalArgumentException("Too many chapter prerequisites");
		prerequisiteQuests.clear(); for (int i = 0; i < prerequisites; i++) prerequisiteQuests.add(data.readInt());
        backgroundImage=data.readString(); sidebarImage=data.readString(); backgroundShader=data.readString();
		progressionMode = ProgressionMode.NAME_MAP.read(data);
		requireSequentialTasks = data.readBoolean();
		hideQuestDetailsUntilStartable = data.readBoolean();
		data.readCollection(subtitle, DataIn.STRING);
		alwaysInvisible = data.readBoolean();
		group = file.getChapter(data.readInt());
		NBTTagCompound missingGroup = data.readNBT();
		unresolvedGroup = missingGroup == null ? null : missingGroup.getTag("group");
		defaultQuestShape = data.readString();
		data.readCollection(images, d -> {
			ChapterImage image = new ChapterImage(this);
			image.readNetData(d);
			return image;
		});
	}

	public int getIndex()
	{
		return file.chapters.indexOf(this);
	}

	@Override
	public int getRelativeProgressFromChildren(QuestData data)
	{
		if (alwaysInvisible)
		{
			return 100;
		}

		List<Chapter> children = getChildren();

		if (quests.isEmpty() && children.isEmpty())
		{
			return 100;
		}

		int progress = 0;
		int count = 0;

		for (Quest quest : quests)
		{
			if (!quest.isProgressionIgnored())
			{
				progress += quest.getRelativeProgress(data);
				count++;
			}
		}

		for (Chapter chapter : getChildren())
		{
			progress += chapter.getRelativeProgressFromChildren(data);
			count++;
		}

		if (count <= 0)
		{
			return 100;
		}

		return getRelativeProgressFromChildren(progress, count);
	}

	@Override
	public void onCompleted(QuestData data, List<EntityPlayerMP> onlineMembers, List<EntityPlayerMP> notifiedPlayers)
	{
		super.onCompleted(data, onlineMembers, notifiedPlayers);
		new ObjectCompletedEvent.ChapterEvent(data, this, onlineMembers, notifiedPlayers).post();

		if (!disableToast)
		{
			for (EntityPlayerMP player : notifiedPlayers)
			{
				new MessageDisplayCompletionToast(id).sendTo(player);
			}
		}

		for (Chapter chapter : file.chapters)
		{
			for (Quest quest : chapter.quests)
			{
				if (quest.dependencies.contains(this))
				{
					data.checkAutoCompletion(quest);
				}
			}
		}

		if (file.isComplete(data))
		{
			file.onCompleted(data, onlineMembers, notifiedPlayers);
		}
	}

	@Override
	public void changeProgress(QuestData data, ChangeProgress type)
	{
		for (Quest quest : quests)
		{
			quest.changeProgress(data, type);
		}

		for (Chapter chapter : getChildren())
		{
			chapter.changeProgress(data, type);
		}
	}

	@Override
	public Icon getAltIcon()
	{
		List<Icon> list = new ArrayList<>();

		for (Quest quest : quests)
		{
			list.add(quest.getIcon());
		}

		for (Chapter child : getChildren())
		{
			list.add(child.getIcon());
		}

		return IconAnimation.fromList(list, false);
	}

	@Override
	public String getAltTitle()
	{
		return I18n.format("ftbquests.unnamed");
	}

	@Override
	public void deleteSelf()
	{
		super.deleteSelf();
		file.chapters.remove(this);
	}

	@Override
	public void deleteChildren()
	{
		for (Quest quest : quests)
		{
			quest.deleteChildren();
			quest.invalid = true;
		}

		quests.clear();
	}

	@Override
	public void onCreated()
	{
		file.chapters.add(this);

		if (!quests.isEmpty())
		{
			for (Quest quest : ListUtils.clearAndCopy(quests))
			{
				quest.onCreated();
			}
		}
	}

	@Override
	public File getFile()
	{
		return new File(file.getFolder(), "chapters/" + getCodeString(this));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getConfig(ConfigGroup config)
	{
		super.getConfig(config);
        config.addString("background_image",()->backgroundImage,v->backgroundImage=v,"");
		Predicate<QuestObjectBase> prerequisite = object -> object instanceof Quest && ((Quest)object).chapter != this;
		config.addList("chapter_prerequisites", prerequisiteQuests,
			new ConfigQuestObject(file, 0, prerequisite),
			id -> new ConfigQuestObject(file, id, prerequisite), ConfigQuestObject::getObject);
        config.addString("sidebar_image",()->sidebarImage,v->sidebarImage=v,"");
        config.addString("background_shader",()->backgroundShader,v->backgroundShader=v,"");
		config.addEnum("progression_mode", () -> progressionMode, v -> progressionMode = v, ProgressionMode.NAME_MAP);
		config.addBool("require_sequential_tasks", () -> requireSequentialTasks, v -> requireSequentialTasks = v, false);
		config.addBool("hide_quest_details_until_startable", () -> hideQuestDetailsUntilStartable, v -> hideQuestDetailsUntilStartable = v, false);
		config.addList("subtitle", subtitle, new ConfigString(""), ConfigString::new, ConfigString::getString);
		config.addBool("always_invisible", () -> alwaysInvisible, v -> alwaysInvisible = v, false);

		Predicate<QuestObjectBase> predicate = object -> object == null || (object instanceof Chapter && object != this && ((Chapter) object).group == null);

		config.add("group", new ConfigQuestObject(file, groupID(), predicate)
		{
			@Override
			public int getObject()
			{
				return groupID();
			}

			@Override
			public void setObject(int v)
			{
				if (v == groupID()) return;
				unresolvedGroup = null;
				group = file.getChapter(v);
			}
		}, new ConfigQuestObject(file, 0, predicate));

		config.addEnum("default_quest_shape", () -> defaultQuestShape.isEmpty() ? "default" : defaultQuestShape, v -> defaultQuestShape = v.equals("default") ? "" : v, QuestShape.idMapWithDefault);
	}

	@Override
	public boolean isVisible(QuestData data)
	{
		if (!prerequisitesComplete(data)) return false;
		if (alwaysInvisible)
		{
			return false;
		}

		for (Quest quest : quests)
		{
			if (quest.isVisible(data))
			{
				return true;
			}
		}

		for (Chapter child : getChildren())
		{
			if (child.isVisible(data))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public void clearCachedData()
	{
		super.clearCachedData();

		for (Quest quest : quests)
		{
			quest.clearCachedData();
		}

		for (Chapter chapter : getChildren())
		{
			chapter.clearCachedData();
		}
	}

	public boolean hasChildren()
	{
		if (group != null)
		{
			return false;
		}

		for (Chapter chapter : file.chapters)
		{
			if (chapter.group == this)
			{
				return true;
			}
		}

		return false;
	}

	public List<Chapter> getChildren()
	{
		List<Chapter> list = Collections.emptyList();

		if (group != null)
		{
			return list;
		}

		for (Chapter chapter : file.chapters)
		{
			if (chapter.group == this)
			{
				if (list.isEmpty())
				{
					list = new ArrayList<>(3);
				}

				list.add(chapter);
			}
		}

		return list;
	}

	public boolean hasUnclaimedRewards(UUID player, QuestData data, boolean showExcluded)
	{
		for (Quest quest : quests)
		{
			if (quest.hasUnclaimedRewards(player, data, showExcluded))
			{
				return true;
			}
		}

		for (Chapter chapter : getChildren())
		{
			if (chapter.hasUnclaimedRewards(player, data, showExcluded))
			{
				return true;
			}
		}

		return false;
	}

	public int getUnclaimedRewards(UUID player, QuestData data, boolean showExcluded)
	{
		int r = 0;

		for (Quest quest : quests)
		{
			r += quest.getUnclaimedRewards(player, data, showExcluded);
		}

		for (Chapter chapter : getChildren())
		{
			r += chapter.getUnclaimedRewards(player, data, showExcluded);
		}

		return r;
	}

	@Override
	public boolean verifyDependenciesInternal(QuestObject original, boolean firstLoop)
	{
		if (this == original && !firstLoop)
		{
			return false;
		}

		for (Quest quest : quests)
		{
			if (!quest.verifyDependenciesInternal(original, false))
			{
				return false;
			}
		}

		return true;
	}

	public boolean hasGroup()
	{
		return group != null && !group.invalid;
	}

	public String getDefaultQuestShape()
	{
		return defaultQuestShape.isEmpty() ? file.getDefaultQuestShape() : defaultQuestShape;
	}
}
