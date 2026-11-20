package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.GuiWrapper;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.ClientQuestFile;
import com.feed_the_beast.ftbquests.client.VisualConfig;
import com.feed_the_beast.ftbquests.gui.tree.ButtonChapter;
import com.feed_the_beast.ftbquests.gui.tree.ButtonExpandedChapter;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.ServerQuestFile;
import com.feed_the_beast.ftbquests.quest.task.CheckmarkTask;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcceptanceGuiEntryPoints
{
    private static final int CHAPTER = 0x6B000001, CHILD = 0x6B000002;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> diagnostics = new LinkedHashMap<>();
    private final String[] commands = {
        "/pondergui", "/pondergui demo ponder:examples/furnace",
        "/pondergui edit ponder:examples/furnace", "/pondergui reload",
        "/pondergui demo missing:no_such_tutorial", "/pondergui invalid"
    };
    private final String[] screens = {
        "GuiTutorialLibraryScreen", "GuiDemoScreen", "GuiTutorialEditorScreen", "", "", ""
    };
    private GuiQuestTree tree;
    private int ticks, stage, waiting, command, mode, pointerX, pointerY;
    private volatile boolean synced;
    private volatile Throwable failure;

    public static void install()
    {
        MinecraftForge.EVENT_BUS.register(new AcceptanceGuiEntryPoints());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || stage == 100) return;
        try
        {
            if (++ticks > 1800) throw new AssertionError("GUI entry timeout at " + stage);
            if (failure != null) throw new AssertionError("Fixture setup failed", failure);
            if (stage == 0 && mc.currentScreen instanceof GuiMainMenu)
            {
                stage = 1;
                mc.launchIntegratedServer("gui-entry-acceptance", "GUI entry regression",
                    new WorldSettings(21, GameType.CREATIVE, false, false, WorldType.FLAT));
            }
            else if (stage == 1 && mc.player != null && ClientQuestFile.exists())
            {
                stage = 2;
                mc.getIntegratedServer().addScheduledTask(() -> {
                    try
                    {
                        AcceptanceMod.roundTrip();
                        var file = ServerQuestFile.INSTANCE;
                        Chapter parent = new Chapter(file);
                        parent.id = CHAPTER;
                        parent.title = "章节名称显示验收";
                        parent.onCreated();
                        Chapter child = new Chapter(file);
                        child.id = CHILD;
                        child.title = "子章节名称显示验收";
                        child.group = parent;
                        child.onCreated();
                        Quest quest = new Quest(child);
                        quest.id = CHILD + 1;
                        quest.title = "章节悬停";
                        quest.onCreated();
                        CheckmarkTask task = new CheckmarkTask(quest);
                        task.id = CHILD + 2;
                        task.onCreated();
                        file.refreshIDMap();
                        file.clearCachedData();
                        var player = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                        ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));
                        synced = true;
                    }
                    catch (Throwable error) { failure = error; }
                });
            }
            else if (stage == 2 && synced && ClientQuestFile.INSTANCE.getChapter(CHILD) != null)
            {
                tree = ClientQuestFile.INSTANCE.questTreeGui;
                stage = Loader.isModLoaded("ponder-legacy") ? 3 : 5;
            }
            else if (stage == 3)
            {
                SubmittedChat chat = new SubmittedChat(commands[command]);
                mc.displayGuiScreen(chat);
                chat.submit();
                checks.put("chat_closes_after_command_" + command, mc.currentScreen == null);
                waiting = ticks;
                stage = 4;
            }
            else if (stage == 4 && ticks - waiting > 3)
            {
                String screen = mc.currentScreen == null ? "" : mc.currentScreen.getClass().getSimpleName();
                checks.put("command_" + command + "_opens_expected_screen", screens[command].equals(screen));
                if (!screen.isEmpty()) screenshot("pondergui-command-" + command + ".png");
                mc.displayGuiScreen(null);
                stage = ++command < commands.length ? 3 : 5;
            }
            else if (stage == 5)
            {
                ClientQuestFile.INSTANCE.editingMode = mode == 1;
                VisualConfig.chapterPanelAnimation = mode != 2;
                tree.open(ClientQuestFile.INSTANCE.getChapter(CHAPTER), false);
                tree.updateGui(0, 0, 0);
                ButtonChapter tab = (ButtonChapter) tree.chapterPanel.widgets.stream()
                    .filter(widget -> widget instanceof ButtonChapter button && button.chapter.id == CHAPTER)
                    .findFirst().orElseThrow();
                tree.chapterPanel.setScrollY(Math.max(0, tab.posY - 30));
                tree.chapterPanel.setOffset(true);
                pointerX = tab.getX() + 8;
                pointerY = tab.getY() + 8;
                tree.chapterPanel.setOffset(false);
                mc.displayGuiScreen(new GuiWrapper(tree) {
                    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                        super.drawScreen(pointerX, pointerY, partialTicks);
                    }
                });
                tree.chapterHoverPanel.chapter = null;
                tree.chapterHoverPanel.refreshWidgets();
                tree.updateGui(pointerX, pointerY, 0);
                tree.tick();
                checks.put("mode_" + mode + "_parent_tick_opens_chapter",
                    tree.chapterHoverPanel.chapter != null && tree.chapterHoverPanel.chapter.chapter.id == CHAPTER);
                waiting = ticks;
                stage = 6;
            }
            else if (stage == 6 && ticks - waiting > 15)
            {
                diagnostics.put("mode_" + mode, Map.of(
                    "requested_pointer", List.of(pointerX, pointerY),
                    "observed_pointer", List.of(tree.getMouseX(), tree.getMouseY(), Mouse.getX(), Mouse.getY()),
                    "chapter", tree.chapterHoverPanel.chapter == null ? "none" : tree.chapterHoverPanel.chapter.chapter.getTitle(),
                    "panel", List.of(tree.chapterHoverPanel.getX(), tree.chapterHoverPanel.getY(),
                        tree.chapterHoverPanel.width, tree.chapterHoverPanel.height),
                    "tabs", tree.chapterPanel.widgets.stream().map(widget ->
                        widget.getTitle() + " " + widget.isMouseOver() + " " + widget.posX + "," + widget.posY).toList(),
                    "widgets", tree.chapterHoverPanel.widgets.stream().map(widget ->
                        widget.getTitle() + " " + widget.width + "x" + widget.height).toList()));
                checks.put("mode_" + mode + "_parent_title_visible", titleVisible(CHAPTER));
                checks.put("mode_" + mode + "_child_title_visible", titleVisible(CHILD));
                screenshot("chapters-mode-" + mode + ".png");
                var child = tree.chapterHoverPanel.widgets.stream()
                    .filter(widget -> widget instanceof ButtonExpandedChapter button && button.chapter.id == CHILD)
                    .findFirst();
                if (child.isPresent()) {
                    Widget button = child.get();
                    tree.updateGui(button.getX() + button.width / 2, button.getY() + button.height / 2, 0);
                    tree.mousePressed(MouseButton.LEFT);
                }
                checks.put("mode_" + mode + "_child_title_selects_chapter",
                    tree.selectedChapter != null && tree.selectedChapter.id == CHILD);
                pointerX = tree.width - 15;
                pointerY = tree.height - 15;
                tree.updateGui(pointerX, pointerY, 0);
                waiting = ticks;
                stage = 7;
            }
            else if (stage == 7 && ticks - waiting > 18)
            {
                checks.put("mode_" + mode + "_chapter_closes_after_leaving", tree.chapterHoverPanel.chapter == null);
                stage = ++mode < 3 ? 5 : 8;
            }
            else if (stage == 8)
            {
                Files.writeString(Path.of("gui-entry-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                Files.writeString(Path.of("gui-entry-diagnostics.json"), new GsonBuilder().setPrettyPrinting().create().toJson(diagnostics));
                AcceptanceMod.require(checks.values().stream().allMatch(Boolean::booleanValue), "GUI entry checks: " + checks);
                AcceptanceMod.report("PASS", "Chat Enter command dispatch; chapter titles through parent ticks in browsing, editing and disabled animation", null);
                stage = 100;
                mc.shutdown();
            }
        }
        catch (Throwable error)
        {
            AcceptanceMod.report("FAIL", "GUI entry stage " + stage, error);
            stage = 100;
            mc.shutdown();
        }
    }

    private boolean titleVisible(int chapter)
    {
        for (Widget widget : tree.chapterHoverPanel.widgets)
        {
            if (widget instanceof ButtonExpandedChapter button && button.chapter.id == chapter)
                return !button.getTitle().isEmpty()
                    && button.width >= tree.getTheme().getStringWidth(button.getTitle()) + (button.hasIcon() ? 28 : 8);
        }
        return false;
    }

    private void screenshot(String name)
    {
        if (mc.currentScreen instanceof GuiWrapper wrapper && wrapper.getGui() == tree)
            mc.currentScreen.drawScreen(pointerX, pointerY, 0);
        ScreenShotHelper.saveScreenshot(mc.gameDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static final class SubmittedChat extends GuiChat
    {
        private SubmittedChat(String text) { super(text); }
        private void submit() throws IOException { keyTyped('\r', Keyboard.KEY_RETURN); }
    }
}
