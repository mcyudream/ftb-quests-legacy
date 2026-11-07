package com.feed_the_beast.ftbquests.quest.task;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class AdvancedKillTask extends KillTask
{
    public String entityNbt = "{}";
    public String heldItem = "";
    public String heldItemNbt = "{}";
    public AdvancedKillTask(Quest quest) { super(quest); }
    @Override public TaskType getType() { return FTBQuestsTasks.ADVANCED_KILL; }
    @Override public boolean matchesKill(EntityLivingBase victim, EntityPlayerMP player)
    {
        if (!super.matchesKill(victim, player)) return false;
        ItemStack stack = player.getHeldItemMainhand();
        if (!heldItem.isEmpty() && (stack.isEmpty() || !heldItem.equals(String.valueOf(stack.getItem().getRegistryName())))) return false;
        try
        {
            return NBTUtil.areNBTEquals(JsonToNBT.getTagFromJson(entityNbt), victim.writeToNBT(new NBTTagCompound()), true)
                    && NBTUtil.areNBTEquals(JsonToNBT.getTagFromJson(heldItemNbt), stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound(), true);
        }
        catch (NBTException ex) { return false; }
    }
    @Override public void writeData(NBTTagCompound nbt) { super.writeData(nbt); nbt.setString("entity_nbt", entityNbt); nbt.setString("held_item", heldItem); nbt.setString("held_item_nbt", heldItemNbt); }
    @Override public void readData(NBTTagCompound nbt) { super.readData(nbt); entityNbt = nbt.hasKey("entity_nbt") ? nbt.getString("entity_nbt") : "{}"; heldItem = nbt.getString("held_item"); heldItemNbt = nbt.hasKey("held_item_nbt") ? nbt.getString("held_item_nbt") : "{}"; }
    @Override public void writeNetData(DataOut out) { super.writeNetData(out); out.writeString(entityNbt); out.writeString(heldItem); out.writeString(heldItemNbt); }
    @Override public void readNetData(DataIn in) { super.readNetData(in); entityNbt = in.readString(); heldItem = in.readString(); heldItemNbt = in.readString(); }
    @Override @SideOnly(Side.CLIENT) public void getConfig(ConfigGroup group)
    {
        super.getConfig(group); group.addString("entity_nbt", () -> entityNbt, v -> entityNbt = v, "{}");
        group.addString("held_item", () -> heldItem, v -> heldItem = v, "");
        group.addString("held_item_nbt", () -> heldItemNbt, v -> heldItemNbt = v, "{}");
    }
}
