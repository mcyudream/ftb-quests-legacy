package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.*;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.*;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.texteditor.*;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.GsonBuilder;
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
import org.lwjgl.input.Keyboard;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Installed GUI and keyboard-queue commits; no external editor. Real OS IME engine keystrokes are not simulated. */
@SideOnly(Side.CLIENT)
public final class AcceptanceInput
{
    private static final int CHAPTER=0x6B000001,QUEST=0x6B000002;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private int ticks,step,wait,query;
    private volatile int serverStep;
    private volatile Throwable failure;
    private GuiQuestTree tree;
    private GuiSelectItemStack items;
    private GuiSelectPonder ponder;
    private GuiQuestTextEditor editor;
    private ItemStack selectedItem=ItemStack.EMPTY;
    private PonderLink.Option selectedPonder;
    private String before;
    private long windowsBefore;
    private static final String[] ITEM_QUERIES={"铁锭","tieding","td","iron_ingot","@minecraft"};
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceInput());}

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;ticks++;
        try {
            if(failure!=null)throw new AssertionError("Input server",failure);
            if(ticks>2400)throw new AssertionError("Input timeout at "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu) {
                mc.getLanguageManager().setCurrentLanguage(mc.getLanguageManager().getLanguages().stream().filter(l->l.getLanguageCode().equals("zh_cn")).findFirst().orElseThrow());mc.gameSettings.language="zh_cn";mc.refreshResources();
                step=1;mc.launchIntegratedServer("input-acceptance","Input and pinyin",new WorldSettings(17,GameType.CREATIVE,false,false,WorldType.FLAT));
            } else if(step==1&&mc.player!=null&&ClientQuestFile.exists()) {
                step=2;server(()->{
                    AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="中文输入验收";chapter.onCreated();
                    Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="保留原标题";quest.description.add("原正文与未知扩展不丢失");quest.onCreated();
                    var nbt=EditHistory.snapshot(quest);nbt.setLong("input_unknown",9000000001L);quest.readDataPreservingUnknown(nbt);
                    file.refreshIDMap();file.clearCachedData();var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                });
            } else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(QUEST)!=null&&com.feed_the_beast.ftblib.client.CreativeItemIndex.complete()) {
                tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST),false);
                items=new GuiSelectItemStack(tree,stack->selectedItem=stack);items.openGui();input().setText("",false);input().setCursorPosition(0);
                // Commit-only character events, as produced by an input method; no paste.
                for(char c:"铁锭".toCharArray())items.keyPressed(Keyboard.KEY_NONE,c);
                AcceptanceMod.require(input().getText().equals("铁锭"),"Character-only Chinese commit was dropped");
                input().keyPressed(Keyboard.KEY_BACK,'\0');AcceptanceMod.require(input().getText().equals("铁"),"Chinese backspace corrupted text");
                input().setText("",false);input().writeText("𠀀");input().deleteFromCursor(-1);AcceptanceMod.require(input().getText().isEmpty(),"Supplementary Han backspace left half a character");
                checks.put("direct_commit_only_chinese_events_and_unicode_backspace",true);
                input().setText(ITEM_QUERIES[0]);wait=ticks;step=3;
            } else if(step==3&&ticks-wait>15) {
                Panel stacks=(Panel)field(items,"panelStacks");
                AcceptanceMod.require(stacks.widgets.stream().anyMatch(w->{try{return ((ItemStack)field(w,"stack")).getItem()==Items.IRON_INGOT;}catch(Exception ex){return false;}}),"Item query did not find iron ingot: "+ITEM_QUERIES[query]);
                checks.put("item_search_"+ITEM_QUERIES[query],true);query++;
                if(query<ITEM_QUERIES.length){input().setText(ITEM_QUERIES[query]);wait=ticks;}
                else {input().setText("tieding");wait=ticks;step=4;}
            } else if(step==4&&ticks-wait>15) {
                shot("item-pinyin.png");Panel stacks=(Panel)field(items,"panelStacks");
                Button selected=(Button)stacks.widgets.stream().filter(w->{try{return ((ItemStack)field(w,"stack")).getItem()==Items.IRON_INGOT;}catch(Exception ex){return false;}}).findFirst().orElseThrow();selected.onClicked(MouseButton.LEFT);
                ((Button)field(items,"buttonAccept")).onClicked(MouseButton.LEFT);AcceptanceMod.require(selectedItem.getItem()==Items.IRON_INGOT,"Pinyin search selected the wrong item");
                checks.put("item_pinyin_selection_returns_actual_stack",true);
                items=new GuiSelectItemStack(tree,stack->selectedItem=stack);items.openGui();input().setText("原文");input().setCursorPosition(2);
                windowsBefore=visibleWindows();
                queueKey(Keyboard.KEY_F4);
                TextBox box=input();items.updateGui(box.getX()+box.width-7,box.getY()+box.height/2,0);items.mousePressed(MouseButton.LEFT);
                queueText("中文输入铁锭");wait=ticks;step=5;
            } else if(step==5&&ticks-wait>6) {
                AcceptanceMod.require(input().getText().equals("原文中文输入铁锭"),"Keyboard-queue Chinese did not reach the original search field");
                AcceptanceMod.require(visibleWindows()==windowsBefore&&!NativeTextInput.isOpen(),"F4 or the former 中 position opened an external editor");
                checks.put("inline_search_keyboard_queue_chinese_without_external_window",true);
                tree.openGui();
                var options=PonderLink.options();AcceptanceMod.require(options.stream().anyMatch(o->o.title().contains("能源核心结构搭建")),"Ponder fixture was not registered");
                ponder=new GuiSelectPonder(tree,options,option->selectedPonder=option);ponder.openGui();ponder.focus();search(ponder).setText("nyhxjgdj");wait=ticks;step=9;
            } else if(step==9&&ticks-wait>18) {
                Panel panel=(Panel)fieldBase(ponder,GuiButtonListBase.class,"panelButtons");
                AcceptanceMod.require(panel.widgets.stream().anyMatch(w->w.getTitle().contains("能源核心结构搭建")),"Ponder initials did not match scene title");
                checks.put("ponder_initials_find_localized_scene_title",true);shot("ponder-initials.png");search(ponder).setText("nengyuanhexinjiegoudajian");wait=ticks;step=10;
            } else if(step==10&&ticks-wait>6) {
                Panel panel=(Panel)fieldBase(ponder,GuiButtonListBase.class,"panelButtons");
                Button button=(Button)panel.widgets.stream().filter(w->w.getTitle().contains("能源核心结构搭建")).findFirst().orElseThrow();button.onClicked(MouseButton.LEFT);
                AcceptanceMod.require(selectedPonder.value().endsWith("#ftbquests:links_second"),"Ponder full-pinyin selection lost scene ID");checks.put("ponder_full_pinyin_selects_exact_scene",true);
                TextEditorFrame.open(ClientQuestFile.INSTANCE.getQuest(QUEST));editor=(GuiQuestTextEditor)((GuiWrapper)mc.currentScreen).getGui();
                editor.source.edit(()->{editor.source.buffer.selectAll();editor.source.buffer.replace("");});
                queueText("# 中文编辑");wait=ticks;step=11;
            } else if(step==11&&ticks-wait>4) {
                AcceptanceMod.require(editor.source.buffer.text().equals("# 中文编辑"),"Source keyboard input was dropped");
                editor.source.keyPressed(Keyboard.KEY_RETURN,'\0');editor.source.keyPressed(Keyboard.KEY_RETURN,'\0');
                queueText("输入法正文，标点；多行。");wait=ticks;step=12;
            } else if(step==12&&ticks-wait>4) {
                editor.source.keyPressed(Keyboard.KEY_RETURN,'\0');queueText("末行𠀀");wait=ticks;step=13;
            } else if(step==13&&ticks-wait>4) {
                before=editor.source.buffer.text();
                AcceptanceMod.require(before.equals("# 中文编辑\n\n输入法正文，标点；多行。\n末行𠀀"),"Inline multiline commits lost Unicode or newlines");
                editor.source.undo();AcceptanceMod.require(editor.source.buffer.text().endsWith("末行"),"Undo split a supplementary Han character");
                editor.source.redo();AcceptanceMod.require(editor.source.buffer.text().equals(before),"Redo did not restore inline text");
                queueKey(Keyboard.KEY_F4);wait=ticks;step=131;
            } else if(step==131&&ticks-wait>4) {
                AcceptanceMod.require(visibleWindows()==windowsBefore&&!NativeTextInput.isOpen(),"Source F4 opened an external editor");
                AcceptanceMod.require(editor.source.buffer.text().equals(before),"F4 changed the draft");
                editor.source.setFocused(false);TextBox title=(TextBox)field(editor,"title");title.setText("",false);title.setCursorPosition(0);title.setFocused(true);
                queueText("中文标题与标点！");wait=ticks;step=14;
            } else if(step==14&&ticks-wait>6) {
                AcceptanceMod.require(((TextBox)field(editor,"title")).getText().equals("中文标题与标点！"),"Inline title input was not applied");
                shot("chinese-editor.png");editor.save(true);checks.put("inline_multiline_unicode_undo_redo_and_title",true);wait=ticks;step=15;
            } else if(step==15&&ticks-wait>20) {
                step=16;server(()->{
                    var quest=ServerQuestFile.INSTANCE.getQuest(QUEST);AcceptanceMod.require(quest.title.equals("中文标题与标点！"),"Chinese title did not reach the server");
                    AcceptanceMod.require(new DescriptionDocument(quest.description).text().contains("输入法正文，标点；多行。\n末行𠀀"),"Chinese multiline body did not reach the server");
                    AcceptanceMod.require(EditHistory.snapshot(quest).getLong("input_unknown")==9000000001L,"Text input changed unknown quest data");AcceptanceMod.roundTrip();serverStep=2;
                });
            } else if(step==16&&serverStep==2) {
                checks.put("chinese_network_disk_roundtrip_and_unknown_fields",true);
                checks.put("native_ime_engine_keystrokes_not_automated",true);
                Files.writeString(Path.of("input-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));AcceptanceMod.report("PASS","Inline Chinese keyboard-queue commits, supplementary Han undo/redo, no F4 or 中 editor popup; item/Ponder pinyin selection; Chinese title/body network and disk roundtrip",null);step=100;mc.shutdown();
            }
        } catch(Throwable ex){AcceptanceMod.report("FAIL","Input stage "+step,ex);step=100;mc.shutdown();}
    }
    private TextBox input()throws Exception{return (TextBox)field(items,"searchBox");}
    private TextBox search(GuiButtonListBase gui)throws Exception{return (TextBox)fieldBase(gui,GuiButtonListBase.class,"searchBox");}
    private static Object field(Object object,String name)throws Exception{return fieldBase(object,object.getClass(),name);}
    private static Object fieldBase(Object object,Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static long visibleWindows()
    { return java.util.Arrays.stream(java.awt.Window.getWindows()).filter(java.awt.Window::isShowing).count(); }
    private static void queueText(String value) throws Exception
    {
        Method character=Keyboard.class.getMethod("addCharEvent",int.class,char.class);
        for(char c:value.toCharArray())character.invoke(null,0,c);
    }
    private static void queueKey(int key) throws Exception
    {
        Method event=Keyboard.class.getMethod("addKeyEvent",int.class,boolean.class);
        event.invoke(null,key,true);event.invoke(null,key,false);
    }
    private void server(Checked action){mc.getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable ex){failure=ex;}});}
    private interface Checked{void run()throws Exception;}
    private void shot(String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
