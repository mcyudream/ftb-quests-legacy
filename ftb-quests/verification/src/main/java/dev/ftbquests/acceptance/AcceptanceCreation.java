package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.events.ClearFileCacheEvent;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.integration.jei.QuestRegistry;
import com.feed_the_beast.ftbquests.net.edit.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.GsonBuilder;
import io.netty.channel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual create packets and first rendered canvas frame with a large book and real JEI installed. */
@SideOnly(Side.CLIENT)
public final class AcceptanceCreation
{
    private static final int CHAPTER=0x6F000001,FIRST=0x6F100000,COUNT=2000;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Object> metrics=new LinkedHashMap<>();
    private final List<Map<String,Object>> samples=new ArrayList<>();
    private final AtomicInteger clientClears=new AtomicInteger(),serverClears=new AtomicInteger();
    private volatile Throwable failure;private volatile int serverStep;
    private volatile long firstResponse;private long sent,rendered;
    private final List<Double> clientHandlers=new ArrayList<>();
    private int step,ticks,wait,sample,clientBefore,serverBefore;private double px,py;
    private GuiQuestTree gui;private Quest sentinel;private Object sentinelIcon,sentinelTitle,sentinelButton,sentinelRecipe;
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceCreation());}
    @SubscribeEvent public void clear(ClearFileCacheEvent event)
    {if(event.getFile().isClient())clientClears.incrementAndGet();else serverClears.incrementAndGet();}
    private Quest created(){return ClientQuestFile.INSTANCE.getChapter(CHAPTER).quests.stream().filter(q->q.x==px&&q.y==py).findFirst().orElse(null);}
    private Object button(Quest quest){return gui.questPanel.widgets.stream().filter(w->w instanceof ButtonQuest q&&q.quest==quest).findFirst().orElse(null);}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step!=4||rendered!=0)return;
        Quest quest=created();if(quest!=null&&button(quest)!=null&&(sample==2||!quest.tasks.isEmpty()))rendered=System.nanoTime();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;ticks++;
        try{
            if(failure!=null)throw new AssertionError("Server",failure);
            if(ticks>3000)throw new AssertionError("Creation timeout at "+step+" sample "+sample);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu){step=1;mc.launchIntegratedServer("create-latency","Creation latency",new WorldSettings(9,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1&&mc.player!=null&&ClientQuestFile.exists()){
                step=2;mc.getIntegratedServer().addScheduledTask(()->{try{
                    var file=ServerQuestFile.INSTANCE;AcceptanceMod.roundTrip();
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="2000 个任务 · 新建节点延迟";chapter.onCreated();
                    for(int i=0;i<COUNT;i++){
                        Quest q=new Quest(chapter);q.id=FIRST+i*3;q.title="任务 "+i;q.x=i%50-25;q.y=i/50-20;q.onCreated();
                        ItemTask task=new ItemTask(q);task.id=q.id+1;task.items.add(new ItemStack(Items.IRON_INGOT));task.onCreated();
                        ItemReward reward=new ItemReward(q);reward.id=q.id+2;reward.item=new ItemStack(Items.DIAMOND);reward.onCreated();
                    }
                    file.refreshIDMap();var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
                    file.getTask(FIRST+1).changeProgress(file.getData(player),ChangeProgress.COMPLETE);
                    new MessageCreateObjectResponse().getWrapper().getChannel(Side.SERVER).pipeline().addLast("creation-response-clock",new ChannelOutboundHandlerAdapter(){
                        @Override public void write(ChannelHandlerContext context,Object message,ChannelPromise promise)throws Exception{
                            if(message instanceof MessageCreateObjectResponse&&firstResponse==0)firstResponse=System.nanoTime();super.write(context,message,promise);
                        }
                    });
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                }catch(Throwable error){failure=error;}});
            }else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(FIRST)!=null&&ClientQuestFile.INSTANCE.canEdit()){
                gui=ClientQuestFile.INSTANCE.questTreeGui;gui.selectChapter(ClientQuestFile.INSTANCE.getChapter(CHAPTER));gui.openGui();
                new MessageCreateObjectResponse().getWrapper().getChannel(Side.CLIENT).pipeline().addBefore(MessageCreateObjectResponse.class.getName(),"creation-handler-clock",new ChannelInboundHandlerAdapter(){
                    @Override public void channelRead(ChannelHandlerContext context,Object message)throws Exception{
                        if(message instanceof MessageCreateObjectResponse response){
                            // Keep the original handler's game-thread scheduling and time only its eventual onMessage().
                            context.fireChannelRead(new MessageCreateObjectResponse(){@Override public void onMessage(){
                                long start=System.nanoTime();response.onMessage();clientHandlers.add((System.nanoTime()-start)/1_000_000D);
                            }});
                        }else super.channelRead(context,message);
                    }
                });
                gui.questPanel.scrollTo(0,0);wait=ticks;step=3;
            }else if(step==3&&ticks-wait>20){
                AcceptanceMod.require(!QuestRegistry.INSTANCE.list.isEmpty(),"Real JEI runtime was not installed");
                sentinel=ClientQuestFile.INSTANCE.getQuest(FIRST);sentinelIcon=sentinel.getIcon();sentinelTitle=sentinel.getTitle();sentinelButton=button(sentinel);
                sentinelRecipe=QuestRegistry.INSTANCE.list.stream().filter(w->w.quest==sentinel).findFirst().orElseThrow();
                metrics.put("existing_quests_in_chapter",COUNT);metrics.put("baseline",Boolean.getBoolean("ftbquests.acceptance.baseline"));
                metrics.put("sidebar_duration_ms",Boolean.getBoolean("ftbquests.acceptance.baseline")?140:VisualConfig.sidebarAnimationMilliseconds);
                next();
            }else if(step==4&&rendered!=0){
                Map<String,Object> row=new LinkedHashMap<>();row.put("kind",sample==2?"create-quest":"create-quest-and-task");
                row.put("request_to_first_render_ms",(rendered-sent)/1_000_000D);row.put("request_to_server_first_response_ms",(firstResponse-sent)/1_000_000D);
                row.put("client_response_handlers_ms",List.copyOf(clientHandlers));
                row.put("client_cache_clear_events",clientClears.get()-clientBefore);row.put("server_cache_clear_events",serverClears.get()-serverBefore);
                boolean icon=sentinelIcon==sentinel.getIcon(),title=sentinelTitle==sentinel.getTitle();
                row.put("unrelated_icon_preserved",icon);row.put("unrelated_title_preserved",title);row.put("unrelated_button_preserved",sentinelButton==button(sentinel));
                row.put("unrelated_jei_recipe_preserved",QuestRegistry.INSTANCE.list.stream().anyMatch(w->w==sentinelRecipe));samples.add(row);
                if(!Boolean.getBoolean("ftbquests.acceptance.baseline")){
                    AcceptanceMod.require(icon&&title,"Creation discarded unrelated cached titles/icons");
                    AcceptanceMod.require(sentinelButton==button(sentinel),"Creation rebuilt unrelated canvas buttons");
                    AcceptanceMod.require(rendered-sent<500_000_000L,"Creation took over 500 ms to reach the actual rendered canvas: "+row);
                }
                sample++;if(sample<3){wait=ticks;step=5;}
                else{
                    shot("creation-large-book.png");step=6;mc.getIntegratedServer().addScheduledTask(()->{try{
                        var file=ServerQuestFile.INSTANCE;var chapter=file.getChapter(CHAPTER);
                        AcceptanceMod.require(chapter.quests.size()==COUNT+3,"Server lost or duplicated created quests");
                        for(Quest q:chapter.quests){AcceptanceMod.require(file.getBase(q.id)==q,"Quest missing from ID index");for(Task task:q.tasks)AcceptanceMod.require(file.getBase(task.id)==task,"Task missing from ID index");}
                        var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                        AcceptanceMod.require(file.getTask(FIRST+1).isComplete(file.getData(player)),"Creation reset existing task progress");
                        AcceptanceMod.roundTrip();metrics.put("server_id_index_task_progress_and_save_reload",true);serverStep=2;
                    }catch(Throwable error){failure=error;}});
                }
            }else if(step==4&&ticks-wait>500)throw new AssertionError("Created node never rendered");
            else if(step==5&&ticks-wait>15)next();
            else if(step==6&&serverStep==2){write();AcceptanceMod.report("PASS","Large-book creation latency, actual first canvas frame, preserved unrelated caches/buttons/JEI, server indexes, progress and save/reload",null);step=100;mc.shutdown();}
        }catch(Throwable error){try{write();}catch(Exception ignored){}AcceptanceMod.report("FAIL","Creation stage "+step,error);step=100;mc.shutdown();}
    }
    private void next(){
        px=.25+sample*.1;py=.25;firstResponse=rendered=0;clientHandlers.clear();clientBefore=clientClears.get();serverBefore=serverClears.get();
        var chapter=ClientQuestFile.INSTANCE.getChapter(CHAPTER);Quest draft=new Quest(chapter);draft.x=px;draft.y=py;
        wait=ticks;step=4;sent=System.nanoTime();
        if(sample==2)new MessageCreateObject(draft,null).sendToServer();
        else new MessageCreateTaskAt(chapter,px,py,new CheckmarkTask(draft)).sendToServer();
    }
    private void write()throws Exception{metrics.put("samples",samples);Files.writeString(Path.of("creation-metrics.json"),new GsonBuilder().setPrettyPrinting().create().toJson(metrics));}
    private void shot(String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
