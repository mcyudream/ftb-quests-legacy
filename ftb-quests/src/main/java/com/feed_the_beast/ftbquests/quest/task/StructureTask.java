package com.feed_the_beast.ftbquests.quest.task;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftbquests.quest.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.*;
import java.nio.file.*;
import java.util.*;

public final class StructureTask extends Task
{
    public String structure = "";
    public boolean checkAir = false, checkTileNBT = false, customPicture = false;
    public NBTTagCompound template = new NBTTagCompound();
    public StructureTask(Quest quest) { super(quest); }
    @Override public TaskType getType() { return FTBQuestsTasks.STRUCTURE; }
    @Override public TaskData createData(QuestData data)
    {
        return new BooleanTaskData<StructureTask>(this,data)
        {
            @Override public boolean canSubmit(EntityPlayerMP player)
            {
                NBTTagCompound state=data.getTaskState(id);
                if(!state.hasKey("origin") || state.getInteger("dimension")!=player.dimension) return false;
                BlockPos origin=BlockPos.fromLong(state.getLong("origin"));
                return player.getDistanceSq(origin)<=128*128 && matches(player.world,origin);
            }
        };
    }
    public Map<BlockPos,IBlockState> blocks()
    {
        NBTTagList palette=template.getTagList("palette",10), entries=template.getTagList("blocks",10);
        if(palette.tagCount()>4096 || entries.tagCount()>4096) throw new IllegalArgumentException("Structure exceeds 4096 blocks");
        List<IBlockState> states=new ArrayList<>(); for(int i=0;i<palette.tagCount();i++)
        {
            NBTTagCompound tag=palette.getCompoundTagAt(i);
            if(Block.getBlockFromName(tag.getString("Name"))==null) throw new IllegalArgumentException("Unknown structure block: "+tag.getString("Name"));
            states.add(NBTUtil.readBlockState(tag));
        }
        Map<BlockPos,IBlockState> result=new LinkedHashMap<>();
        for(int i=0;i<entries.tagCount();i++)
        {
            NBTTagCompound entry=entries.getCompoundTagAt(i); NBTTagList pos=entry.getTagList("pos",3); int index=entry.getInteger("state");
            if(pos.tagCount()!=3 || index<0 || index>=states.size()) throw new IllegalArgumentException("Invalid structure block");
            BlockPos offset=new BlockPos(pos.getIntAt(0),pos.getIntAt(1),pos.getIntAt(2));
            if(Math.abs((long)offset.getX())>128 || Math.abs((long)offset.getY())>256 || Math.abs((long)offset.getZ())>128) throw new IllegalArgumentException("Structure is too large");
            IBlockState state=states.get(index); if(checkAir || state.getBlock()!=net.minecraft.init.Blocks.AIR) result.put(offset,state);
        }
        return result;
    }
    public boolean matches(World world, BlockPos origin)
    {
        Map<BlockPos,IBlockState> blocks;
        try { blocks=blocks(); } catch(IllegalArgumentException ex) { return false; }
        if(blocks.isEmpty()) return false;
        for(var block:blocks.entrySet())
        {
            BlockPos pos=origin.add(block.getKey());
            if(!world.isBlockLoaded(pos) || !world.getBlockState(pos).equals(block.getValue())) return false;
        }
        if(checkTileNBT)
        {
            NBTTagList entries=template.getTagList("blocks",10);
            for(int i=0;i<entries.tagCount();i++)
            {
                NBTTagCompound entry=entries.getCompoundTagAt(i); if(!entry.hasKey("nbt")) continue;
                NBTTagList p=entry.getTagList("pos",3); var tile=world.getTileEntity(origin.add(p.getIntAt(0),p.getIntAt(1),p.getIntAt(2)));
                if(tile==null) return false;
                NBTTagCompound expected=entry.getCompoundTag("nbt").copy(); expected.removeTag("x");expected.removeTag("y");expected.removeTag("z");
                if(!NBTUtil.areNBTEquals(expected,tile.writeToNBT(new NBTTagCompound()),true)) return false;
            }
        }
        return true;
    }
    @Override public void writeData(NBTTagCompound nbt)
    { super.writeData(nbt); if(customPicture)nbt.setBoolean("custom_picture",true); nbt.setString("structure",structure); if(checkAir) nbt.setBoolean("check_air",true); if(checkTileNBT) nbt.setBoolean("check_tile_nbt",true); if(!template.isEmpty()) nbt.setTag("template",template.copy()); }
    @Override public void readData(NBTTagCompound nbt)
    {
        super.readData(nbt); customPicture=nbt.getBoolean("custom_picture"); structure=nbt.getString("structure"); checkAir=nbt.getBoolean("check_air");checkTileNBT=nbt.getBoolean("check_tile_nbt");template=nbt.getCompoundTag("template").copy();
        if(!getQuestFile().isClient() && structure.matches("[a-zA-Z0-9_/-]+") && !structure.contains(".."))
        {
            Path path=getQuestFile().getFolder().toPath().resolve("structures").resolve(structure+".nbt");
            if(!Files.exists(path)) path=Paths.get("Quests_structures").resolve(structure+".nbt");
            if(Files.isRegularFile(path)) try(var input=Files.newInputStream(path)) { template=CompressedStreamTools.readCompressed(input); }
            catch(java.io.IOException ex) { throw new java.io.UncheckedIOException("Cannot read quest structure "+path,ex); }
        }
        if(!template.isEmpty()) blocks();
    }
    @Override public void writeNetData(DataOut out) { super.writeNetData(out);out.writeBoolean(customPicture);out.writeString(structure);out.writeBoolean(checkAir);out.writeBoolean(checkTileNBT);out.writeNBT(template); }
    @Override public void readNetData(DataIn in) { super.readNetData(in);customPicture=in.readBoolean();structure=in.readString();checkAir=in.readBoolean();checkTileNBT=in.readBoolean();template=in.readNBT(); }
    @Override @SideOnly(Side.CLIENT) public void getConfig(ConfigGroup config)
    { super.getConfig(config);config.addBool("custom_picture",()->customPicture,v->customPicture=v,false);config.addString("structure",()->structure,v->structure=v,"");config.addBool("check_air",()->checkAir,v->checkAir=v,false);config.addBool("check_tile_nbt",()->checkTileNBT,v->checkTileNBT=v,false); }
    @Override @SideOnly(Side.CLIENT) public com.feed_the_beast.ftblib.lib.icon.Icon getAltIcon(){return customPicture?com.feed_the_beast.ftblib.lib.icon.Icon.getIcon("questsadditions:textures/structure_icons/"+structure+".png"):super.getAltIcon();}
    @Override @SideOnly(Side.CLIENT) public void onButtonClicked(boolean canClick)
    { if(canClick) com.feed_the_beast.ftbquests.client.StructurePreview.select(this); }
}
