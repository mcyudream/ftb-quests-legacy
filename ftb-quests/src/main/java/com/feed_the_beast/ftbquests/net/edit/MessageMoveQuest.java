package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToServer;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import net.minecraft.entity.player.EntityPlayerMP;
import com.feed_the_beast.ftbquests.client.ClientEditSync;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.quest.Chapter;
import java.util.UUID;

/**
 * @author LatvianModder
 */
public class MessageMoveQuest extends MessageToServer
{
	private int id;
	private int chapter;
	private double x, y;
	private UUID session = ClientEditSync.UNSOLICITED;
	private long revision;

	public MessageMoveQuest()
	{
	}

	public MessageMoveQuest(int i, int c, double _x, double _y)
	{
		id = i;
		chapter = c;
		x = _x;
		y = _y;
	}

	@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
	public static void send(Quest quest, Chapter target, double x, double y)
	{
		if (!Double.isFinite(x) || !Double.isFinite(y) || target.invalid) return;
		ClientQuestFile file = ClientQuestFile.INSTANCE;
		MessageMoveQuest message = new MessageMoveQuest(quest.id, target.id, x, y);
		message.session = file.editSync.session;
		message.revision = file.editSync.begin(quest.id);
		boolean changedChapter = quest.chapter != target;
		quest.moved(x, y, target.id);
		if (changedChapter)
		{
			file.invalidateObject(quest);
			com.feed_the_beast.ftbquests.integration.jei.FTBQuestsJEIHelper.refresh(quest);
		}
		else file.clearCachedProgress();
		file.questTreeGui.questPanel.refreshForEdit();
		message.sendToServer();
	}

	@Override
	public NetworkWrapper getWrapper()
	{
		return FTBQuestsEditNetHandler.EDIT;
	}

	@Override
	public void writeData(DataOut data)
	{
		data.writeInt(id);
		data.writeInt(chapter);
		data.writeDouble(x);
		data.writeDouble(y);
		data.writeUUID(session);
		data.writeLong(revision);
	}

	@Override
	public void readData(DataIn data)
	{
		id = data.readInt();
		chapter = data.readInt();
		x = data.readDouble();
		y = data.readDouble();
		session = data.readUUID();
		revision = data.readLong();
	}

	@Override
	public void onMessage(EntityPlayerMP player)
	{
		ServerQuestFile file = ServerQuestFile.INSTANCE;
		if (file == null) return;
		Quest quest = file.getQuest(id);

		if (quest != null)
		{
			if (!com.feed_the_beast.ftbquests.FTBQuests.canEdit(player) || !Double.isFinite(x) || !Double.isFinite(y) || file.getChapter(chapter) == null)
			{
				new MessageEditObjectResponse(quest, session, revision).sendTo(player);
				return;
			}
			net.minecraft.nbt.NBTTagCompound before = com.feed_the_beast.ftbquests.util.EditHistory.snapshot(quest);
			boolean sameChapter = quest.chapter.id == chapter;
			quest.moved(x, y, chapter);
			if (sameChapter) com.feed_the_beast.ftbquests.util.EditHistory.record(ServerQuestFile.INSTANCE, quest, before);
			else com.feed_the_beast.ftbquests.util.EditHistory.clear(ServerQuestFile.INSTANCE);
			ServerQuestFile.INSTANCE.save();
			// Return the complete authoritative state so a move also reconciles earlier rejected edits.
			new MessageEditObjectResponse(quest, session, revision).sendToAll();
		}
	}
}
