package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfig;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.CheckmarkTask;
import com.feed_the_beast.ftbquests.quest.reward.ItemReward;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.opengl.GL11;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

/** Rendered production-jar transitions, interrupted input, scrolled Markdown and resource lifecycle. */
@SideOnly(Side.CLIENT)
public final class AcceptanceAnimation
{
    private static final int CHAPTER=0x6E000001,QUEST=0x6E000002,SECOND=0x6E000003;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private final List<Map<String,Object>> frames=new ArrayList<>();
    private final Set<String> shots=new HashSet<>();
    private GuiQuestTree tree;
    private int ticks,step,wait,originalScale;
    private long opened;
    private volatile int serverStep;
    private volatile Throwable failure;

    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceAnimation());}

    @SubscribeEvent public void render(TickEvent.RenderTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||tree==null||step==100)return;
        try {
            if(!(mc.currentScreen instanceof GuiWrapper wrapper)||wrapper.getGui()!=tree)return;
            AcceptanceMod.require(GL11.glGetError()==0,"Panel transition left an OpenGL error");
            AcceptanceMod.require(GL11.glGetInteger(0x8CA6)==mc.getFramebuffer().framebufferObject,"Transition leaked its framebuffer binding");
            double p=progress(tree.viewQuestPanel);
            if(step==3) {
                frames.add(Map.of("elapsed_ms",(System.nanoTime()-opened)/1_000_000D,"opacity",p));
                if(p>.05&&p<.3)shotOnce("detail-early.png");
                if(p>.35&&p<.75)shotOnce("detail-middle.png");
                if(p==1)shotOnce("detail-open.png");
            }
        } catch(Throwable ex){fail(ex);}
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;
        ticks++;
        try {
            if(failure!=null)throw new AssertionError("Server check",failure);
            if(ticks>1600)throw new AssertionError("Animation timeout at "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu) {
                step=1;mc.launchIntegratedServer("animation-acceptance","Gentle transitions",new WorldSettings(13,GameType.CREATIVE,false,false,WorldType.FLAT));
            } else if(step==1&&mc.player!=null&&ClientQuestFile.exists()) {
                step=2;server(()->{
                    AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="动画验收";chapter.onCreated();
                    for(int id:new int[]{QUEST,SECOND}) {
                        Quest q=new Quest(chapter);q.id=id;q.x=id==QUEST?-2:2;q.title=id==QUEST?"柔和过渡与滚动正文":"快速切换目标";
                        q.description.add("{\"type\":\"markdown\",\"text\":\"# 渐入、缩放与裁剪\\n\\n任务文字在过渡中保持完整，滚动后只在正文内显示。\\n\\n[返回任务](quest:"+Integer.toHexString(SECOND)+")\"}");
                        for(int i=0;i<28;i++)q.description.add("正文第 "+(i+1)+" 行：测试鼠标、滚动、物品图标和裁剪范围。");
                        q.onCreated();CheckmarkTask task=new CheckmarkTask(q);task.id=id+0x100;task.onCreated();
                        ItemReward reward=new ItemReward(q);reward.id=id+0x200;reward.item=new ItemStack(Items.DIAMOND);reward.onCreated();
                    }
                    file.refreshIDMap();file.clearCachedData();
                    var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",false);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                });
            } else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(QUEST)!=null) {
                tree=ClientQuestFile.INSTANCE.questTreeGui;ClientQuestFile.INSTANCE.editingMode=false;
                AcceptanceMod.require(PanelTransition.supported(),"This scenario must exercise framebuffer compositing");
                checks.put("detail_default_ms",VisualConfig.detailAnimationMilliseconds);checks.put("sidebar_default_ms",VisualConfig.sidebarAnimationMilliseconds);
                tree.selectChapter(ClientQuestFile.INSTANCE.getChapter(CHAPTER));tree.openGui();wait=ticks;step=21;
            } else if(step==21&&ticks-wait>12) {
                tree.viewQuest(ClientQuestFile.INSTANCE.getQuest(QUEST));opened=System.nanoTime();wait=ticks;step=3;
            } else if(step==3&&ticks-wait>20) {
                AcceptanceMod.require(frames.size()>5,"Insufficient actual animation frames");
                double previous=0;boolean intermediate=false;
                for(var frame:frames){double p=((Number)frame.get("opacity")).doubleValue();AcceptanceMod.require(p>=previous&&p<=1,"Opening opacity went backwards");previous=p;if(p>.05&&p<.95)intermediate=true;}
                AcceptanceMod.require(intermediate&&previous==1,"Popup did not ease through intermediate frames and settle");
                checks.put("actual_rendered_opening_is_monotonic_and_settles",true);
                // Test the visual location of nested widgets at an intermediate scale.
                visibility(tree.viewQuestPanel).snap(.4);
                tree.viewQuestPanel.draw(tree.getTheme(),tree.viewQuestPanel.getX(),tree.viewQuestPanel.getY(),tree.viewQuestPanel.width,tree.viewQuestPanel.height);
                PanelViewQuest panel=tree.viewQuestPanel;double p=progress(panel),scale=.985+.015*p;
                double cx=panel.getX()+panel.width/2D,cy=panel.getY()+panel.height/2D;
                Widget close=panel.buttonClose;int bx=close.getX()+close.width/2,by=close.getY()+close.height/2;
                int mx=(int)Math.round(cx+(bx-cx)*scale),my=(int)Math.round(cy+(by-cy)*scale+4*(1-p));
                tree.updateGui(mx,my,0);
                AcceptanceMod.require(close.isMouseOver(),"Animated close button hit area differs from rendered location");
                AcceptanceMod.require(Math.abs(close.getMouseX()-bx)<=1&&Math.abs(close.getMouseY()-by)<=1,"Nested input coordinates were not inversely transformed");
                AcceptanceMod.require(panel.mousePressed(MouseButton.LEFT)&&panel.isClosing()&&tree.getViewedQuest()==null,"Click during animation did not close immediately in logical state");
                AcceptanceMod.require(!panel.isEnabled(),"Fading-out panel still accepts input");
                checks.put("animated_nested_mouse_and_close_input_match_the_picture",true);
                tree.viewQuest(ClientQuestFile.INSTANCE.getQuest(SECOND));
                AcceptanceMod.require(!panel.isClosing()&&tree.getViewedQuest().id==SECOND,"Reopen during fade-out failed");
                wait=ticks;step=4;
            } else if(step==4&&ticks-wait>15) {
                PanelViewQuest panel=tree.viewQuestPanel;
                tree.updateGui(panel.getX()+panel.width/2,panel.getY()+panel.height/2,0);
                double before=panel.panelContent.getScrollY();panel.mouseScrolled(-3);
                AcceptanceMod.require(panel.panelContent.getScrollY()>before,"Long description stopped scrolling");wait=ticks;step=41;
            } else if(step==41&&ticks-wait>4) {
                shotOnce("detail-scrolled.png");
                originalScale=mc.gameSettings.guiScale;mc.gameSettings.guiScale=3;mc.resize(mc.displayWidth,mc.displayHeight);wait=ticks;step=5;
            } else if(step==5&&ticks-wait>6) {
                shotOnce("detail-resized.png");checks.put("long_markdown_scroll_and_resize_keep_correct_clipping",true);
                tree.closeQuest();wait=ticks;step=6;
            } else if(step==6&&ticks-wait>16) {
                AcceptanceMod.require(tree.viewQuestPanel.quest==null&&!tree.viewQuestPanel.isClosing(),"Fade-out retained a closed quest");
                checks.put("interrupted_open_close_and_reopen_release_logical_state",true);
                mc.gameSettings.guiScale=originalScale;mc.resize(mc.displayWidth,mc.displayHeight);
                tree.addZoom(1);wait=ticks;step=61;
            } else if(step==61&&ticks-wait>2) {
                AcceptanceMod.require(tree.getZoom()>16&&tree.getZoom()<=20,"Browsing zoom did not interpolate");
                double previous=tree.getZoom();tree.addZoom(-1);AcceptanceMod.require(tree.getZoom()==previous,"Reversing zoom jumped immediately");wait=ticks;step=62;
            } else if(step==62&&ticks-wait>16) {
                AcceptanceMod.require(Math.abs(tree.getZoom()-16)<.001,"Reversed zoom did not settle");
                checks.put("browsing_zoom_reversal_is_continuous_and_settles",true);
                // Editing stays immediate, regardless of saved player animation settings.
                ClientQuestFile.INSTANCE.editingMode=true;tree.addZoom(1);AcceptanceMod.require(tree.getZoom()==20,"Editing zoom waited for easing");tree.addZoom(-1);
                tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);wait=ticks;step=7;
            } else if(step==7&&ticks-wait>2) {
                AcceptanceMod.require(progress(tree.viewQuestPanel)==1,"Editing mode waits for a detail animation");
                tree.closeQuest();AcceptanceMod.require(tree.viewQuestPanel.quest==null,"Editing mode close is delayed");
                checks.put("editing_open_and_close_are_immediate",true);ClientQuestFile.INSTANCE.editingMode=false;
                VisualConfig.detailPanelAnimation=false;tree.viewQuest(ClientQuestFile.INSTANCE.getQuest(QUEST));wait=ticks;step=8;
            } else if(step==8&&ticks-wait>2) {
                AcceptanceMod.require(progress(tree.viewQuestPanel)==1,"Disabling detail animation still animates");
                tree.closeQuest();AcceptanceMod.require(tree.viewQuestPanel.quest==null,"Disabled close still animates");VisualConfig.detailPanelAnimation=true;
                checks.put("individual_animation_switch_preserved",true);
                ButtonChapter chapter=(ButtonChapter)tree.chapterPanel.widgets.stream().filter(w->w instanceof ButtonChapter b&&b.chapter.id==CHAPTER).findFirst().orElseThrow();
                tree.chapterPanel.setOffset(true);int x=chapter.getX()+8,y=chapter.getY()+8;tree.chapterPanel.setOffset(false);
                tree.updateGui(x,y,0);tree.chapterHoverPanel.tick();wait=ticks;step=9;
            } else if(step==9&&ticks-wait>12) {
                // The real pointer may have left the sidebar; open it again and draw a partial frame.
                PanelChapterHover panel=tree.chapterHoverPanel;
                ButtonChapter chapter=(ButtonChapter)tree.chapterPanel.widgets.stream().filter(w->w instanceof ButtonChapter b&&b.chapter.id==CHAPTER).findFirst().orElseThrow();
                panel.chapter=chapter;panel.refreshWidgets();visibility(panel).snap(.5);
                panel.draw(tree.getTheme(),panel.getX(),panel.getY(),panel.width,panel.height);
                double p=progress(panel);Widget first=panel.widgets.get(0);
                tree.updateGui((int)Math.round(first.getX()+first.width/2D-6*(1-p)),first.getY()+first.height/2,0);
                AcceptanceMod.require(first.isMouseOver(),"Sidebar slide and hit area differ");
                AcceptanceMod.require(GL11.glGetError()==0,"Sidebar left a GL error");
                checks.put("sidebar_fade_slide_nested_input_and_gl_state",true);
                // FBO-disabled fallback must keep coordinates and clipping untransformed.
                boolean previous=mc.gameSettings.fboEnable;mc.gameSettings.fboEnable=false;
                tree.viewQuest(ClientQuestFile.INSTANCE.getQuest(QUEST));visibility(tree.viewQuestPanel).snap(.5);
                tree.viewQuestPanel.draw(tree.getTheme(),tree.viewQuestPanel.getX(),tree.viewQuestPanel.getY(),tree.viewQuestPanel.width,tree.viewQuestPanel.height);
                Widget close=tree.viewQuestPanel.buttonClose;tree.updateGui(close.getX()+6,close.getY()+6,0);AcceptanceMod.require(close.isMouseOver(),"FBO-disabled fallback moved input");
                mc.gameSettings.fboEnable=previous;checks.put("framebuffer_disabled_fallback_has_correct_input",true);
                VisualSettings.open(tree);GuiEditConfig config=(GuiEditConfig)((GuiWrapper)mc.currentScreen).getGui();Field group=GuiEditConfig.class.getDeclaredField("group");group.setAccessible(true);
                AcceptanceMod.require(field(field(tree.viewQuestPanel,"transition"),"buffer")==null&&field(field(tree.chapterHoverPanel,"transition"),"buffer")==null,"Leaving the book leaked panel framebuffers");
                checks.put("temporary_render_targets_are_released_when_leaving_the_book",true);
                ConfigGroup visual=((ConfigGroup)group.get(config)).getGroup("visual");Set<String> ids=new HashSet<>();for(var entry:visual.getValues())ids.add(entry.getPath());
                for(String key:List.of("smoothingmilliseconds","sidebaranimationmilliseconds","detailanimationmilliseconds")) {
                    AcceptanceMod.require(ids.stream().anyMatch(id->id.endsWith("."+key)),"Missing visual settings control "+key+" / "+ids);
                    AcceptanceMod.require(I18n.hasKey("ftbquests.visual."+key),"Untranslated visual duration "+key);
                }
                checks.put("three_duration_controls_exist_and_are_translated",true);
                wait=ticks;step=10;server(()->{AcceptanceMod.roundTrip();serverStep=2;});
            } else if(step==10&&serverStep==2&&ticks-wait>6) {
                AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper wrapper&&wrapper.getGui() instanceof GuiEditConfig,"Settings screen did not remain open");shotOnce("animation-settings.png");
                checks.put("existing_definitions_and_animation_fixtures_save_reload",true);write();AcceptanceMod.report("PASS","Rendered detail/side transitions, interrupted input and reopen, Markdown scrolling, resize, editing instant response, switches, FBO fallback, settings and definition roundtrip",null);step=100;mc.shutdown();
            }
        } catch(Throwable ex){fail(ex);}
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static double progress(Object panel)throws Exception{return ((Number)field(panel,"progress")).doubleValue();}
    private static Easing visibility(Object panel)throws Exception{return (Easing)field(panel,"visibility");}
    private void shotOnce(String name){if(shots.add(name))ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
    private void server(Checked action){mc.getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable error){failure=error;}});}
    private interface Checked{void run()throws Exception;}
    private void write()throws Exception{Files.writeString(Path.of("animation-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("checks",checks,"rendered_opening_frames",frames)));}
    private void fail(Throwable error){try{write();}catch(Exception ignored){}AcceptanceMod.report("FAIL","Animation stage "+step,error);step=100;mc.shutdown();}
}
