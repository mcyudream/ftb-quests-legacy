package com.feed_the_beast.ftbquests.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** Uses 1.12 resource-pack bitmap fonts, keeping vanilla Unicode fallback. */
public final class QuestFonts
{
    private static final Map<String,FontRenderer> FONTS=new HashMap<>();
    public static FontRenderer get(String texture)
    {
        Minecraft mc=Minecraft.getMinecraft();
        if(texture.isEmpty())return mc.fontRenderer;
        return FONTS.computeIfAbsent(texture,key->{
            try
            {
                FontRenderer font=new FontRenderer(mc.gameSettings,new ResourceLocation(key),mc.getTextureManager(),false);
                font.onResourceManagerReload(mc.getResourceManager());return font;
            }
            catch(RuntimeException ex){com.feed_the_beast.ftbquests.FTBQuests.LOGGER.warn("Could not load quest font "+key,ex);return mc.fontRenderer;}
        });
    }
    public static void clear(){FONTS.clear();}
}
