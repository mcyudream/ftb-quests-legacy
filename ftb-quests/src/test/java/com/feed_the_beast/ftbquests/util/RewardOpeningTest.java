package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.io.*;
import io.netty.buffer.Unpooled;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardOpeningTest
{
    @BeforeAll static void bootstrap(){Bootstrap.register();}
    @Test void wireRoundTripKeepsMetadataLongCountsTextAndDefinitionReferences()
    {
        ItemStack original=new ItemStack(Items.DIAMOND,7);NBTTagCompound nbt=new NBTTagCompound();nbt.setString("opening_test","保留物品 NBT");original.setTagCompound(nbt);
        var result=new RewardOpening.Result(0x69000101,64,0x88ACFF,List.of(RewardOpening.Entry.item(original,4_000_000_001L),RewardOpening.Entry.text(new TextComponentTranslation("ftbquests.reward.ftbquests.xp"),Icon.EMPTY),RewardOpening.Entry.reference(0x69000102,9)),3);
        var buffer=Unpooled.buffer();try{
            result.write(new DataOut(buffer));var decoded=RewardOpening.Result.read(new DataIn(buffer));
            assertEquals(0,buffer.readableBytes());assertEquals(result.tableId,decoded.tableId);assertEquals(64,decoded.boxes);assertEquals(0x88ACFF,decoded.color);assertEquals(3,decoded.omitted);
            assertEquals(4_000_000_001L,decoded.entries.get(0).count);assertEquals(nbt,decoded.entries.get(0).stack.getTagCompound());assertEquals(1,decoded.entries.get(0).stack.getCount());
            assertEquals(result.entries.get(1).text,decoded.entries.get(1).text);assertEquals(0x69000102,decoded.entries.get(2).tableId);assertEquals(9,decoded.entries.get(2).index);assertEquals(7,original.getCount());
        }finally{buffer.release();}
    }
    @Test void aggregationDistinguishesItemTagsAndIgnoresSourceStackCount()
    {
        ItemStack one=new ItemStack(Items.DIAMOND,1),many=new ItemStack(Items.DIAMOND,64),tagged=one.copy();NBTTagCompound tag=new NBTTagCompound();tag.setString("variant","other");tagged.setTagCompound(tag);
        assertTrue(RewardOpening.Entry.item(one,1).same(RewardOpening.Entry.item(many,64)));
        assertFalse(RewardOpening.Entry.item(one,1).same(RewardOpening.Entry.item(tagged,1)));
        assertFalse(RewardOpening.Entry.item(one,1).same(RewardOpening.Entry.item(new ItemStack(Items.EMERALD),1)));
    }
    @Test void excessiveWireEntryCountIsRejectedBeforeAllocation()
    {
        var buffer=Unpooled.buffer();try{var out=new DataOut(buffer);out.writeInt(1);out.writeVarInt(1);out.writeInt(0);out.writeVarInt(RewardOpening.MAX_ENTRIES+1);
            assertThrows(IllegalArgumentException.class,()->RewardOpening.Result.read(new DataIn(buffer)));
        }finally{buffer.release();}
    }
    @Test void invalidBatchAndInvalidCountsCannotBecomeDisplayResults()
    {
        assertThrows(IllegalArgumentException.class,()->new RewardOpening.Result(1,0,0,List.of(),0));
        var buffer=Unpooled.buffer();try{RewardOpening.Entry.item(new ItemStack(Items.DIAMOND),0).write(new DataOut(buffer));assertThrows(IllegalArgumentException.class,()->RewardOpening.Entry.read(new DataIn(buffer)));}finally{buffer.release();}
    }
}
