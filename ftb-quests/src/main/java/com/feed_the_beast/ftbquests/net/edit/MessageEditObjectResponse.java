package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftblib.lib.net.MessageToClient;
import com.feed_the_beast.ftblib.lib.net.NetworkWrapper;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.client.ClientEditSync;
import com.feed_the_beast.ftbquests.integration.jei.FTBQuestsJEIHelper;
import com.feed_the_beast.ftbquests.quest.QuestObjectBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.UUID;

/**
 * @author LatvianModder
 */
public class MessageEditObjectResponse extends MessageToClient
{
	private int id;
	private NBTTagCompound nbt;
	private int parent;
	private UUID session = ClientEditSync.UNSOLICITED;
	private long revision;

	public MessageEditObjectResponse()
	{
	}

	public MessageEditObjectResponse(QuestObjectBase o)
	{
		this(o, ClientEditSync.UNSOLICITED, 0);
	}

	public MessageEditObjectResponse(QuestObjectBase o, UUID source, long sequence)
	{
		id = o.id;
		parent = o.getParentID();
		session = source;
		revision = sequence;
		nbt = new NBTTagCompound();
		o.writeData(nbt);
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
		data.writeInt(parent);
		data.writeUUID(session);
		data.writeLong(revision);
	}

	@Override
	public void readData(DataIn data)
	{
		id = data.readInt();
		nbt = data.readNBT();
		parent = data.readInt();
		session = data.readUUID();
		revision = data.readLong();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void onMessage()
	{
		if (!ClientQuestFile.exists()) return;
		ClientQuestFile file = ClientQuestFile.INSTANCE;
		if (!file.editSync.receive(id, session, revision)) return;
		QuestObjectBase object = file.getBase(id);

		if (object != null)
		{
			boolean changedParent = object instanceof com.feed_the_beast.ftbquests.quest.Quest && object.getParentID() != parent;
			if (!changedParent && com.feed_the_beast.ftbquests.util.EditHistory.snapshot(object).equals(nbt)) return;
			if (changedParent) ((com.feed_the_beast.ftbquests.quest.Quest)object).moved(nbt.getDouble("x"), nbt.getDouble("y"), parent);
			object.readDataPreservingUnknown(nbt);
			file.invalidateObject(object);
			object.editedFromGUI();
			FTBQuestsJEIHelper.refresh(object);
		}
	}
}
