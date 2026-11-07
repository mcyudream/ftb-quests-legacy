package com.feed_the_beast.ftbquests.client;

import net.minecraft.nbt.NBTTagCompound;
import java.util.*;
import java.util.regex.Pattern;

public final class DescriptionData
{
    public static final Pattern VARIABLE=Pattern.compile("\\$\\{((persisted|player|kubejs):[a-zA-Z0-9_.-]+)}");
    private static final Set<String> KEYS=new LinkedHashSet<>();
    private static NBTTagCompound values=new NBTTagCompound();
    private static long requested;
    public static void watch(String[] lines)
    {
        KEYS.clear();
        for(String line:lines){var matcher=VARIABLE.matcher(line);while(matcher.find() && KEYS.size()<32)KEYS.add(matcher.group(1));}
        tick();
    }
    public static String get(String key){return values.hasKey(key)?values.getString(key):null;}
    public static void tick()
    {
        if(net.minecraft.client.Minecraft.getMinecraft().world==null){KEYS.clear();values=new NBTTagCompound();requested=0;return;}
        if(!KEYS.isEmpty() && System.currentTimeMillis()-requested>=1000 && com.feed_the_beast.ftblib.lib.client.ClientUtils.getCurrentGuiAs(com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree.class)!=null)
        {requested=System.currentTimeMillis();new com.feed_the_beast.ftbquests.net.MessageDescriptionData.Request(KEYS).sendToServer();}
    }
    public static void update(NBTTagCompound next)
    {
        if(values.equals(next))return;values=next;
        if(ClientQuestFile.INSTANCE!=null && ClientQuestFile.INSTANCE.questTreeGui!=null)ClientQuestFile.INSTANCE.questTreeGui.viewQuestPanel.refreshWidgets();
    }
}
