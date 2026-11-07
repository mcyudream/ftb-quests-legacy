package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftbquests.net.*;
import com.feed_the_beast.ftbquests.quest.loot.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.*;
import java.util.*;

/** Collects notifications after the normal server reward calls. Never rolls or grants a reward itself. */
public final class RewardOpening
{
    public static final int MAX_ENTRIES=128;
    private static final ThreadLocal<Session> ACTIVE=new ThreadLocal<>();

    public static final class Entry
    {
        public final ItemStack stack;
        public final ITextComponent text;
        public final Icon icon;
        public final int tableId,index;
        public long count;
        private Entry(ItemStack stack,ITextComponent text,Icon icon,int tableId,int index,long count)
        {this.stack=stack.copy();if(!this.stack.isEmpty())this.stack.setCount(1);this.text=text;this.icon=icon;this.tableId=tableId;this.index=index;this.count=count;}
        public static Entry item(ItemStack stack,long count){return new Entry(stack,new TextComponentString(""),Icon.EMPTY,0,-1,count);}
        public static Entry text(ITextComponent text,Icon icon){return new Entry(ItemStack.EMPTY,text,icon,0,-1,1);}
        public static Entry reference(int tableId,int index){return new Entry(ItemStack.EMPTY,new TextComponentString(""),Icon.EMPTY,tableId,index,1);}
        public boolean same(Entry other)
        {
            if(!stack.isEmpty()||!other.stack.isEmpty())return !stack.isEmpty()&&!other.stack.isEmpty()&&ItemStack.areItemStacksEqualUsingNBTShareTag(stack,other.stack);
            return tableId==other.tableId&&index==other.index&&text.equals(other.text)&&icon.equals(other.icon);
        }
        public void write(DataOut out)
        {out.writeItemStack(stack);out.writeTextComponent(text);out.writeIcon(icon);out.writeInt(tableId);out.writeInt(index);out.writeVarLong(count);}
        public static Entry read(DataIn in)
        {
            ItemStack stack=in.readItemStack();ITextComponent text=in.readTextComponent();Icon icon=in.readIcon();int table=in.readInt(),index=in.readInt();long count=in.readVarLong();
            if(count<1)throw new IllegalArgumentException("Invalid opening reward count");
            return new Entry(stack,text,icon,table,index,count);
        }
    }
    public static final class Result
    {
        public final int tableId,boxes,color;
        public final List<Entry> entries;
        public final long omitted;
        public Result(int tableId,int boxes,int color,List<Entry> entries,long omitted)
        {
            if(boxes<1||entries.size()>MAX_ENTRIES||omitted<0)throw new IllegalArgumentException("Invalid opening result");
            this.tableId=tableId;this.boxes=boxes;this.color=color;this.entries=List.copyOf(entries);this.omitted=omitted;
        }
        public void write(DataOut out)
        {out.writeInt(tableId);out.writeVarInt(boxes);out.writeInt(color);out.writeVarInt(entries.size());for(Entry entry:entries)entry.write(out);out.writeVarLong(omitted);}
        public static Result read(DataIn in)
        {
            int table=in.readInt(),boxes=in.readVarInt(),color=in.readInt(),size=in.readVarInt();
            if(size<0||size>MAX_ENTRIES)throw new IllegalArgumentException("Opening result exceeds display limit");
            List<Entry> entries=new ArrayList<>(size);for(int i=0;i<size;i++)entries.add(Entry.read(in));
            return new Result(table,boxes,color,entries,in.readVarLong());
        }
    }
    public static final class Session implements AutoCloseable
    {
        private final EntityPlayerMP player;
        private final RewardTable table;
        private final int boxes;
        private final Session previous;
        private final List<Entry> entries=new ArrayList<>();
        private long omitted,notifications;
        private boolean closed;
        private Session(EntityPlayerMP player,RewardTable table,int boxes)
        {this.player=player;this.table=table;this.boxes=boxes;previous=ACTIVE.get();ACTIVE.set(this);}
        private void add(Entry entry)
        {
            notifications++;
            for(Entry current:entries)if(current.same(entry)){current.count+=entry.count;return;}
            if(entries.size()<MAX_ENTRIES)entries.add(entry);else omitted+=entry.count;
        }
        @Override public void close()
        {
            if(closed)return;closed=true;
            if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);
            if(previous!=null&&previous.player==player){for(Entry entry:entries)previous.add(entry);previous.omitted+=omitted;return;}
            int color=table.lootCrate==null?0xE8B75A:table.lootCrate.color.rgb();
            new MessageRewardOpening(new Result(table.id,boxes,color,entries,omitted)).sendTo(player);
        }
    }
    public static Session begin(EntityPlayerMP player,RewardTable table,int boxes)
    {return table==null?null:new Session(player,table,Math.max(1,boxes));}
    private static Session session(EntityPlayerMP player){Session value=ACTIVE.get();return value!=null&&value.player==player?value:null;}
    public static void item(EntityPlayerMP player,ItemStack stack,int count)
    {
        if(stack.isEmpty()||count<=0)return;
        Session session=session(player);
        if(session!=null)session.add(Entry.item(stack,count));else new MessageDisplayItemRewardToast(stack,count).sendTo(player);
    }
    public static void text(EntityPlayerMP player,int id,ITextComponent text,Icon icon)
    {
        Session session=session(player);
        if(session!=null)session.add(Entry.text(text,icon));else new MessageDisplayRewardToast(id,text,icon).sendTo(player);
    }
    public static void claim(EntityPlayerMP player,RewardTable table,WeightedReward selected,boolean notify)
    {
        Session session=session(player);long before=session==null?0:session.notifications;
        selected.reward.claim(player,notify);
        // Non-item rewards such as commands may have no toast. Reference the selected
        // definition without invoking its client title/icon methods on a server.
        if(notify&&session!=null&&session.notifications==before&&selected.reward.isAvailable()&&!(selected.reward instanceof ItemReward)&&!(selected.reward instanceof RandomReward))
            session.add(Entry.reference(table.id,table.rewards.indexOf(selected)));
    }
}
