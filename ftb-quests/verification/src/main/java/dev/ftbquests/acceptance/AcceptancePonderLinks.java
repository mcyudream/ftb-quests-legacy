package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.GuiSelectPonder;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.CheckmarkTask;
import com.feed_the_beast.ftbquests.texteditor.*;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.input.Keyboard;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Real packaged-mod UI, network save and playback; this code is never shipped. No Ponder types. */
@SideOnly(Side.CLIENT)
public final class AcceptancePonderLinks
{
    private static final int CHAPTER=0x69000001,QUEST=0x69000002,TASK=0x69000003;
    private static final String TARGET="minecraft:compass#ftbquests:links_second";
    private static final List<String> LEGACY=List.of("{\"type\":\"ponder\",\"value\":\"minecraft:compass\",\"addon\":{\"kept\":[1,2]}}","  &a旧正文保留  ","{\"type\":\"future\",\"extra\":123}","","");
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private int ticks,step,wait; private volatile int serverStep; private volatile Throwable failure;
    private GuiQuestTree tree; private GuiQuestTextEditor editor; private GuiDescriptionComponent form;
    private GuiScreen returnScreen; private List<String> saved;
    private String beforeMarkdownChoice;
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptancePonderLinks());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;
        ticks++;
        try
        {
            if(failure!=null)throw new AssertionError("Server check",failure);
            if(ticks>2200)throw new AssertionError("Ponder link timeout at "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu)
            {step=1;mc.launchIntegratedServer("ponder-link-acceptance","Ponder Links",new WorldSettings(7,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1&&mc.player!=null&&ClientQuestFile.exists())
            {
                step=2;server(()->{
                    AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="思索链接验收";chapter.onCreated();
                    Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="任务内思索教程";quest.description.addAll(LEGACY);quest.onCreated();
                    CheckmarkTask task=new CheckmarkTask(quest);task.id=TASK;task.onCreated();
                    var nbt=EditHistory.snapshot(quest);nbt.setLong("ponder_unknown",9_000_000_001L);quest.readDataPreservingUnknown(nbt);
                    file.refreshIDMap();file.clearCachedData();var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
                    task.changeProgress(file.getData(player),ChangeProgress.COMPLETE);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                });
            }
            else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(QUEST)!=null&&ClientQuestFile.INSTANCE.canEdit())
            {
                checks.put("optional_ponder_loaded",PonderLink.available());
                tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);
                editor=new GuiQuestTextEditor(ClientQuestFile.INSTANCE.getQuest(QUEST));editor.openGui();
                AcceptanceMod.require(editor.draftLines().equals(LEGACY)&&!editor.isDirty(),"Opening changed old description");
                editor.source.buffer.select(0,false);clickTitle(editor,GuiQuestTextEditor.tr("ponder"));form=(GuiDescriptionComponent)current();
                input("value").setText("bad target",false);clickTitle(form,GuiQuestTextEditor.tr("insert_apply"));
                AcceptanceMod.require(current()==form,"Invalid input was saved");checks.put("invalid_target_stays_in_form",true);
                clickTitle(form,PonderLink.tr("choose_button"));wait=ticks;
                if(!PonderLink.available())
                {
                    AcceptanceMod.require(current()!=form,"Missing optional mod had no message");
                    AcceptanceMod.require(!PonderLink.tr("missing").startsWith("ftbquests."),"Missing translation");
                    shot("ponder-missing.png");current().closeGui(true);AcceptanceMod.require(current()==form,"Notice did not return to form");
                    input("value").setText("ponder:"+TARGET,false);input("text").setText("第二演示",false);step=4;
                }
                else
                {
                    AcceptanceMod.require(current() instanceof GuiSelectPonder,"Selector missing");
                    search("指南针");AcceptanceMod.require(entries().size()>=3,"Localized item name search failed");
                    search("minecraft:compass");AcceptanceMod.require(entries().size()>=3,"Component ID search failed");
                    search("links_second");AcceptanceMod.require(entries().size()==1,"Scene ID search failed");
                    search("第二演示");AcceptanceMod.require(entries().size()==1,"Scene title search failed");
                    search("ftbquests:links_second");checks.put("search_by_item_name_component_scene_id_and_title",true);step=3;
                }
            }
            else if(step==3&&ticks-wait>8)
            {
                shot("ponder-selector.png");click(current(),entries().get(0));
                AcceptanceMod.require(current()==form&&form.field("value").equals(TARGET)&&form.field("text").contains("第二演示"),"Selected target/caption was lost");wait=ticks;step=4;
            }
            else if(step==4&&ticks-wait>8)
            {
                shot("ponder-component.png");clickTitle(form,GuiQuestTextEditor.tr("insert_apply"));
                AcceptanceMod.require(current()==editor,"Component did not return to editor");
                var component=new JsonParser().parse(editor.draftLines().get(0)).getAsJsonObject();
                AcceptanceMod.require(component.get("value").getAsString().equals(TARGET)&&component.getAsJsonObject("addon").getAsJsonArray("kept").size()==2,"Unknown component fields lost");
                clickTitle(editor,GuiQuestTextEditor.tr("mode_legacy"));
                editor.source.buffer.select(editor.source.buffer.text().length(),false);
                int start=editor.source.buffer.text().length()+1;
                editor.source.insert("\n选择文字\n");editor.source.buffer.select(start,false);editor.source.buffer.select(start+4,true);
                beforeMarkdownChoice=editor.source.buffer.text();
                clickTitle(editor,GuiQuestTextEditor.tr("ponder"));wait=ticks;
                if(PonderLink.available())
                {
                    AcceptanceMod.require(current() instanceof GuiSelectPonder,"Markdown toolbar did not open selector directly");
                    TextBox search=(TextBox)all(current()).stream().filter(w->w instanceof TextBox).findFirst().orElseThrow();
                    AcceptanceMod.require(search.isFocused(),"Search box did not receive focus");
                    search("links_second");step=40;
                }
                else
                {
                    current().closeGui(true);
                    AcceptanceMod.require(current()==editor&&editor.source.buffer.text().equals(beforeMarkdownChoice)&&editor.source.buffer.selected().equals("选择文字"),"Missing Ponder modified Markdown draft/selection");
                    checks.put("missing_ponder_markdown_picker_preserves_draft_and_selection",true);
                    editor.source.insert("[选择文字](ponder:"+TARGET+")");appendAllComponent();
                }
            }
            else if(step==40&&ticks-wait>8)
            {
                shot("ponder-markdown-selector.png");current().onBack();
                AcceptanceMod.require(current()==editor&&editor.source.buffer.text().equals(beforeMarkdownChoice)&&editor.source.buffer.selected().equals("选择文字"),"Cancelling selector changed draft/selection");
                checks.put("cancel_markdown_selector_preserves_draft_and_selection",true);
                clickTitle(editor,GuiQuestTextEditor.tr("ponder"));search("links_second");click(current(),entries().get(0));
                String inserted=editor.source.buffer.text();
                AcceptanceMod.require(inserted.equals(beforeMarkdownChoice.replace("选择文字","[选择文字](ponder:"+TARGET+")")),"Selected caption was not wrapped in Markdown link");
                editor.source.undo();AcceptanceMod.require(editor.source.buffer.text().equals(beforeMarkdownChoice)&&editor.source.buffer.selected().equals("选择文字"),"Markdown insertion is not one undo step");
                editor.source.redo();AcceptanceMod.require(editor.source.buffer.text().equals(inserted),"Markdown insertion redo failed");
                checks.put("markdown_selector_wraps_selected_caption_and_undo_redo",true);
                editor.source.buffer.select(editor.source.buffer.text().length(),false);
                clickTitle(editor,GuiQuestTextEditor.tr("ponder"));search("links_second");wait=ticks;step=41;
            }
            else if(step==41&&ticks-wait>8)
            {
                String title=entries().get(0).getTitle();click(current(),entries().get(0));
                AcceptanceMod.require(current()==editor&&editor.source.buffer.text().endsWith("["+title+"](ponder:"+TARGET+")"),"Automatic tutorial caption/URI missing");
                checks.put("markdown_selector_uses_tutorial_caption_without_selection",true);
                appendAllComponent();
            }
            else if(step==5&&ticks-wait>10)
            {
                Widget button=editor.preview.widgets.stream().filter(w->w instanceof Button).findFirst().orElseThrow();
                click(editor,button);AcceptanceMod.require(current()==editor,"Preview executed Ponder action");
                checks.put("preview_does_not_execute_actions",true);shot("ponder-editor.png");
                saved=List.copyOf(editor.draftLines());editor.save(true);wait=ticks;step=6;
            }
            else if(step==6&&ticks-wait>30)
            {
                AcceptanceMod.require(ClientQuestFile.INSTANCE.getQuest(QUEST).description.equals(saved),"Client saved lines differ");
                tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);returnScreen=mc.currentScreen;
                shot("ponder-quest.png");click(tree,tree.viewQuestPanel.panelText.widgets.stream().filter(w->w instanceof Button).findFirst().orElseThrow());
                wait=ticks;step=7;
            }
            else if(step==7&&ticks-wait>35)
            {
                if(PonderLink.available()){verifyAnimation("ftbquests:links_second");shot("ponder-selected-animation.png");closeAnimationButton();}
                else {AcceptanceMod.require(current()!=tree,"JSON link did not show missing-mod notice");current().closeGui(true);}
                AcceptanceMod.require(mc.currentScreen==returnScreen,"JSON link did not return to original quest screen");
                checks.put("json_link_and_return_to_quest",true);
                tree.updateGui(0,0,0);
                Widget markdown=tree.viewQuestPanel.panelText.widgets.stream().filter(w->{
                    reveal(w);tree.updateGui(w.getX()+3,w.getY()+3,0);List<String> tooltip=new ArrayList<>();w.addMouseOverText(tooltip);return tooltip.contains("ponder:"+TARGET);
                }).findFirst().orElseThrow(()->new AssertionError("Markdown link widget missing: "+tree.viewQuestPanel.panelText.widgets.stream().map(w->w.getClass().getName()+" "+w.posY+"/"+w.height+" hover="+w.isMouseOver()).toList()));
                click(tree,markdown);wait=ticks;step=8;
            }
            else if(step==8&&ticks-wait>30)
            {
                if(PonderLink.available())verifyAnimation("ftbquests:links_second");else AcceptanceMod.require(current()!=tree,"Markdown did not show missing-mod notice");
                escape();AcceptanceMod.require(mc.currentScreen==returnScreen,"Markdown Escape did not return to task");checks.put("markdown_link_selects_same_scene_and_returns",true);
                clickTitle(tree,"全部演示入口");wait=ticks;step=9;
            }
            else if(step==9&&ticks-wait>30)
            {
                if(PonderLink.available())verifyAnimation("ftbquests:links_first");
                escape();AcceptanceMod.require(mc.currentScreen==returnScreen,"Legacy component link lost return screen");checks.put("legacy_component_link_opens_all_scenes",true);
                if(Boolean.getBoolean("ftbquests.acceptance.guides"))
                {
                    var options=PonderLink.options();AcceptanceMod.require(options.stream().anyMatch(o->o.value().equals("minecraft:compass#ftbquests:links_guide")&&o.title().contains("任务图文教程")),"Guide metadata missing from selector");
                    tree.handleClick("ponder","minecraft:compass#ftbquests:links_guide");wait=ticks;step=10;
                }
                else finishLinks();
            }
            else if(step==10&&ticks-wait>15)
            {
                verifyGuide("ftbquests:links_guide");shot("ponder-guide.png");escape();AcceptanceMod.require(mc.currentScreen==returnScreen,"Guide Escape lost task screen");
                checks.put("guide_only_tutorial_and_search_metadata",true);
                tutorialPreference(true);tree.handleClick("ponder","minecraft:compass#ftbquests:links_animation");wait=ticks;step=11;
            }
            else if(step==11&&ticks-wait>25)
            {
                verifyAnimation("ftbquests:links_animation");
                var buttons=screenButtons();AcceptanceMod.require(buttons.stream().anyMatch(b->b.id==10&&!b.enabled),"Animation-only tutorial enabled an unrelated guide");
                escape();checks.put("animation_only_respects_explicit_target_with_guide_preference",true);
                tree.handleClick("ponder","minecraft:compass#ftbquests:links_mapped");wait=ticks;step=12;
            }
            else if(step==12&&ticks-wait>15)
            {
                verifyGuide("ftbquests:links_mapped");escape();tutorialPreference(false);
                tree.handleClick("ponder","minecraft:compass#ftbquests:links_mapped");wait=ticks;step=13;
            }
            else if(step==13&&ticks-wait>25)
            {verifyAnimation("ftbquests:links_second");escape();checks.put("specific_tutorial_maps_to_correct_guide_and_animation",true);finishLinks();}
            else if(step==20&&serverStep==2)
            {
                Files.writeString(Paths.get("ponder-links-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                AcceptanceMod.report("PASS","Optional Ponder editor, searchable metadata selector, JSON/Markdown scene links, exact scene playback and return, missing/invalid targets, preview, server save/reload and retained legacy fields/progress",null);
                step=100;mc.shutdown();
            }
        }
        catch(Throwable ex){AcceptanceMod.report("FAIL","Ponder link stage "+step,ex);step=100;mc.shutdown();}
    }
    private void appendAllComponent()
    {
        editor.source.buffer.select(editor.source.buffer.text().length(),false);
        editor.source.insert("\n{\"type\":\"ponder\",\"value\":\"minecraft:compass\",\"text\":\"全部演示入口\"}\n");
        wait=ticks;step=5;
    }
    private void finishLinks()
    {
        tree.handleClick("ponder","minecraft:stone#ftbquests:missing");AcceptanceMod.require(current()!=tree,"Missing target produced no notice");current().closeGui(true);
        tree.handleClick("ponder","invalid target");AcceptanceMod.require(current()!=tree,"Invalid link produced no notice");current().closeGui(true);
        checks.put("missing_and_invalid_targets_are_localized_and_safe",true);
        AcceptanceMod.require(ClientQuestFile.INSTANCE.getQuest(QUEST).description.equals(saved),"Following links mutated the description");
        step=20;server(()->{
            var file=ServerQuestFile.INSTANCE;Quest quest=file.getQuest(QUEST);
            AcceptanceMod.require(quest.description.equals(saved),"Server lost saved Ponder description");
            AcceptanceMod.require(EditHistory.snapshot(quest).getLong("ponder_unknown")==9_000_000_001L,"Unknown quest fields lost");
            var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            AcceptanceMod.require(file.getTask(TASK).isComplete(file.getData(player)),"Quest edit/link playback reset task progress");
            AcceptanceMod.roundTrip();checks.put("server_save_reload_unknown_fields_and_existing_task_progress",true);serverStep=2;
        });
    }
    private void verifyAnimation(String id)throws Exception
    {
        Object scene=method(mc.currentScreen.getClass(),"getActiveScene").invoke(mc.currentScreen);
        AcceptanceMod.require(method(scene.getClass(),"getId").invoke(scene).toString().equals(id),"Wrong Ponder scene");
        AcceptanceMod.require((Integer)method(scene.getClass(),"getCurrentTick").invoke(scene)>0,"Scene is not playing");
        AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError()==0,"Ponder GL error");
    }
    private void verifyGuide(String id)throws Exception
    {Object page=method(mc.currentScreen.getClass(),"currentTutorial").invoke(mc.currentScreen);AcceptanceMod.require(method(page.getClass(),"id").invoke(page).toString().equals(id),"Wrong guide");}
    private void tutorialPreference(boolean guide)throws Exception
    {Class.forName("net.createmod.ponder.client.guide.Tutorials").getMethod("setPreferGuide",boolean.class).invoke(null,guide);}
    private void escape()throws Exception
    {
        if(mc.currentScreen instanceof GuiWrapper wrapper)wrapper.getGui().onBack();
        else {Method m=method(mc.currentScreen.getClass(),"keyTyped",char.class,int.class);m.setAccessible(true);m.invoke(mc.currentScreen,'\0',Keyboard.KEY_ESCAPE);}
    }
    @SuppressWarnings("unchecked") private List<GuiButton> screenButtons()throws Exception
    {Field f;try{f=GuiScreen.class.getDeclaredField("field_146292_n");}catch(NoSuchFieldException ex){f=GuiScreen.class.getDeclaredField("buttonList");}f.setAccessible(true);return (List<GuiButton>)f.get(mc.currentScreen);}
    private void closeAnimationButton()throws Exception
    {GuiButton button=screenButtons().stream().filter(b->b.id==6).findFirst().orElseThrow();Method m=method(mc.currentScreen.getClass(),"mouseClicked",int.class,int.class,int.class);m.setAccessible(true);m.invoke(mc.currentScreen,button.x+3,button.y+3,0);}
    private static Method method(Class<?> type,String name,Class<?>... args)throws Exception
    {String alternate=switch(name){case "keyTyped"->"func_73869_a";case "mouseClicked"->"func_73864_a";default->name;};for(Class<?> c=type;c!=null;c=c.getSuperclass())for(String candidate:new String[]{name,alternate})try{return c.getDeclaredMethod(candidate,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    @SuppressWarnings("unchecked") private TextBox input(String key)throws Exception
    {Field f=GuiDescriptionComponent.class.getDeclaredField("inputs");f.setAccessible(true);return ((Map<String,TextBox>)f.get(form)).get(key);}
    private static GuiBase current(){return ((GuiWrapper)Minecraft.getMinecraft().currentScreen).getGui();}
    private static List<Widget> all(Panel panel){List<Widget> result=new ArrayList<>();for(Widget w:panel.widgets){result.add(w);if(w instanceof Panel child)result.addAll(all(child));}return result;}
    private List<Widget> entries(){return all(current()).stream().filter(w->w instanceof Button).toList();}
    private void search(String value){((TextBox)all(current()).stream().filter(w->w instanceof TextBox).findFirst().orElseThrow()).setText(value);current().updateGui(0,0,0);}
    // This short fixture fits without scrolling; FTBLib offsets only while dispatching/drawing.
    private void reveal(Widget widget){tree.viewQuestPanel.panelText.setScrollY(0);}
    private void clickTitle(GuiBase gui,String title){gui.updateGui(0,0,0);click(gui,all(gui).stream().filter(w->w instanceof Button&&w.getTitle().equals(title)).findFirst().orElseThrow(()->new AssertionError("Missing button "+title)));}
    private void click(GuiBase gui,Widget widget){if(gui==tree)reveal(widget);gui.updateGui(widget.getX()+3,widget.getY()+3,0);gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);}
    private void server(Checked action){mc.getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable ex){failure=ex;}});}
    private interface Checked{void run()throws Exception;}
    private void shot(String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
