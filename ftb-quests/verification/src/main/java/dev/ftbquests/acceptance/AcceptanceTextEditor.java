package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.texteditor.*;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.input.Keyboard;
import java.nio.file.*;
import java.util.*;

/** Real menu and input dispatch against a packaged jar on the production loader. */
@SideOnly(Side.CLIENT)
public final class AcceptanceTextEditor
{
    private static final int CHAPTER = 0x6d000001, QUEST = 0x6d000002;
    private static final List<String> LEGACY = List.of("  &a旧正文  ", "**保持字面**", "{\"type\":\"future\",\"value\":{\"kept\":[1,2]}}", "", "");
    private static final String MARKDOWN = "# 任务正文编辑\n支持 **加粗**、*斜体* 和 ~~删除线~~。\n\n## 操作步骤\n- 直接在游戏内编辑\n- 右侧实时预览\n> 保留原版任务与扩展数据\n\n[任务链接](quest:6d000002)  [脚本动作](zs:guide/open)\n\n| 输入 | 输出 |\n| --- | --- |\n| 铁矿石 | 铁锭 |\n\n```zs\n  // 保持代码缩进\n  print(\"hello\");\n```\n\n";
    private int ticks, step, wait;
    private volatile int serverStep;
    private volatile Throwable failure;
    private GuiQuestTree tree;
    private GuiQuestTextEditor editor;
    private final Map<String, Object> checks = new LinkedHashMap<>();
    public static void install() { MinecraftForge.EVENT_BUS.register(new AcceptanceTextEditor()); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || step == 100) return;
        Minecraft mc = Minecraft.getMinecraft(); ticks++;
        try
        {
            if (failure != null) throw new AssertionError("Server text-editor check", failure);
            if (ticks > 2000) throw new AssertionError("Text editor timed out: " + step);
            if (step == 0 && mc.currentScreen instanceof GuiMainMenu)
            {
                mc.getLanguageManager().setCurrentLanguage(mc.getLanguageManager().getLanguages().stream().filter(l -> l.getLanguageCode().equals("zh_cn")).findFirst().orElseThrow());
                mc.gameSettings.language = "zh_cn"; mc.refreshResources();
                step = 1; mc.launchIntegratedServer("text-editor-acceptance", "Text Editor", new WorldSettings(2, GameType.CREATIVE, false, false, WorldType.FLAT));
            }
            else if (step == 1 && mc.player != null && ClientQuestFile.exists())
            {
                step = 2; mc.getIntegratedServer().addScheduledTask(() -> {
                    try {
                        AcceptanceMod.roundTrip(); ServerQuestFile file = ServerQuestFile.INSTANCE;
                        Chapter chapter = new Chapter(file); chapter.id = CHAPTER; chapter.title = "正文编辑验收"; chapter.onCreated();
                        Quest quest = new Quest(chapter); quest.id = QUEST; quest.title = "旧任务标题"; quest.description.addAll(LEGACY); quest.onCreated();
                        var nbt = EditHistory.snapshot(quest); nbt.setLong("acceptance_unknown_field", 9_000_000_001L); quest.readDataPreservingUnknown(nbt);
                        file.refreshIDMap(); file.clearCachedData();
                        var player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                        NBTUtils.getPersistedData(player, true).setBoolean("ftbquests_editing_mode", true);
                        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player))); serverStep = 1;
                    } catch (Throwable ex) { failure = ex; }
                });
            }
            else if (step == 2 && serverStep == 1 && ClientQuestFile.INSTANCE.getQuest(QUEST) != null && ClientQuestFile.INSTANCE.canEdit())
            {
                tree = ClientQuestFile.INSTANCE.questTreeGui; tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST), false); wait = ticks; step = 3;
            }
            else if (step == 3 && ticks - wait > 10)
            {
                ButtonQuest button = (ButtonQuest)tree.questPanel.widgets.stream().filter(w -> w instanceof ButtonQuest && ((ButtonQuest)w).quest.id == QUEST).findFirst().orElseThrow();
                button.onClicked(MouseButton.RIGHT);
                ContextMenu menu = (ContextMenu)tree.contextMenu;
                AcceptanceMod.require(menu.items.stream().anyMatch(i -> i.title.equals("编辑依赖连线")), "Dependency-line localization missing");
                ContextMenu.CButton edit = (ContextMenu.CButton)menu.widgets.stream().filter(w -> w.getTitle().equals(I18n.format("ftbquests.gui.edit_text"))).findFirst().orElseThrow();
                click(tree, edit); editor = currentEditor(mc);
                AcceptanceMod.require(!editor.isMarkdown() && editor.draftLines().equals(LEGACY) && !editor.isDirty(), "Opening changed a legacy description");
                checks.put("context_menu_click_opens_native_editor", true); checks.put("legacy_open_is_lossless", true);
                editor.save(false); AcceptanceMod.require(ClientQuestFile.INSTANCE.getQuest(QUEST).description.equals(LEGACY), "Unchanged save changed legacy data");
                editor.source.buffer.selectAll(); editor.source.insert("cancelled draft\n\n"); editor.onBack();
                ContextMenu discard = (ContextMenu)editor.contextMenu;
                click(editor, discard.widgets.stream().filter(w -> w.getTitle().equals(GuiQuestTextEditor.tr("discard"))).findFirst().orElseThrow());
                AcceptanceMod.require(ClientQuestFile.INSTANCE.getQuest(QUEST).description.equals(LEGACY), "Discard mutated the quest");
                checks.put("cancel_keeps_legacy_data", true);
                TextEditorFrame.open(ClientQuestFile.INSTANCE.getQuest(QUEST)); editor = currentEditor(mc);
                click(editor, editor.widgets.stream().filter(w -> w.getTitle().equals(GuiQuestTextEditor.tr("mode_legacy"))).findFirst().orElseThrow());
                click(editor, editor.source);
                editor.source.buffer.selectAll(); editor.source.insert(MARKDOWN);
                int oldLength = editor.source.buffer.text().length();
                editor.keyPressed(Keyboard.KEY_RETURN, '\n'); editor.keyPressed(Keyboard.KEY_NONE, '中');
                AcceptanceMod.require(editor.source.buffer.text().length() == oldLength + 2, "Native text input was not handled");
                editor.source.undo(); editor.source.undo(); AcceptanceMod.require(editor.source.buffer.text().equals(MARKDOWN), "Editor undo did not restore draft");
                editor.source.redo(); editor.source.undo();
                checks.put("multiline_input_and_local_undo_redo", true);
                editor.source.buffer.select(0, false); editor.source.edit(() -> {}); wait = ticks; step = 4;
            }
            else if (step == 4 && ticks - wait > 12)
            {
                AcceptanceMod.require(editor.preview.widgets.size() >= 15, "Live preview did not render Markdown blocks");
                AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError() == 0, "Preview produced OpenGL error");
                shot(mc, "markdown-editor-wide.png");
                checks.put("live_preview_rendered", true);
                // The structured-component form must also be inside Minecraft and cancel safely.
                click(editor, editor.widgets.stream().filter(w -> w.getTitle().equals(GuiQuestTextEditor.tr("image"))).findFirst().orElseThrow());
                AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper && ((GuiWrapper)mc.currentScreen).getGui().getClass().getSimpleName().equals("GuiDescriptionComponent"), "Component editor opened outside the game");
                GuiBase form = ((GuiWrapper)mc.currentScreen).getGui();
                click(form, form.widgets.stream().filter(w -> w.getTitle().equals(I18n.format("gui.cancel"))).findFirst().orElseThrow());
                AcceptanceMod.require(currentEditor(mc).source.buffer.text().equals(MARKDOWN), "Component cancel changed source");
                checks.put("native_component_form_and_cancel", true);
                editor.setSize(420, 240); editor.refreshWidgets(); wait = ticks; step = 5;
            }
            else if (step == 5 && ticks - wait > 8)
            {
                AcceptanceMod.require(editor.width < 460, "Narrow editor case did not resize");
                shot(mc, "markdown-editor-narrow.png");
                click(editor, editor.widgets.stream().filter(w -> w.getTitle().equals(GuiQuestTextEditor.tr("preview"))).findFirst().orElseThrow()); wait = ticks; step = 6;
            }
            else if (step == 6 && ticks - wait > 8)
            {
                AcceptanceMod.require(editor.widgets.contains(editor.preview) && !editor.widgets.contains(editor.source), "Narrow preview tab did not switch");
                shot(mc, "markdown-preview-narrow.png");
                checks.put("narrow_source_preview_tabs", true);
                editor.save(true); wait = ticks; step = 7;
            }
            else if (step == 7 && ticks - wait > 20)
            {
                AcceptanceMod.require(new DescriptionDocument(ClientQuestFile.INSTANCE.getQuest(QUEST).description).text().equals(MARKDOWN), "Client did not keep saved Markdown");
                TextEditorFrame.open(ClientQuestFile.INSTANCE.getQuest(QUEST)); editor = currentEditor(mc);
                AcceptanceMod.require(editor.isMarkdown() && editor.source.buffer.text().equals(MARKDOWN) && !editor.isDirty(), "Saved Markdown did not reopen as source");
                editor.closeGui(true); tree.open(ClientQuestFile.INSTANCE.getQuest(QUEST), false); wait = ticks; step = 8;
                mc.getIntegratedServer().addScheduledTask(() -> {
                    try {
                        ServerQuestFile file = ServerQuestFile.INSTANCE; Quest quest = file.getQuest(QUEST);
                        AcceptanceMod.require(new DescriptionDocument(quest.description).text().equals(MARKDOWN), "Server lost Markdown or trailing newlines");
                        AcceptanceMod.require(EditHistory.snapshot(quest).getLong("acceptance_unknown_field") == 9_000_000_001L, "Text edit lost unknown quest data");
                        AcceptanceMod.require(ScriptTextActions.declared(quest, "guide/open"), "Markdown ZS link was not declared");
                        AcceptanceMod.roundTrip(); serverStep = 2;
                    } catch (Throwable ex) { failure = ex; }
                });
            }
            else if (step == 8 && serverStep == 2 && ticks - wait > 12)
            {
                shot(mc, "markdown-quest-view.png"); checks.put("save_network_reopen_and_disk_roundtrip", true); checks.put("unknown_quest_fields_preserved", true);
                Files.writeString(Paths.get("text-editor-checks.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(checks));
                AcceptanceMod.report("PASS", "Native editor menu click; Chinese labels; legacy open/save/cancel; multiline typing and undo/redo; wide and narrow Markdown preview; native component form; server save/reload; unknown fields and trailing lines", null);
                step = 100; mc.shutdown();
            }
        }
        catch (Throwable ex) { int at = step; step = 100; AcceptanceMod.report("FAIL", "Text editor step " + at, ex); mc.shutdown(); }
    }
    private static GuiQuestTextEditor currentEditor(Minecraft mc)
    {
        AcceptanceMod.require(mc.currentScreen instanceof GuiWrapper && ((GuiWrapper)mc.currentScreen).getGui() instanceof GuiQuestTextEditor, "Edit Text did not open the in-game editor");
        return (GuiQuestTextEditor)((GuiWrapper)mc.currentScreen).getGui();
    }
    private static void click(GuiBase gui, Widget widget)
    {
        gui.updateGui(widget.getX() + 3, widget.getY() + 3, 0); gui.mousePressed(MouseButton.LEFT); gui.mouseReleased(MouseButton.LEFT);
    }
    private static void shot(Minecraft mc, String name) { ScreenShotHelper.saveScreenshot(mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer()); }
}
