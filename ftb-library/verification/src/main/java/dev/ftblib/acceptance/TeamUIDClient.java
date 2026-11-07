package dev.ftblib.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;

@SideOnly(Side.CLIENT)
public final class TeamUIDClient
{
    private final Minecraft mc=Minecraft.getMinecraft();
    private int ticks,step,wait;private volatile int serverStep;private volatile Throwable failure;
    public static void install(){MinecraftForge.EVENT_BUS.register(new TeamUIDClient());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;
        ticks++;
        try{
            if(failure!=null)throw new AssertionError("Server checks",failure);
            if(ticks>1500)throw new AssertionError("Team UID timeout at step "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu){
                step=1;mc.launchIntegratedServer("team-uid-acceptance","Team UID",new WorldSettings(8,GameType.CREATIVE,false,false,WorldType.FLAT));
            }else if(step==1&&mc.player!=null&&ClientQuestFile.exists()){
                step=2;mc.getIntegratedServer().addScheduledTask(()->{
                    try{
                        if(System.getProperty("ftblib.acceptance.phase").equals("seed"))TeamUIDAcceptance.seed();
                        TeamUIDAcceptance.verify();TeamUIDAcceptance.saveAndVerify();
                        var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                    }catch(Throwable error){failure=error;}
                });
            }else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(TeamUIDAcceptance.QUEST)!=null){
                var file=ClientQuestFile.INSTANCE;
                file.questTreeGui.open(file.getQuest(TeamUIDAcceptance.QUEST),false);wait=ticks;step=3;
            }else if(step==3&&ticks-wait>15){
                ScreenShotHelper.saveScreenshot(mc.gameDir,"team-uid-progress.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                TeamUIDAcceptance.require(true,"quest_book_opened_after_team_load");
                mc.world.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.displayGuiScreen(new GuiMainMenu());wait=ticks;step=4;
            }else if(step==4&&ticks-wait>20&&!Universe.loaded()){
                TeamUIDAcceptance.require(true,"integrated_world_saved_and_closed");TeamUIDAcceptance.report("PASS",null);step=100;mc.shutdown();
            }
        }catch(Throwable error){TeamUIDAcceptance.report("FAIL",error);step=100;mc.shutdown();}
    }
}
