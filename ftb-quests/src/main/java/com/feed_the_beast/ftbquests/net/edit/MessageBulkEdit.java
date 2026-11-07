package com.feed_the_beast.ftbquests.net.edit;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.EditHistory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

public final class MessageBulkEdit extends MessageToServer
{
    public static final Set<String> FIELDS = Set.of("shape", "size", "optional", "hide", "secret", "can_repeat", "hide_dependency_lines", "progression_mode", "require_sequential_tasks", "hide_details_until_startable", "or_tasks", "min_required_tasks", "background_image");
    private int[] ids; private NBTTagCompound patch;
    public MessageBulkEdit() {}
    public MessageBulkEdit(int[] ids, NBTTagCompound patch) { this.ids = ids; this.patch = patch; }
    @Override public NetworkWrapper getWrapper() { return FTBQuestsEditNetHandler.EDIT; }
    @Override public void writeData(DataOut out) { out.writeVarInt(ids.length); for (int id : ids) out.writeInt(id); out.writeNBT(patch); }
    @Override public void readData(DataIn in)
    { int count = in.readVarInt(); if (count < 0 || count > 256) throw new IllegalArgumentException("Too many quests"); ids = new int[count]; for (int i=0;i<count;i++) ids[i]=in.readInt(); patch=in.readNBT(); }
    @Override public void onMessage(EntityPlayerMP player)
    {
        if (!FTBQuests.canEdit(player) || patch == null || !FIELDS.containsAll(patch.getKeySet())) return;
        if (patch.hasKey("size") && (!Double.isFinite(patch.getDouble("size")) || patch.getDouble("size") < 0.0625 || patch.getDouble("size") > 8)) return;
        ServerQuestFile file = ServerQuestFile.INSTANCE; Map<Integer,NBTTagCompound> before = new LinkedHashMap<>();
        for (int id : ids)
        {
            Quest quest = file.getQuest(id); if (quest == null) return;
            before.put(id, EditHistory.snapshot(quest));
        }
        for (int id : before.keySet())
        { NBTTagCompound next = before.get(id).copy(); next.merge(patch); file.getQuest(id).readDataPreservingUnknown(next); }
        EditHistory.record(file, before); file.clearCachedData(); file.save();
        for (int id : before.keySet()) new MessageEditObjectResponse(file.getQuest(id)).sendToAll();
    }
}
