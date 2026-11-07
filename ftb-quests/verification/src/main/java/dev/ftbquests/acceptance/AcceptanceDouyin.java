package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.net.*;
import com.feed_the_beast.ftbquests.net.edit.MessageMediaUpload;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.*;
import io.netty.channel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Actual user-link decoding, native controls, local playback and zero game-server video packets. */
@SideOnly(Side.CLIENT)
public final class AcceptanceDouyin
{
    private static final String SOURCE="https://v.douyin.com/IVOk7RWtfKU/";
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private final Map<String,Object> observations=new LinkedHashMap<>();
    private final AtomicInteger videoPackets=new AtomicInteger(),imagePackets=new AtomicInteger();
    private int step,ticks,wait;private volatile int serverStep;private volatile Throwable failure;
    private ClientMediaAssets.Import importing;private String videoSource,imageSource;
    private CompletableFuture<Path> pending;private VideoBackend video;private GuiBase gui;
    private AcceptanceTimeline timeline;private AcceptanceStreaming streaming;
    private Map<String,String> mediaBefore;
    private long seekTarget;
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceDouyin());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;ticks++;
        try{
            if(failure!=null)throw new AssertionError("Server",failure);
            if(ticks>2200)throw new AssertionError("Video acceptance timeout at "+step+": "+(video==null?"":video.status()));
            if(step==0&&mc.currentScreen instanceof GuiMainMenu){step=1;mc.launchIntegratedServer("douyin-local-acceptance","Client video",new WorldSettings(8,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1&&mc.player!=null&&ClientQuestFile.exists()){
                tap(new MessageMediaUpload().getWrapper().getChannel(Side.CLIENT));tap(new MessageMediaRequest().getWrapper().getChannel(Side.CLIENT));
                step=2;mc.getIntegratedServer().addScheduledTask(()->{try{
                    var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                }catch(Throwable error){failure=error;}});
            }else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.canEdit()){
                importing=ClientMediaAssets.importFile(Path.of("fixtures/acceptance.avi"),true);step=3;
            }else if(step==3&&importing.result.isDone()){
                videoSource=importing.result.join();Path file=LocalQuestVideo.resolve(Path.of("config"),videoSource);
                require(Arrays.equals(Files.readAllBytes(file),Files.readAllBytes(Path.of("fixtures/acceptance.avi"))),"import_copies_video_byte_exact_into_client_media");
                require(file.startsWith(Path.of("config/ftbquests/media").toRealPath()),"import_uses_installed_client_directory");
                require(videoPackets.get()==0,"video_import_sends_zero_upload_packets");
                require(!Files.exists(ClientMediaAssets.cache().resolve(videoSource.substring(6))),"video_import_does_not_create_download_cache");
                show(videoSource,"客户端本地视频");timeline=new AcceptanceTimeline(videoSource,"client-local",checks);step=4;
            }else if(step==4&&timeline.tick()){
                timeline=null;VideoBackend.closeAll();
                Path local=Path.of("config/ftbquests/videos/教程/演示.avi");Files.createDirectories(local.getParent());Files.copy(Path.of("fixtures/acceptance.avi"),local);
                show("local:教程/演示.avi","整合包本地路径");wait=ticks;step=5;
            }else if(step==5&&video.status().isEmpty()){
                video.toggle();wait=ticks;step=6;
            }else if(step==6&&video.hasFrame()&&video.durationMillis()>0){
                require(video.durationMillis()==20000,"local_relative_pack_path_decodes_with_native_timeline");shot("local-relative-video.png");VideoBackend.closeAll();
                pending=ClientMediaAssets.resolve("a".repeat(64)+".mp4");show("media:"+"a".repeat(64)+".mp4","缺失本地视频");wait=ticks;step=7;
            }else if(step==7&&pending.isDone()&&video.status().equals(I18n.format("media_api.video.local.missing"))){
                require(pending.isCompletedExceptionally(),"missing_local_video_fails_without_download");
                require(videoPackets.get()==0,"local_video_import_play_and_missing_file_send_zero_game_server_packets");shot("missing-client-video.png");VideoBackend.closeAll();
                importing=ClientMediaAssets.importFile(Path.of("fixtures/image.png"),false);step=8;
            }else if(step==8&&importing.result.isDone()){
                imageSource=importing.result.join();require(imagePackets.get()>0,"image_upload_still_uses_game_server_protocol_and_packet_tap_works");
                Files.delete(ClientMediaAssets.cache().resolve(imageSource.substring(6)));pending=ClientMediaAssets.resolve(imageSource.substring(6));step=9;
            }else if(step==9&&pending.isDone()){
                require(Arrays.equals(Files.readAllBytes(pending.join()),Files.readAllBytes(Path.of("fixtures/image.png"))),"image_download_still_matches_original");
                require(videoPackets.get()==0,"image_sync_never_requests_video_files");
                streaming=new AcceptanceStreaming(Path.of("fixtures/acceptance.avi"),Path.of("config/ftbquests"),true);step=10;
            }else if(step==10&&streaming.status().isEmpty()){
                streaming.play();wait=ticks;step=11;
            }else if(step==11&&streaming.hasFrame()){
                timeline=new AcceptanceTimeline(streaming.source(),"direct-http",checks);step=111;
            }else if(step==111&&timeline.tick()){
                timeline=null;
                streaming.finish(checks);streaming=null;mediaBefore=AcceptanceStreaming.snapshot(Path.of("config/ftbquests"));
                show(SOURCE,"抖音直连在线播放验收");wait=ticks;step=12;
            }else if(step==12){
                if(video.status().isEmpty()&&!video.isPlaying())video.toggle();
                if(video.hasFrame()&&video.positionMillis()>300&&video.durationMillis()>0){
                    observations.put("douyin_duration_millis",video.durationMillis());
                    require(video.durationMillis()>27000&&video.durationMillis()<35000,"douyin_user_link_native_frame_and_original_video_duration");
                    require(video.isSeekable(),"douyin_user_link_native_stream_is_seekable");
                    observations.put("douyin_user_link_live_playback",true);observations.put("douyin_status","native frame decoded");
                    observations.put("douyin_duration_millis",video.durationMillis());
                    playPause();wait=ticks;step=121;
                }else if(List.of("douyin.network","douyin.unavailable","douyin.images","douyin.link").stream().anyMatch(key->video.status().equals(I18n.format("media_api.video."+key)))){
                    observations.put("douyin_user_link_live_playback",false);observations.put("douyin_status",video.status());throw new AssertionError("Actual user Douyin link did not play: "+video.status());
                }else if(ticks-wait>700)throw new AssertionError("Douyin did not resolve or report a typed failure: "+video.status());
            }else if(step==121&&ticks-wait>10){
                require(!video.isPlaying(),"douyin_description_pause_button_pauses_native_stream");
                Widget widget=gui.widgets.get(0);seekTarget=Math.round(video.durationMillis()*.35);
                gui.updateGui(widget.getX()+4+(int)Math.round((widget.width-9)*.35),widget.getY()+widget.height-32+6,0);
                long started=System.nanoTime();gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);
                observations.put("douyin_seek_submission_ms",(System.nanoTime()-started)/1_000_000D);wait=ticks;step=122;
            }else if(step==122){
                if(ticks-wait>12&&Math.abs(video.positionMillis()-seekTarget)<1500){
                    require(!video.isPlaying(),"douyin_description_timeline_seeks_native_stream_and_preserves_pause");
                    shot("douyin-live-paused-timeline.png");wait=ticks;step=13;
                }else if(ticks-wait>500)throw new AssertionError("Actual Douyin timeline seek failed: "+video.positionMillis()+" / "+seekTarget);
            }else if(step==13&&ticks-wait>10){
                Widget widget=gui.widgets.get(0);gui.updateGui(widget.getX()+widget.width-14,widget.getY()+widget.height-10,0);
                List<String> tip=new ArrayList<>();widget.addMouseOverText(tip);require(tip.get(0).equals(I18n.format("ftbquests.video.web")),"douyin_website_icon_localized");
                gui.updateGui(widget.getX()+widget.width-34,widget.getY()+widget.height-10,0);tip.clear();widget.addMouseOverText(tip);
                require(tip.get(0).equals(I18n.format("ftbquests.video.retry")),"douyin_retry_icon_localized");
                require(VideoBackend.webPage(SOURCE).equals(SOURCE),"douyin_original_share_url_retained_for_web_fallback");
                require(mediaBefore.equals(AcceptanceStreaming.snapshot(Path.of("config/ftbquests"))),"douyin_user_link_writes_no_video_files");
                shot("douyin-user-link-result.png");VideoBackend.retry(SOURCE);video=VideoBackend.get(SOURCE);wait=ticks;step=14;
            }else if(step==14){
                if(video.status().isEmpty()&&!video.isPlaying())video.toggle();
                if(!video.hasFrame()){
                    if(ticks-wait>600)throw new AssertionError("Actual Douyin retry failed: "+video.status());
                    return;
                }
                require(true,"douyin_retry_resolves_and_decodes_fresh_live_stream");
                require(videoPackets.get()==0,"douyin_and_local_video_use_no_game_server_video_packets");
                require(mediaBefore.equals(AcceptanceStreaming.snapshot(Path.of("config/ftbquests"))),"douyin_retry_writes_no_video_files");
                VideoBackend.closeAll();step=15;mc.getIntegratedServer().addScheduledTask(()->{try{
                    var file=ServerQuestFile.INSTANCE;var chapter=new Chapter(file);chapter.id=0x6D000001;chapter.title="本地视频与抖音";chapter.onCreated();
                    var quest=new Quest(chapter);quest.id=0x6D000002;quest.title="媒体路径保留";quest.description.add("{\"type\":\"video\",\"source\":\""+videoSource+"\"}");quest.description.add("{\"type\":\"video\",\"source\":\""+SOURCE+"\"}");quest.onCreated();file.refreshIDMap();
                    var before=AcceptanceMod.snapshot(file);AcceptanceMod.roundTrip();require(before.equals(AcceptanceMod.snapshot(file)),"quest_save_reload_preserves_original_media_and_douyin_sources");serverStep=2;
                }catch(Throwable error){failure=error;}});
            }else if(step==15&&serverStep==2){
                observations.put("video_packets",videoPackets.get());observations.put("image_packets",imagePackets.get());write();
                AcceptanceMod.report("PASS","Actual user Douyin link decoded with native pause, timeline seek and retry; client-only local videos; zero video transfer packets; image protocol and original task data retained",null);step=100;mc.shutdown();
            }
        }catch(Throwable error){try{write();}catch(Exception ignored){}AcceptanceMod.report("FAIL","Douyin/local stage "+step,error);step=100;VideoBackend.closeAll();if(streaming!=null)streaming.close();mc.shutdown();}
    }
    private void tap(Channel channel){channel.pipeline().addLast("video-acceptance",new ChannelOutboundHandlerAdapter(){@Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise)throws Exception{
        String name=message instanceof MessageMediaUpload upload?upload.name:message instanceof MessageMediaRequest request?request.name:null;
        if(name!=null){if(QuestMedia.video(name))videoPackets.incrementAndGet();else imagePackets.incrementAndGet();}super.write(ctx,message,promise);
    }});}
    private void require(boolean valid,String name){AcceptanceMod.require(valid,name);checks.put(name,true);}
    private void playPause(){Widget widget=gui.widgets.get(0);gui.updateGui(widget.getX()+14,widget.getY()+widget.height-10,0);gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);}
    private void show(String source,String title){video=VideoBackend.get(source);gui=new GuiBase(){
        @Override public boolean onInit(){setSize(360,260);return true;}
        @Override public void addWidgets(){JsonObject json=new JsonObject();json.addProperty("type","video");json.addProperty("source",source);json.addProperty("width",320);json.addProperty("height",180);DescriptionComponents.add(this,new String[]{json.toString()});}
        @Override public void alignWidgets(){widgets.get(0).setPos(20,28);}
        @Override public void drawBackground(Theme theme,int x,int y,int w,int h){Color4I.rgb(0x18232D).draw(x,y,w,h);theme.drawString(title,x+12,y+10,Color4I.WHITE,0);}
    };gui.openGui();}
    private void write()throws Exception{Map<String,Object> data=new LinkedHashMap<>();data.put("checks",checks);data.put("observations",observations);Files.writeString(Path.of("douyin-local-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
    private void shot(String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
