package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToServer;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.integration.jei.FTBQuestsJEIHelper;
import com.feed_the_beast.ftbquests.quest.QuestObjectBase;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import com.feed_the_beast.ftbquests.client.ClientEditSync;
import java.util.UUID;

/**
 * @author LatvianModder
 */
public class MessageEditObject extends MessageToServer
{
	private int id;
	private NBTTagCompound nbt;
	private UUID session = ClientEditSync.UNSOLICITED;
	private long revision;

	public MessageEditObject()
	{
	}

	public static void send(QuestObjectBase object)
	{
		MessageEditObject message = new MessageEditObject(object);
		MessageEditPayload.send(1, message.id, 0, 0, message.nbt, message.session, message.revision);
	}

	static void apply(EntityPlayerMP player, int id, NBTTagCompound nbt, UUID session, long revision)
	{
		MessageEditObject message = new MessageEditObject();
		message.id = id; message.nbt = nbt; message.session = session; message.revision = revision; message.onMessage(player);
	}

	public MessageEditObject(QuestObjectBase o)
	{
		id = o.id;
		nbt = new NBTTagCompound();
		o.writeData(nbt);
		session = ClientQuestFile.INSTANCE.editSync.session;
		revision = ClientQuestFile.INSTANCE.editSync.begin(id);
		ClientQuestFile.INSTANCE.invalidateObject(o);
		o.editedFromGUI();
		FTBQuestsJEIHelper.refresh(o);
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
		data.writeNBT(nbt);
		data.writeUUID(session);
		data.writeLong(revision);
	}

	@Override
	public void readData(DataIn data)
	{
		id = data.readInt();
		nbt = data.readNBT();
		session = data.readUUID();
		revision = data.readLong();
	}

	@Override
	public void onMessage(EntityPlayerMP player)
	{
		ServerQuestFile file = ServerQuestFile.INSTANCE;
		if (file == null) return;
		QuestObjectBase object = file.getBase(id);
		if (object == null) return;
		if (!FTBQuests.canEdit(player))
		{
			new MessageEditObjectResponse(object, session, revision).sendTo(player);
			return;
		}
		NBTTagCompound before = com.feed_the_beast.ftbquests.util.EditHistory.snapshot(object);
		try
		{
			object.readDataPreservingUnknown(nbt);
			file.validateChapterGroups();
			if (object instanceof com.feed_the_beast.ftbquests.quest.Quest && !((com.feed_the_beast.ftbquests.quest.Quest)object).verifyDependencies(false))
				throw new IllegalArgumentException("Edit introduces a dependency loop");
		}
		catch (RuntimeException ex)
		{
			object.readDataPreservingUnknown(before);
			file.invalidateObject(object);
			new MessageEditObjectResponse(object, session, revision).sendTo(player);
			FTBQuests.LOGGER.warn("Rejected invalid quest edit", ex);
			return;
		}
		com.feed_the_beast.ftbquests.util.EditHistory.record(file, object, before);
		file.invalidateObject(object);
		file.save();
		new MessageEditObjectResponse(object, session, revision).sendToAll();
	}
}
