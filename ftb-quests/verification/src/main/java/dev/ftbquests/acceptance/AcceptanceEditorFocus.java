package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.*;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.client.PonderLink;
import com.feed_the_beast.ftbquests.gui.GuiSelectPonder;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.texteditor.*;
import com.feed_the_beast.ftbquests.util.EditHistory;
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
import org.lwjgl.input.Keyboard;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Actual wrapper clicks and queued text; leaves an isolated editor open for human IME testing. */
@SideOnly(Side.CLIENT)
public final class AcceptanceEditorFocus
{
    private static final int CHAPTER=0x6c000001,QUEST=0x6c000002;
    private static final String BODY="Header\nbody\nend";
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private int ticks,step,wait;
    private volatile int serverStep;
    private volatile Throwable failure;
    private GuiQuestTextEditor editor;
    private TextBox title,subtitle;
    private String savedBody;
    private GuiDescriptionComponent form;
    private Map<String,TextBox> fields;
    private GuiSelectItemStack items;
    private GuiSelectPonder ponder;
    private TextBox search;
    private PonderLink.Option selectedPonder;
    private ItemStack selectedItem;
    private static Boolean platformIme;
    private static int imeCalls;
    public static void install(){
        observePlatformIme();
        MinecraftForge.EVENT_BUS.register(new AcceptanceEditorFocus());
    }

    /** Observe and delegate to the actual baseline native handler; never replace it with a dummy. */
    @SuppressWarnings("unchecked") private static void observePlatformIme() {
        try {
            Class<?> handler=Class.forName("com.cleanroommc.client.IMEHandler");
            Field target=handler.getDeclaredField("instance");target.setAccessible(true);
            java.util.function.Consumer<Boolean> nativeHandler=(java.util.function.Consumer<Boolean>)target.get(null);
            Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");Field field=unsafeClass.getDeclaredField("theUnsafe");field.setAccessible(true);Object unsafe=field.get(null);
            Object base=unsafeClass.getMethod("staticFieldBase",Field.class).invoke(unsafe,target);
            long offset=(long)unsafeClass.getMethod("staticFieldOffset",Field.class).invoke(unsafe,target);
            java.util.function.Consumer<Boolean> observer=value->{nativeHandler.accept(value);platformIme=value;imeCalls++;};
            unsafeClass.getMethod("putObjectVolatile",Object.class,long.class,Object.class).invoke(unsafe,base,offset,observer);
        } catch(ReflectiveOperationException ex) {throw new AssertionError("Cannot observe real Cleanroom 0.4.0 native IME handler",ex);}
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;ticks++;
        try {
            if(failure!=null)throw new AssertionError("Focus server",failure);
            if(ticks>1800)throw new AssertionError("Focus timeout at "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu) {
                step=1;mc.launchIntegratedServer("focus-acceptance","中文输入测试",new WorldSettings(18,GameType.CREATIVE,false,false,WorldType.FLAT));
            } else if(step==1&&mc.player!=null&&ClientQuestFile.exists()) {
                step=2;server(()->{
                    AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="中文输入测试";chapter.onCreated();
                    Quest quest=new Quest(chapter);quest.id=QUEST;quest.title="原标题";quest.description.addAll(List.of(BODY.split("\n")));quest.onCreated();
                    var data=EditHistory.snapshot(quest);data.setLong("focus_unknown",9000000001L);quest.readDataPreservingUnknown(data);
                    file.refreshIDMap();file.clearCachedData();var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                });
            } else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(QUEST)!=null&&ClientQuestFile.INSTANCE.canEdit()) {
                if(!Boolean.getBoolean("ftbquests.acceptance.skipchapters")) chapterClicks();
                openEditor();
                // Deliberately leave the last rendered pointer over the title. A click may arrive before the next frame.
                editor.updateGui(title.getX()+4,title.getY()+4,0);
                click(editor.source.getX()+4,editor.source.getY()+18);
                focus(false,false,true);
                AcceptanceMod.require(editor.source.buffer.cursor()==7,"Body click used the previous frame's title coordinates: cursor="+editor.source.buffer.cursor());
                queue("正文中文");wait=ticks;step=3;
            } else if(step==3&&ticks-wait>4) {
                AcceptanceMod.require(editor.source.buffer.text().equals("Header\n正文中文body\nend"),"Chinese did not enter clicked body line");
                AcceptanceMod.require(title.getText().equals("原标题")&&subtitle.getText().isEmpty(),"Body Chinese leaked into title or subtitle");
                checks.put("wrapper_click_uses_event_coordinates_and_chinese_stays_in_body",true);
                savedBody=editor.source.buffer.text();click(title.getX()+4,title.getY()+4);focus(true,false,false);
                queue("标题中文");wait=ticks;step=4;
            } else if(step==4&&ticks-wait>4) {
                AcceptanceMod.require(title.getText().equals("标题中文原标题")&&editor.source.buffer.text().equals(savedBody),"Title input changed the body");
                click(subtitle.getX()+4,subtitle.getY()+4);focus(false,true,false);queue("副标题中文");wait=ticks;step=5;
            } else if(step==5&&ticks-wait>4) {
                AcceptanceMod.require(subtitle.getText().equals("副标题中文")&&title.getText().equals("标题中文原标题")&&editor.source.buffer.text().equals(savedBody),"Subtitle focus leaked text");
                checks.put("body_title_subtitle_clicks_are_exclusive",true);
                click(title.getX()+4,title.getY()+4);editor.setSize(420,300);editor.refreshWidgets();wait=ticks;step=6;
            } else if(step==6&&ticks-wait>4) {
                focus(true,false,false);queue("重排");wait=ticks;step=7;
            } else if(step==7&&ticks-wait>4) {
                AcceptanceMod.require(title.getText().equals("重排标题中文原标题")&&editor.source.buffer.text().equals(savedBody),"Relayout redirected title input into body");
                checks.put("relayout_preserves_one_active_field",true);
                editor.keyPressed(Keyboard.KEY_TAB,'\0');focus(false,true,false);
                editor.keyPressed(Keyboard.KEY_TAB,'\0');focus(false,false,true);
                editor.setSize(editor.getScreen().getScaledWidth(),editor.getScreen().getScaledHeight());editor.refreshWidgets();wait=ticks;step=8;
            } else if(step==8&&ticks-wait>4) {
                click(editor.preview.getX()+8,editor.preview.getY()+8);focus(false,false,false);queue("不应写入");wait=ticks;step=9;
            } else if(step==9&&ticks-wait>4) {
                AcceptanceMod.require(editor.source.buffer.text().equals(savedBody)&&title.getText().equals("重排标题中文原标题")&&subtitle.getText().equals("副标题中文"),"Preview click kept a hidden text focus");
                checks.put("preview_click_releases_text_input",true);
                click(editor.source.getX()+4,editor.source.getY()+30);focus(false,false,true);
                queue("末行𠀀");wait=ticks;step=10;
            } else if(step==10&&ticks-wait>4) {
                savedBody="Header\n正文中文body\n末行𠀀end";
                AcceptanceMod.require(editor.source.buffer.text().equals(savedBody),"Body refocus lost target line or supplementary Han");
                editor.source.undo();AcceptanceMod.require(!editor.source.buffer.text().contains("𠀀"),"Undo split supplementary Han");editor.source.redo();
                AcceptanceMod.require(editor.source.buffer.text().equals(savedBody),"Redo changed body focus text");editor.save(true);wait=ticks;step=11;
            } else if(step==11&&ticks-wait>15) {
                step=12;server(()->{
                    var quest=ServerQuestFile.INSTANCE.getQuest(QUEST);
                    AcceptanceMod.require(quest.title.equals("重排标题中文原标题")&&quest.subtitle.equals("副标题中文"),"Title/subtitle network save changed");
                    AcceptanceMod.require(new DescriptionDocument(quest.description).text().equals(savedBody),"Body network save changed");
                    AcceptanceMod.require(EditHistory.snapshot(quest).getLong("focus_unknown")==9000000001L,"Focus edit lost unknown fields");
                    AcceptanceMod.roundTrip();serverStep=2;
                });
            } else if(step==12&&serverStep==2) {
                checks.put("chinese_focus_network_disk_roundtrip_unknown_fields",true);
                openEditor();
                clickButton(editor,GuiQuestTextEditor.tr("quest"));
                form=(GuiDescriptionComponent)((GuiWrapper)mc.currentScreen).getGui();
                @SuppressWarnings("unchecked") Map<String,TextBox> inputs=(Map<String,TextBox>)read(form,GuiDescriptionComponent.class,"inputs");fields=inputs;
                clickBox(fields.get("text"));queue("组件中文");wait=ticks;step=13;
            } else if(step==13&&ticks-wait>4) {
                AcceptanceMod.require(form.field("text").equals("组件中文")&&form.field("value").isEmpty()&&form.field("hover").isEmpty(),"Component text leaked into another field");
                clickBox(fields.get("value"));queue("6c000002");wait=ticks;step=14;
            } else if(step==14&&ticks-wait>4) {
                AcceptanceMod.require(form.field("text").equals("组件中文")&&form.field("value").equals("6c000002"),"Component value input replaced label");
                clickBox(fields.get("hover"));queue("悬停中文");wait=ticks;step=15;
            } else if(step==15&&ticks-wait>4) {
                AcceptanceMod.require(form.field("hover").equals("悬停中文")&&form.field("text").equals("组件中文")&&form.field("value").equals("6c000002"),"Component hover input leaked");
                fields.get("text").setFocused(true);
                AcceptanceMod.require(fields.values().stream().filter(TextBox::isFocused).count()==1&&form.getFocusedTextInput()==fields.get("text"),"Programmatic focus left multiple component fields active");
                checks.put("component_clicks_and_programmatic_focus_are_exclusive",true);
                clickButton(form,GuiQuestTextEditor.tr("choose_quest"));
                GuiButtonListBase picker=(GuiButtonListBase)((GuiWrapper)mc.currentScreen).getGui();search=(TextBox)read(picker,GuiButtonListBase.class,"searchBox");
                clickBox(search);queue("原标题");wait=ticks;step=16;
            } else if(step==16&&ticks-wait>6) {
                AcceptanceMod.require(search.getText().equals("原标题")&&form.field("text").equals("组件中文"),"Quest selector search leaked into the component");
                ((GuiWrapper)mc.currentScreen).getGui().onBack();
                AcceptanceMod.require(((GuiWrapper)mc.currentScreen).getGui()==form,"Quest selector cancel did not return to component");
                clickButton(form,I18n.format("gui.cancel"));
                AcceptanceMod.require(((GuiWrapper)mc.currentScreen).getGui()==editor&&editor.source.buffer.text().equals(savedBody),"Component cancel changed the body");
                AcceptanceMod.require(Keyboard.areRepeatEventsEnabled(),"Closing component disabled key repeat in the returned editor");
                checks.put("quest_selector_chinese_and_cancel_return_preserve_draft",true);
                items=new GuiSelectItemStack(editor,stack->selectedItem=stack);items.openGui();search=(TextBox)read(items,GuiSelectItemStack.class,"searchBox");
                search.setText("A𠀀B",false);search.setCursorPosition(3);search.keyPressed(Keyboard.KEY_LEFT,'\0');search.writeText("中");
                AcceptanceMod.require(search.getText().equals("A中𠀀B"),"Left arrow split supplementary Han in single-line input");
                search.setText("A𠀀B",false);search.setCursorPosition(1);search.keyPressed(Keyboard.KEY_RIGHT,'\0');search.writeText("文");
                AcceptanceMod.require(search.getText().equals("A𠀀文B"),"Right arrow split supplementary Han in single-line input");
                checks.put("single_line_arrow_keys_keep_supplementary_han_whole",true);
                search.setText("",false);search.setCursorPosition(0);clickBox(search);queue("铁锭");wait=ticks;step=17;
            } else if(step==17&&ticks-wait>20&&com.feed_the_beast.ftblib.client.CreativeItemIndex.complete()) {
                AcceptanceMod.require(search.getText().equals("铁锭")&&hasIron(),"Clicked item search did not accept Chinese");
                search.setText("",false);search.setCursorPosition(0);queue("tieding");wait=ticks;step=18;
            } else if(step==18&&ticks-wait>20) {
                AcceptanceMod.require(search.getText().equals("tieding")&&hasIron(),"Item pinyin search regressed");
                checks.put("item_selector_chinese_pinyin_and_return_preserve_draft",true);
                ItemStack inventoryStack=new ItemStack(Items.IRON_INGOT,7);inventoryStack.setStackDisplayName("背包铁锭");
                inventoryStack.getTagCompound().setString("search_test","keep_nbt");mc.player.inventory.setInventorySlotContents(0,inventoryStack);
                Panel tabs=(Panel)read(items,GuiSelectItemStack.class,"tabs");Widget mode=tabs.widgets.get(0);click(mode.getX()+5,mode.getY()+5);
                wait=ticks;step=181;
            } else if(step==181&&ticks-wait>10) {
                Panel stacks=(Panel)read(items,GuiSelectItemStack.class,"panelStacks");Widget found=null;
                for(Widget widget:stacks.widgets){ItemStack stack=(ItemStack)read(widget,widget.getClass(),"stack");if(stack.hasTagCompound()&&stack.getTagCompound().getString("search_test").equals("keep_nbt")){found=widget;break;}}
                AcceptanceMod.require(found!=null,"Inventory pinyin search did not find the named NBT stack");click(found.getX()+4,found.getY()+4);
                // Restore catalog mode before accepting; the selected stack must remain an independent copy.
                Panel tabs=(Panel)read(items,GuiSelectItemStack.class,"tabs");Widget mode=tabs.widgets.get(0);click(mode.getX()+5,mode.getY()+5);
                clickButton(items,I18n.format("gui.accept"));
                AcceptanceMod.require(selectedItem!=null&&selectedItem.getItem()==Items.IRON_INGOT&&selectedItem.getCount()==7&&selectedItem.getTagCompound().getString("search_test").equals("keep_nbt"),"Inventory selection lost count or NBT across mode switch");
                AcceptanceMod.require(editor.source.buffer.text().equals(savedBody),"Item selector return changed the body");
                checks.put("inventory_pinyin_selection_and_mode_switch_preserve_count_and_nbt",true);
                ponder=new GuiSelectPonder(editor,List.of(new PonderLink.Option("minecraft:compass#ftbquests:focus","能源核心结构搭建",new ItemStack(Items.COMPASS))),option->selectedPonder=option);
                ponder.openGui();ponder.focus();search=(TextBox)read(ponder,GuiButtonListBase.class,"searchBox");clickBox(search);queue("nyhxjgdj");wait=ticks;step=19;
            } else if(step==19&&ticks-wait>20) {
                Panel choices=(Panel)read(ponder,GuiButtonListBase.class,"panelButtons");
                Widget choice=choices.widgets.stream().filter(w->w.getTitle().equals("能源核心结构搭建")).findFirst().orElseThrow();
                click(choice.getX()+4,choice.getY()+4);
                AcceptanceMod.require(selectedPonder!=null&&selectedPonder.value().equals("minecraft:compass#ftbquests:focus")&&editor.source.buffer.text().equals(savedBody),"Ponder selector input or return changed the editor");
                checks.put("ponder_initials_selection_and_return_preserve_draft",true);
                click(editor.source.getX()+4,editor.source.getY()+18);
                editor.onBack(); // Saved draft is unchanged, so this closes without a confirmation.
                openEditor();click(editor.source.getX()+4,editor.source.getY()+18);
                editor.openContextMenu(List.of(new ContextMenuItem("测试菜单",com.feed_the_beast.ftblib.lib.icon.Icon.EMPTY,()->{})));
                focus(false,false,false);queue("菜单不应写入");wait=ticks;step=20;
            } else if(step==20&&ticks-wait>4) {
                AcceptanceMod.require(editor.source.buffer.text().equals(savedBody),"Context menu allowed text into the underlying editor");
                editor.keyPressed(Keyboard.KEY_ESCAPE,'\0');focus(false,false,true);
                checks.put("modal_context_menu_blocks_text_and_escape_restores_body_focus",true);
                checks.put("native_ime_engine_keystrokes_not_automated",true);
                Files.writeString(Path.of("focus-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                AcceptanceMod.report("PASS","Native Cleanroom IME focus notifications; "+(Boolean.getBoolean("ftbquests.acceptance.skipchapters")?"chapter checks skipped; ":"chapter right-click edit; ")+"wrapper clicks; exclusive title/subtitle/body; relayout; selectors; Chinese network and disk roundtrip",null);
                if(Boolean.getBoolean("ftbquests.acceptance.manual")) {
                    openEditor();editor.source.buffer.selectAll();editor.source.insert("# 中文输入测试\n\n请点击这里输入中文，再切换标题、副标题测试。\n\n当前为独立测试存档。");
                    click(editor.source.getX()+4,editor.source.getY()+30);
                    wait=ticks;step=21;
                } else { step=100;mc.shutdown(); }
            } else if(step==21&&ticks-wait>3) {
                ScreenShotHelper.saveScreenshot(mc.gameDir,"manual-editor-ready.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
                Files.writeString(Path.of("manual-ready.json"),"{\"ready\":true,\"screen\":\"GuiQuestTextEditor\",\"world\":\"focus-acceptance\"}\n");step=100;
            }
        } catch(Throwable ex){AcceptanceMod.report("FAIL","Editor focus stage "+step,ex);step=100;mc.shutdown();}
    }
    private void openEditor() throws Exception {
        TextEditorFrame.open(ClientQuestFile.INSTANCE.getQuest(QUEST));editor=(GuiQuestTextEditor)((GuiWrapper)mc.currentScreen).getGui();
        title=(TextBox)field("title");subtitle=(TextBox)field("subtitle");
    }
    private void chapterClicks()throws Exception {
        var file=ClientQuestFile.INSTANCE;var chapter=file.getChapter(CHAPTER);GuiQuestTree tree=file.questTreeGui;
        tree.open(chapter,false);
        ButtonChapter tab=(ButtonChapter)tree.chapterPanel.widgets.stream().filter(w->w instanceof ButtonChapter b&&b.chapter==chapter).findFirst().orElseThrow();
        tree.chapterPanel.setScrollY(Math.max(0,tab.posY-20));
        tree.chapterPanel.setOffset(true);int x=tab.getX()+8,y=tab.getY()+8;tree.chapterPanel.setOffset(false);
        tree.chapterHoverPanel.chapter=null;tree.chapterHoverPanel.refreshWidgets();
        click(x,y,1);
        AcceptanceMod.require(tree.contextMenu instanceof ContextMenu,"Collapsed chapter icon right-click did not open a menu before hover tick");
        ContextMenu menu=(ContextMenu)tree.contextMenu;
        ContextMenu.CButton edit=(ContextMenu.CButton)menu.widgets.stream().filter(w->w.getTitle().equals(I18n.format("selectServer.edit"))).findFirst().orElseThrow(()->new AssertionError("No chapter edit item: "+menu.items.stream().map(i->i.title).toList()+", target="+tab.posY+", scroll="+tree.chapterPanel.getScrollY()+", pointer="+tree.getMouseX()+","+tree.getMouseY()));
        menu.setScrollY(Math.max(0,edit.posY-menu.height/2));menu.setOffset(true);x=edit.getX()+5;y=edit.getY()+5;menu.setOffset(false);
        click(x,y,0);
        AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper&&((GuiWrapper)mc.currentScreen).getGui() instanceof GuiEditConfig,"Chapter context edit did not open config");
        checks.put("chapter_collapsed_icon_real_right_click_opens_edit_config",true);
        ((GuiWrapper)mc.currentScreen).getGui().closeGui(true);
        tree.open(chapter,false);tree.chapterHoverPanel.chapter=tab;tree.chapterHoverPanel.refreshWidgets();tree.updateGui(x,y,0);
        ButtonExpandedChapter expanded=(ButtonExpandedChapter)tree.chapterHoverPanel.widgets.stream().filter(w->w instanceof ButtonExpandedChapter b&&b.chapter.id==CHAPTER).findFirst().orElseThrow();
        click(expanded.getX()+5,expanded.getY()+5,1);
        AcceptanceMod.require(tree.contextMenu instanceof ContextMenu,"Expanded chapter right-click menu regressed");tree.closeContextMenu();
        file.editingMode=false;
        try {
            tab.onClicked(com.feed_the_beast.ftblib.lib.util.misc.MouseButton.RIGHT);
            AcceptanceMod.require(tree.contextMenu==null,"Non-editor got a chapter edit menu");
        } finally {file.editingMode=true;}
        checks.put("chapter_expanded_menu_and_non_editor_permissions",true);
    }
    private Object field(String name)throws Exception {Field f=GuiQuestTextEditor.class.getDeclaredField(name);f.setAccessible(true);return f.get(editor);}
    private static Object read(Object object,Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void clickBox(TextBox box)throws Exception {
        click(box.getX()+4,box.getY()+4);
        AcceptanceMod.require(box.isFocused()&&Boolean.TRUE.equals(platformIme),"Clicked search/component input did not activate platform IME");
        checks.put("component_and_selector_fields_activate_platform_ime",true);
    }
    private void clickButton(Panel panel,String title)throws Exception {
        Widget found=null;for(Widget widget:panel.widgets)if(widget instanceof Button&&widget.getTitle().equals(title)){found=widget;break;}
        if(found==null)for(Widget widget:panel.widgets)if(widget instanceof Panel child){try{clickButton(child,title);return;}catch(NoSuchElementException ignored){}}
        if(found==null)throw new NoSuchElementException(title);click(found.getX()+4,found.getY()+4);
    }
    private boolean hasIron()throws Exception {
        Panel stacks=(Panel)read(items,GuiSelectItemStack.class,"panelStacks");
        for(Widget widget:stacks.widgets)try{if(((ItemStack)read(widget,widget.getClass(),"stack")).getItem()==Items.IRON_INGOT)return true;}catch(NoSuchFieldException ignored){}
        return false;
    }
    private void focus(boolean a,boolean b,boolean c) {
        AcceptanceMod.require(title.isFocused()==a&&subtitle.isFocused()==b&&editor.source.isFocused()==c,
                "Wrong focus: title="+title.isFocused()+", subtitle="+subtitle.isFocused()+", source="+editor.source.isFocused());
        AcceptanceMod.require(platformIme!=null&&platformIme==(a||b||c),"Cleanroom native IME was not notified: state="+platformIme+", calls="+imeCalls);
        checks.put("cleanroom_platform_ime_focus_matches_editor_field",true);
    }
    private void click(int x,int y)throws Exception {
        click(x,y,0);
    }
    private void click(int x,int y,int button)throws Exception {
        // GuiWrapper receives event coordinates before the next drawScreen/updateGui.
        Method clicked=Arrays.stream(GuiWrapper.class.getDeclaredMethods()).filter(m->Modifier.isProtected(m.getModifiers())&&Modifier.isFinal(m.getModifiers())&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{int.class,int.class,int.class})).findFirst().orElseThrow();
        clicked.setAccessible(true);clicked.invoke(mc.currentScreen,x,y,button);
        ((GuiWrapper)mc.currentScreen).getGui().mouseReleased(com.feed_the_beast.ftblib.lib.util.misc.MouseButton.get(button));
    }
    private static void queue(String text)throws Exception {
        Method method=Keyboard.class.getMethod("addCharEvent",int.class,char.class);for(char c:text.toCharArray())method.invoke(null,0,c);
    }
    private void server(Checked action){mc.getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable ex){failure=ex;}});}
    private interface Checked {void run()throws Exception;}
}
