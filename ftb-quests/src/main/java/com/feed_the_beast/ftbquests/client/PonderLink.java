package com.feed_the_beast.ftbquests.client;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.*;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.gui.*;
import com.feed_the_beast.ftbquests.integration.ponder.client.PonderBridge;
import com.feed_the_beast.ftbquests.util.PonderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import java.util.*;
import java.util.function.Consumer;

/** No Ponder types in fields or signatures: safe without the optional mod. */
public final class PonderLink
{
    public record Option(String value,String title,ItemStack item){}
    public static String tr(String key,Object... args){return I18n.format("ftbquests.ponder."+key,args);}
    public static boolean available(){return Loader.isModLoaded("ponder-legacy")||Loader.isModLoaded("ponder_legacy");}
    public static Icon icon(String value)
    {
        try{Item item=Item.REGISTRY.getObject(new ResourceLocation(PonderTarget.parse(value).component()));if(item!=null)return ItemIcon.getItemIcon(new ItemStack(item));}catch(IllegalArgumentException ignored){}
        return GuiIcons.INFO;
    }
    public static boolean open(String value)
    {
        PonderTarget target;
        try{target=PonderTarget.parse(value);}catch(IllegalArgumentException ex){notice("invalid");return true;}
        if(!available()){notice("missing");return true;}
        try{Minecraft mc=Minecraft.getMinecraft();mc.displayGuiScreen(PonderBridge.open(target,mc.currentScreen));}
        catch(IllegalArgumentException ex){notice("not_found",target.value());}
        catch(LinkageError|ReflectiveOperationException|RuntimeException ex){FTBQuests.LOGGER.warn("Could not open Ponder target "+target.value(),ex);notice("failed");}
        return true;
    }
    public static List<Option> options()
    {
        if(!available())return List.of();
        try{return PonderBridge.options();}
        catch(LinkageError|ReflectiveOperationException|RuntimeException ex){FTBQuests.LOGGER.warn("Could not enumerate optional Ponder tutorials",ex);return List.of();}
    }
    public static void choose(GuiBase parent,Consumer<Option> selected)
    {
        if(!available()){notice("missing");return;}
        GuiSelectPonder picker=new GuiSelectPonder(parent,options(),selected);
        picker.openGui();picker.focus();
    }
    private static void notice(String key,Object... args)
    {
        new GuiBase(){
            @Override public boolean onInit(){setSize(Math.min(360,getScreen().getScaledWidth()-16),130);return true;}
            @Override public void addWidgets(){add(new TextField(this).setMaxWidth(width-24).setText(tr(key,args)));add(new SimpleTextButton(this,I18n.format("gui.back"),Icon.EMPTY){@Override public void onClicked(MouseButton button){if(button.isLeft())closeGui(true);}});}
            @Override public void alignWidgets(){widgets.get(0).setPos(12,30);widgets.get(1).setPosAndSize(width/2-45,height-29,90,20);}
            @Override public void drawBackground(Theme theme,int x,int y,int w,int h){super.drawBackground(theme,x,y,w,h);theme.drawString(tr("title"),x+12,y+10,Theme.SHADOW);}
            @Override public Theme getTheme(){return FTBQuestsTheme.INSTANCE;}
        }.openGui();
    }
}
