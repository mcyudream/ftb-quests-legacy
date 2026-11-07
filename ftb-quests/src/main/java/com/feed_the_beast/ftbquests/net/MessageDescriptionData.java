package com.feed_the_beast.ftbquests.net;

import com.feed_the_beast.ftblib.lib.io.*;
import com.feed_the_beast.ftblib.lib.net.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.relauncher.*;
import java.util.*;

/** Sends only the current player's explicitly requested description variables. */
public final class MessageDescriptionData extends MessageToClient
{
    private NBTTagCompound values;
    public MessageDescriptionData() {}
    private MessageDescriptionData(NBTTagCompound values) { this.values=values; }
    @Override public NetworkWrapper getWrapper(){return FTBQuestsNetHandler.GENERAL;}
    @Override public void writeData(DataOut out){out.writeNBT(values);}
    @Override public void readData(DataIn in){values=in.readNBT();}
    @Override @SideOnly(Side.CLIENT) public void onMessage(){if(values!=null)com.feed_the_beast.ftbquests.client.DescriptionData.update(values);}

    public static final class Request extends MessageToServer
    {
        private static final Map<EntityPlayerMP,Long> LAST=new WeakHashMap<>();
        private List<String> keys;
        public Request() {}
        public Request(Collection<String> keys){this.keys=new ArrayList<>(keys);}
        @Override public NetworkWrapper getWrapper(){return FTBQuestsNetHandler.GENERAL;}
        @Override public void writeData(DataOut out){out.writeCollection(keys,DataOut.STRING);}
        @Override public void readData(DataIn in)
        {
            int count=in.readVarInt();if(count<0 || count>32)throw new IllegalArgumentException("Too many description variables");
            keys=new ArrayList<>();for(int i=0;i<count;i++)keys.add(in.readString());
        }
        @Override public void onMessage(EntityPlayerMP player)
        {
            long now=System.currentTimeMillis();if(now-LAST.getOrDefault(player,0L)<500)return;LAST.put(player,now);
            NBTTagCompound reply=new NBTTagCompound();
            for(String key:keys)
            {
                if(key.length()>128 || !key.matches("(persisted|player|kubejs):[a-zA-Z0-9_.-]+"))continue;
                int colon=key.indexOf(':');String kind=key.substring(0,colon);
                NBTBase value=kind.equals("persisted")?com.feed_the_beast.ftblib.lib.util.NBTUtils.getPersistedData(player,false):player.getEntityData();
                if(kind.equals("kubejs"))value=com.feed_the_beast.ftblib.lib.util.NBTUtils.getPersistedData(player,false).getCompoundTag("KubeJS");
                for(String part:key.substring(colon+1).split("\\."))value=value instanceof NBTTagCompound?((NBTTagCompound)value).getTag(part):null;
                String text=value instanceof NBTTagString?((NBTTagString)value).getString():value==null?"":value.toString();
                reply.setString(key,text.substring(0,Math.min(512,text.length())));
            }
            new MessageDescriptionData(reply).sendTo(player);
        }
    }
}
