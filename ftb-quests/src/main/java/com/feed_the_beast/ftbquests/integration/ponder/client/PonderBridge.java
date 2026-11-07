package com.feed_the_beast.ftbquests.integration.ponder.client;

import com.feed_the_beast.ftbquests.client.PonderLink;
import com.feed_the_beast.ftbquests.util.PonderTarget;
import net.createmod.ponder.foundation.*;
import net.createmod.ponder.foundation.ui.*;
import net.createmod.ponder.script.ScriptSceneRegistry;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import java.util.*;

/** Loaded only after the optional loader check; Ponder remains an independent compileOnly dependency. */
public final class PonderBridge
{
    private static boolean modern()
    {try{Class.forName("net.createmod.ponder.client.guide.Tutorials",false,PonderBridge.class.getClassLoader());return true;}catch(ClassNotFoundException ex){return false;}}
    static ItemStack item(ResourceLocation component)
    {Item item=Item.REGISTRY.getObject(component);return new ItemStack(item==null?Items.BOOK:item);}
    public static List<PonderLink.Option> options()throws ReflectiveOperationException
    {
        Map<String,PonderLink.Option> entries=new LinkedHashMap<>();boolean modern=modern();
        Map<ResourceLocation,String> titles=new HashMap<>();
        for(var definition:ScriptSceneRegistry.effectiveScenes())titles.put(definition.getSceneId(),definition.getTitle());
        for(var entry:PonderIndex.getSceneAccess().getRegisteredEntries())
        {
            ResourceLocation component=entry.getKey(),scene=entry.getValue().getDeclaredSceneId();ItemStack stack=item(component);
            entries.putIfAbsent(component.toString(),new PonderLink.Option(component.toString(),stack.getDisplayName()+" · "+PonderLink.tr("all"),stack));
            if(scene!=null){String title=modern?ModernPonderBridge.title(scene):"";if(title.isEmpty())title=titles.getOrDefault(scene,"");if(title.isEmpty()){title=PonderIndex.getLangAccess().getSpecific(scene,"header");if(title.startsWith("missing scene text:"))title=scene.toString();}String value=component+"#"+scene;entries.putIfAbsent(value,new PonderLink.Option(value,stack.getDisplayName()+" · "+title,stack));}
        }
        if(modern)ModernPonderBridge.addOptions(entries);
        return entries.values().stream().sorted(Comparator.comparing(PonderLink.Option::title).thenComparing(PonderLink.Option::value)).toList();
    }
    public static GuiScreen open(PonderTarget target,GuiScreen previous)throws ReflectiveOperationException
    {
        if(modern())return ModernPonderBridge.open(target,previous);
        List<PonderScene> scenes=PonderIndex.getSceneAccess().compile(new ResourceLocation(target.component()));
        // Ponder 1.4.1 has no public scene selector. Its constructor can open the requested scene directly.
        if(!target.scene().isEmpty())scenes=scenes.stream().filter(s->s.getId().toString().equals(target.scene())).toList();
        if(scenes.isEmpty())throw new IllegalArgumentException("Missing Ponder scene");
        return new ReturningPonderUI(scenes,previous);
    }
    private static final class ReturningPonderUI extends PonderUI
    {
        private final GuiScreen previous;
        ReturningPonderUI(List<PonderScene> scenes,GuiScreen previous){super(scenes);this.previous=previous;}
        @Override public void initGui(){super.initGui();for(var button:buttonList)if(button.id==6&&button instanceof PonderButton ponder)ponder.withCallback(()->mc.displayGuiScreen(previous));}
        @Override protected void keyTyped(char character,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(previous);else super.keyTyped(character,key);}
    }
}
