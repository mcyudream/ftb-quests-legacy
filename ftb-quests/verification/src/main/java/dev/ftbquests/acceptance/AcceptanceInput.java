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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Production install GUI events plus real native AWT peers. OS IME engine keystrokes are not simulated. */
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
                items.keyPressed(Keyboard.KEY_F4,'\0');wait=ticks;step=5;
            } else if(step==5&&nativeDialog()!=null&&ticks-wait>3) {
                nativeEdit("中文输入铁锭",true);wait=ticks;step=6;
            } else if(step==6&&ticks-wait>4) {
                AcceptanceMod.require(input().getText().equals("中文输入铁锭"),"Native editor did not commit to its owner on the client thread");
                before=input().getText();TextBox box=input();items.updateGui(box.getX()+box.width-7,box.getY()+box.height/2,0);
                AcceptanceMod.require(items.mousePressed(MouseButton.LEFT)&&NativeTextInput.isOpen(),"The 中 input button did not open native editing");wait=ticks;step=7;
            } else if(step==7&&nativeDialog()!=null&&ticks-wait>3) {
                nativeEdit("取消修改",false);wait=ticks;step=8;
            } else if(step==8&&ticks-wait>4) {
                AcceptanceMod.require(input().getText().equals(before),"Native cancel mutated its text field");
                checks.put("native_single_line_peer_apply_cancel_and_chinese_text",true);
                input().openNativeInput();wait=ticks;step=81;
            } else if(step==81&&nativeDialog()!=null&&ticks-wait>3) {
                tree.openGui();wait=ticks;step=82;
            } else if(step==82&&ticks-wait>6) {
                AcceptanceMod.require(nativeDialog()==null&&!NativeTextInput.isOpen(),"Leaving the owner screen leaked a native editor");
                checks.put("native_input_closes_when_its_owner_changes",true);
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
                before=editor.source.buffer.text();editor.source.keyPressed(Keyboard.KEY_F4,'\0');wait=ticks;step=11;
            } else if(step==11&&nativeDialog()!=null&&ticks-wait>3) {
                nativeEdit("# 中文编辑\n\n输入法正文，标点；多行。\n末行𠀀",true);wait=ticks;step=12;
            } else if(step==12&&ticks-wait>5) {
                AcceptanceMod.require(editor.source.buffer.text().contains("输入法正文，标点；多行。\n末行𠀀"),"Multiline native edit lost Unicode or line breaks");
                editor.source.undo();AcceptanceMod.require(editor.source.buffer.text().equals(before),"Native multiline edit was not one undo step");editor.source.redo();
                editor.source.setFocused(false);TextBox title=(TextBox)field(editor,"title");title.setFocused(true);title.openNativeInput();wait=ticks;step=13;
            } else if(step==13&&nativeDialog()!=null&&ticks-wait>3) {
                nativeEdit("中文标题与标点！",true);wait=ticks;step=14;
            } else if(step==14&&ticks-wait>6) {
                AcceptanceMod.require(((TextBox)field(editor,"title")).getText().equals("中文标题与标点！"),"Native title was not applied");
                shot("chinese-editor.png");editor.save(true);checks.put("native_multiline_undo_redo_and_title",true);wait=ticks;step=15;
            } else if(step==15&&ticks-wait>20) {
                step=16;server(()->{
                    var quest=ServerQuestFile.INSTANCE.getQuest(QUEST);AcceptanceMod.require(quest.title.equals("中文标题与标点！"),"Chinese title did not reach the server");
                    AcceptanceMod.require(new DescriptionDocument(quest.description).text().contains("输入法正文，标点；多行。\n末行𠀀"),"Chinese multiline body did not reach the server");
                    AcceptanceMod.require(EditHistory.snapshot(quest).getLong("input_unknown")==9000000001L,"Text input changed unknown quest data");AcceptanceMod.roundTrip();serverStep=2;
                });
            } else if(step==16&&serverStep==2) {
                checks.put("chinese_network_disk_roundtrip_and_unknown_fields",true);
                checks.put("native_ime_engine_keystrokes_not_automated",true);
                Files.writeString(Path.of("input-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));AcceptanceMod.report("PASS","Chinese character-only commits; native AWT single/multiline apply/cancel/lifecycle/undo; real item and Ponder full-pinyin/initials selection; title/body network and disk roundtrip",null);step=100;mc.shutdown();
            }
        } catch(Throwable ex){AcceptanceMod.report("FAIL","Input stage "+step,ex);step=100;mc.shutdown();}
    }
    private TextBox input()throws Exception{return (TextBox)field(items,"searchBox");}
    private TextBox search(GuiButtonListBase gui)throws Exception{return (TextBox)fieldBase(gui,GuiButtonListBase.class,"searchBox");}
    private static Object field(Object object,String name)throws Exception{return fieldBase(object,object.getClass(),name);}
    private static Object fieldBase(Object object,Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Dialog nativeDialog()throws Exception{return (Dialog)fieldBase(null,NativeTextInput.class,"active");}
    private void nativeEdit(String value,boolean apply)throws Exception
    {
        EventQueue.invokeAndWait(()->{
            try {
                Dialog dialog=nativeDialog();AcceptanceMod.require(dialog!=null&&dialog.isShowing(),"Native editor peer not visible");
                java.awt.TextComponent input=(java.awt.TextComponent)components(dialog).stream().filter(c->"ftblib-native-text-value".equals(c.getName())).findFirst().orElseThrow();
                AcceptanceMod.require(input.getInputContext()!=null,"Native text peer has no input method context");input.setText(value);
                if(apply) {
                    java.awt.Button accept=(java.awt.Button)components(dialog).stream().filter(c->c instanceof java.awt.Button).findFirst().orElseThrow();
                    for(ActionListener listener:accept.getActionListeners())listener.actionPerformed(new ActionEvent(accept,ActionEvent.ACTION_PERFORMED,"accept"));
                } else dialog.dispatchEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSING));
            } catch(Throwable ex){failure=ex;}
        });
    }
    private static List<Component> components(Container root){List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container child)result.addAll(components(child));}return result;}
    private void server(Checked action){mc.getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable ex){failure=ex;}});}
    private interface Checked{void run()throws Exception;}
    private void shot(String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
