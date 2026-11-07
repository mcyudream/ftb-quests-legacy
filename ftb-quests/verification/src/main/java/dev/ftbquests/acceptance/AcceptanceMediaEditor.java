package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiSelectItemStack;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.GuiSelectQuestObject;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.texteditor.*;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.*;
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

/** Exercises visible controls and the real upload/download protocol on the packaged installation. */
@SideOnly(Side.CLIENT)
public final class AcceptanceMediaEditor
{
    private static final int CHAPTER=0x6c000001,QUEST=0x6c000002,PARENT=0x6c000003;
    private int ticks,step,wait; private volatile int serverStep; private volatile Throwable failure;
    private GuiQuestTree tree; private GuiQuestTextEditor editor; private GuiDescriptionComponent form;
    private Path image,videoFile; private String imageSource,videoSource;
    private CompletableFuture<Path> imageDownload,videoDownload;
    private ClientMediaAssets.Import denied;
    private VideoBackend video;
    private AcceptanceStreaming streaming;
    private AcceptanceMediaSizing sizing;
    private AcceptanceTimeline timeline;
    private final Map<String,Object> checks=new LinkedHashMap<>();
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceMediaEditor());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;
        Minecraft mc=Minecraft.getMinecraft();ticks++;
        try
        {
            if(failure!=null)throw new AssertionError("Server check",failure);
            if(ticks>3400)throw new AssertionError("Media editor timeout at "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu)
            {step=1;mc.launchIntegratedServer("media-editor-acceptance","Media Editor",new WorldSettings(3,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1&&mc.player!=null&&ClientQuestFile.exists())
            {
                image=Paths.get("picker-fixtures/选择 图片.png").toAbsolutePath();videoFile=Paths.get("picker-fixtures/选择 视频.avi").toAbsolutePath();
                step=2;server(()->{
                    AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="选择器验收章节";chapter.onCreated();
                    Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="媒体编辑与选择器";quest.onCreated();
                    Quest parent=new Quest(chapter);parent.id=PARENT;parent.title="依赖前置验收";parent.x=4;parent.onCreated();
                    var nbt=EditHistory.snapshot(quest);nbt.setLong("media_unknown",9_000_000_001L);quest.readDataPreservingUnknown(nbt);file.refreshIDMap();file.clearCachedData();
                    var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                });
            }
            else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(QUEST)!=null&&ClientQuestFile.INSTANCE.canEdit())
            {tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);editor=new GuiQuestTextEditor(ClientQuestFile.INSTANCE.getQuest(QUEST));editor.openGui();clickTitle(editor,GuiQuestTextEditor.tr("image"));form=(GuiDescriptionComponent)current();clickTitle(form,GuiMediaFilePicker.tr("browse"));wait=ticks;step=3;}
            else if(step==3&&ticks-wait>10)
            {
                AcceptanceMod.require(current() instanceof GuiMediaFilePicker,"Image picker did not open inside Minecraft");
                GuiMediaFilePicker picker=(GuiMediaFilePicker)current();picker.browse(image.getParent());wait=ticks;step=4;
            }
            else if(step==4&&ticks-wait>10)
            {
                GuiMediaFilePicker picker=(GuiMediaFilePicker)current();
                AcceptanceMod.require(picker.files.widgets.stream().anyMatch(w->w.getTitle().contains("选择 图片.png")),"Unicode image not listed");
                AcceptanceMod.require(picker.files.widgets.stream().noneMatch(w->w.getTitle().contains(".avi")),"Image picker did not filter videos");
                shot("media-file-picker.png");click(picker,picker.files.widgets.stream().filter(w->w.getTitle().contains("选择 图片.png")).findFirst().orElseThrow());clickTitle(picker,GuiMediaFilePicker.tr("select"));step=5;
            }
            else if(step==5&&current()==form&&!form.isImporting()&&form.field("source").startsWith("media:"))
            {
                imageSource=form.field("source");checks.put("in_game_picker_unicode_filter_copy_and_server_ack",true);wait=ticks;step=50;
            }
            else if(step==50&&ticks-wait>5)
            {
                shot("image-imported.png");clickTitle(form,GuiQuestTextEditor.tr("insert_apply"));
                AcceptanceMod.require(current()==editor,"Image import did not return to editor");clickTitle(editor,GuiQuestTextEditor.tr("video"));form=(GuiDescriptionComponent)current();
                clickTitle(form,GuiMediaFilePicker.tr("browse"));wait=ticks;step=6;
            }
            else if(step==6&&ticks-wait>10)
            {GuiMediaFilePicker picker=(GuiMediaFilePicker)current();picker.path.setText(videoFile.toString(),false);clickTitle(picker,GuiMediaFilePicker.tr("select"));step=7;}
            else if(step==7&&current()==form&&!form.isImporting()&&form.field("source").startsWith("media:"))
            {
                videoSource=form.field("source");checks.put("video_path_paste_streamed_upload_and_server_ack",true);clickTitle(form,GuiQuestTextEditor.tr("insert_apply"));
                clickTitle(editor,GuiQuestTextEditor.tr("quest"));form=(GuiDescriptionComponent)current();clickTitle(form,GuiQuestTextEditor.tr("choose_quest"));
                AcceptanceMod.require(current() instanceof GuiSelectQuestObject,"Quest link has no selector");search(current(),"选择器验收章节");
                AcceptanceMod.require(questButtons((GuiSelectQuestObject)current()).size()==2,"Quest search did not include chapter titles");
                search(current(),"6c000003");var buttons=questButtons((GuiSelectQuestObject)current());AcceptanceMod.require(buttons.size()==1&&buttons.get(0).object.id==PARENT,"Quest ID search did not filter");
                wait=ticks;step=70;
            }
            else if(step==70&&ticks-wait>5)
            {
                shot("quest-search-picker.png");click(current(),questButtons((GuiSelectQuestObject)current()).get(0));
                AcceptanceMod.require(form.field("value").equals("6c000003")&&form.field("text").equals("依赖前置验收"),"Quest selector did not fill link and caption");
                clickTitle(form,GuiQuestTextEditor.tr("insert_apply"));checks.put("quest_link_search_by_chapter_and_id",true);
                clickTitle(editor,GuiQuestTextEditor.tr("item"));form=(GuiDescriptionComponent)current();clickTitle(form,GuiQuestTextEditor.tr("choose_item"));
                AcceptanceMod.require(current() instanceof GuiSelectItemStack,"Item model has no selector");search(current(),I18n.format("tile.chest.name"));wait=ticks;step=8;
            }
            else if(step==8&&ticks-wait>35)
            {
                var widgets=all(current());String title=I18n.format("tile.chest.name");
                Widget chest=widgets.stream().filter(w->w instanceof Button&&w.getTitle().equals(title)).findFirst().orElseThrow();
                click(current(),chest);wait=ticks;step=80;
            }
            else if(step==80&&ticks-wait>5)
            {
                shot("item-model-picker.png");clickTitle(current(),I18n.format("gui.accept"));
                AcceptanceMod.require(form.field("item").equals("minecraft:chest")&&form.field("size").equals("32"),"Item picker lost registry ID or model size");
                clickTitle(form,GuiQuestTextEditor.tr("insert_apply"));checks.put("searchable_item_selector_and_3d_item_component",true);editor.save(true);wait=ticks;step=9;
            }
            else if(step==9&&ticks-wait>30)
            {
                tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);
                ButtonQuest target=button(QUEST);target.onClicked(MouseButton.RIGHT);clickTitle(tree,I18n.format("ftbquests.gui.search_add_dependency"));
                search(current(),"6c000003");click(current(),questButtons((GuiSelectQuestObject)current()).get(0));wait=ticks;step=10;
            }
            else if(step==10&&ticks-wait>20)
            {
                AcceptanceMod.require(ClientQuestFile.INSTANCE.getQuest(QUEST).dependencies.contains(ClientQuestFile.INSTANCE.getQuest(PARENT)),"Search-add did not sync dependency");
                button(QUEST).onClicked(MouseButton.RIGHT);clickTitle(tree,I18n.format("ftbquests.gui.search_remove_dependency"));search(current(),"依赖前置验收");click(current(),questButtons((GuiSelectQuestObject)current()).get(0));wait=ticks;step=11;
            }
            else if(step==11&&ticks-wait>20)
            {
                AcceptanceMod.require(ClientQuestFile.INSTANCE.getQuest(QUEST).dependencies.isEmpty(),"Search-remove did not sync dependency");checks.put("dependency_search_add_remove_over_network",true);
                CanvasMedia.invalidate();ClientMediaAssets.clear();Files.delete(ClientMediaAssets.cache().resolve(imageSource.substring(6)));Files.delete(ClientMediaAssets.cache().resolve(videoSource.substring(6)));
                imageDownload=ClientMediaAssets.resolve(imageSource.substring(6));videoDownload=ClientMediaAssets.resolve(videoSource.substring(6));wait=ticks;step=12;
            }
            else if(step==12&&imageDownload.isDone()&&videoDownload.isDone())
            {
                AcceptanceMod.require(Arrays.equals(Files.readAllBytes(image),Files.readAllBytes(imageDownload.join())),"Downloaded image differs from original");
                AcceptanceMod.require(Arrays.equals(Files.readAllBytes(videoFile),Files.readAllBytes(videoDownload.join())),"Downloaded video differs from original");
                checks.put("empty_client_cache_downloads_images_and_video_byte_exact",true);
                tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);wait=ticks;step=13;
            }
            else if(step==13&&ticks-wait>15)
            {
                AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError()==0,"Media/model preview caused GL error");shot("media-and-item-quest.png");
                if(Boolean.getBoolean("ftbquests.acceptance.autosize")){sizing=new AcceptanceMediaSizing(tree,ClientQuestFile.INSTANCE.getQuest(QUEST),imageSource,videoSource,checks);step=130;}
                else beginVideoChecks();
            }
            else if(step==130&&sizing.tick()){sizing=null;beginVideoChecks();}
            else if(step==14)
            {
                video.draw(8,8,128,72,"");if(video.status().isEmpty()){video.toggle();wait=ticks;step=15;}
                else if(ticks-wait>400)throw new AssertionError("Imported video initialization failed: "+video.status());
            }
            else if(step==15)
            {
                video.draw(8,8,128,72,"");
                if(video.hasFrame()){
                    video.toggle();video.replay();checks.put("imported_video_decoded_frame_pause_replay",true);
                    timeline=new AcceptanceTimeline(videoSource,"imported",checks);step=150;
                }
                else if(ticks-wait>400)throw new AssertionError("Imported video did not decode: "+video.status());
            }
            else if(step==150&&timeline.tick())
            {
                timeline=null;CanvasMedia.invalidate();
                streaming=new AcceptanceStreaming(videoFile,ClientMediaAssets.cache().getParent());wait=ticks;step=151;
            }
            else if(step==151)
            {
                if(streaming.status().isEmpty()){streaming.play();wait=ticks;step=152;}
                else if(ticks-wait>400)throw new AssertionError("HTTP stream initialization failed: "+streaming.status());
            }
            else if(step==152)
            {
                if(streaming.hasFrame()){timeline=new AcceptanceTimeline(streaming.source(),"http",checks);step=153;}
                else if(ticks-wait>400)throw new AssertionError("HTTP stream did not decode: "+streaming.status());
            }
            else if(step==153&&timeline.tick())
            {timeline=null;streaming.finish(checks);streaming=null;step=16;}
            else if(step==16)
            {
                CanvasMedia.invalidate();step=17;server(()->{var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",false);serverStep=2;});
            }
            else if(step==17&&serverStep==2){denied=ClientMediaAssets.importFile(image,false);step=18;}
            else if(step==18&&denied.result.isDone())
            {
                AcceptanceMod.require(denied.result.isCompletedExceptionally(),"Unauthorized media import succeeded");checks.put("server_rejects_import_without_edit_permission",true);
                var cancelled=ClientMediaAssets.importFile(videoFile,true);cancelled.cancel();AcceptanceMod.require(cancelled.result.isCancelled(),"Import did not cancel");checks.put("client_import_cancel",true);
                step=19;server(()->{
                    var file=ServerQuestFile.INSTANCE;Quest quest=file.getQuest(QUEST);String text=new DescriptionDocument(quest.description).text();
                    AcceptanceMod.require(text.contains(imageSource)&&text.contains(videoSource)&&text.contains("minecraft:chest")&&text.contains("6c000003"),"Saved description lost selected components");
                    AcceptanceMod.require(EditHistory.snapshot(quest).getLong("media_unknown")==9_000_000_001L,"Media edit lost unknown quest fields");
                    Path media=file.getFolder().toPath().getParent().resolve("media");AcceptanceMod.require(Files.exists(media.resolve(imageSource.substring(6)))&&Files.exists(media.resolve(videoSource.substring(6))),"Server data directory is missing imported media");
                    AcceptanceMod.roundTrip();serverStep=3;
                });
            }
            else if(step==19&&serverStep==3)
            {
                checks.put("legacy_and_new_components_disk_roundtrip_unknown_fields",true);
                Files.writeString(Paths.get("media-editor-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                AcceptanceMod.report("PASS","In-game image/video file pickers, background copy, real server upload and ACK, empty-cache download, item and task selectors, dependency add/remove, permission rejection, cancel, legacy roundtrip"+(Boolean.getBoolean("ftbquests.acceptance.video")?", native video frame and controls":"; optional video backend absent"),null);step=100;mc.shutdown();
            }
        }
        catch(Throwable ex){int at=step;step=100;if(streaming!=null)streaming.close();AcceptanceMod.report("FAIL","Media editor step "+at,ex);mc.shutdown();}
    }
    private void beginVideoChecks()
    {
        if(Boolean.getBoolean("ftbquests.acceptance.video")){video=VideoBackend.get(videoSource);step=14;wait=ticks;}
        else {
            var missing=VideoBackend.get(videoSource);
            AcceptanceMod.require(missing.supportIssue()==VideoBackend.SupportIssue.MOD,"Missing optional Media API was not detected");
            AcceptanceMod.require(!missing.status().contains("ftbquests.video."),"Missing Media API translation key leaked");
            AcceptanceTimeline.unavailable(videoSource,checks);
            missing.toggle();AcceptanceMod.require(current()!=tree,"Missing Media API help did not open");
            current().closeGui(true);checks.put("missing_optional_video_backend_is_safe",true);checks.put("localized_missing_mod_and_help_entry",true);step=16;
        }
    }
    private void server(Checked action){Minecraft.getMinecraft().getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable ex){failure=ex;}});}
    private interface Checked{void run()throws Exception;}
    private static GuiBase current(){return ((GuiWrapper)Minecraft.getMinecraft().currentScreen).getGui();}
    private ButtonQuest button(int id){return (ButtonQuest)tree.questPanel.widgets.stream().filter(w->w instanceof ButtonQuest q&&q.quest.id==id).findFirst().orElseThrow();}
    private static List<Widget> all(Panel panel){List<Widget> result=new ArrayList<>();for(Widget widget:panel.widgets){result.add(widget);if(widget instanceof Panel child)result.addAll(all(child));}if(panel instanceof GuiBase gui&&gui.contextMenu!=null)result.addAll(all(gui.contextMenu));return result;}
    private static List<GuiSelectQuestObject.ButtonQuestObject> questButtons(GuiSelectQuestObject gui){return all(gui).stream().filter(w->w instanceof GuiSelectQuestObject.ButtonQuestObject).map(w->(GuiSelectQuestObject.ButtonQuestObject)w).toList();}
    private static void search(GuiBase gui,String value){TextBox search=(TextBox)all(gui).stream().filter(w->w instanceof TextBox).findFirst().orElseThrow();search.setText(value);}
    private static void clickTitle(GuiBase gui,String title){click(gui,all(gui).stream().filter(w->w instanceof Button&&w.getTitle().equals(title)).findFirst().orElseThrow(()->new AssertionError("Missing button "+title+" on "+gui.getClass())));}
    private static void click(GuiBase gui,Widget widget){gui.updateGui(widget.getX()+3,widget.getY()+3,0);gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);}
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
