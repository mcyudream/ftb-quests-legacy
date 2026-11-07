package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.gui.GuiWrapper;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.gui.*;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.util.RewardOpening;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

/** Queues results rather than replacing an inventory, editor, death or unrelated mod screen. */
@Mod.EventBusSubscriber(modid=FTBQuests.MOD_ID,value=Side.CLIENT)
public final class RewardOpeningClient
{
    private static final Deque<RewardOpening.Result> PENDING=new ArrayDeque<>();
    public static void receive(RewardOpening.Result result)
    {
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||mc.player==null)return;
        if(PENDING.size()>=16){for(var entry:result.entries){var key=GuiLootOpening.key(entry);mc.getToastGui().add(new RewardToast(key.title+" ×"+entry.count,key.icon));}return;}
        PENDING.addLast(result);showNext();
    }
    private static void showNext()
    {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||mc.world==null||!mc.player.isEntityAlive()||PENDING.isEmpty())return;
        if(mc.currentScreen!=null&&!(mc.currentScreen instanceof GuiWrapper wrapper&&wrapper.getGui() instanceof GuiQuestTree))return;
        mc.displayGuiScreen(new GuiLootOpening(PENDING.removeFirst(),mc.currentScreen));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END)return;
        if(Minecraft.getMinecraft().world==null)PENDING.clear();else showNext();
    }
    @SubscribeEvent public static void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event)
    {Minecraft.getMinecraft().addScheduledTask(PENDING::clear);}
}
