package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.*;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** In-game file dialog, including on machines without native desktop dialog services. */
public final class GuiMediaFilePicker extends GuiBase
{
    private static final ExecutorService IO = MediaIO.worker("Quest file picker");
    private static Path lastImage, lastVideo;
    private final boolean video;
    private final Consumer<Path> accept;
    public final TextBox path;
    public final Panel files;
    private final PanelScrollBar scroll;
    private final SimpleTextButton go, up, home, game, select, cancel;
    private List<Entry> entries = List.of();
    private Path directory, selected;
    private int revision;
    private String status = "";
    private boolean failed, closed;
    private record Entry(Path path, boolean directory) {}
    public GuiMediaFilePicker(boolean video, Consumer<Path> accept)
    {
        this.video = video; this.accept = accept;
        path = new TextBox(this); path.charLimit = 32768;
        files = new Panel(this) {
            @Override public void addWidgets() {
                for (Entry entry : entries) add(new SimpleTextButton(this, (entry.directory ? "[+] " : "") + entry.path.getFileName(), Icon.EMPTY) {
                    @Override public void onClicked(MouseButton button) { if (button.isLeft()) { if (entry.directory) browse(entry.path); else { selected = entry.path; path.setText(selected.toString(), false); } } }
                    @Override public void drawBackground(Theme theme, int x, int y, int w, int h) { if (entry.path.equals(selected)) Color4I.rgb(0x36526A).draw(x,y,w,h); else super.drawBackground(theme,x,y,w,h); }
                });
            }
            @Override public void alignWidgets() { int y = 0; for (Widget widget : widgets) { widget.setPosAndSize(0,y,width,20); y += 21; } scroll.setMaxValue(y); }
        };
        scroll = new PanelScrollBar(this, files);
        go = button("open", this::openPath);
        up = button("up", () -> { if (directory != null && directory.getParent() != null) browse(directory.getParent()); });
        home = button("home", () -> browse(Paths.get(System.getProperty("user.home"))));
        game = button("game", () -> browse(Minecraft.getMinecraft().gameDir.toPath()));
        select = button("select", this::openPath);
        cancel = button("cancel", () -> { closed = true; closeGui(true); });
        Path initial = video ? lastVideo : lastImage;
        browse(initial == null ? Minecraft.getMinecraft().gameDir.toPath() : initial);
    }
    public static String tr(String key, Object... args) { return I18n.format("ftbquests.media." + key, args); }
    private SimpleTextButton button(String key, Runnable action) { return new SimpleTextButton(this,tr(key),Icon.EMPTY) {
        @Override public void onClicked(MouseButton button) { if (button.isLeft()) action.run(); }
        @Override public boolean renderTitleInCenter() { return true; }
    }; }
    public void browse(Path requested)
    {
        int current = ++revision; Path target = requested.toAbsolutePath().normalize();
        selected = null; status = tr("listing"); failed = false; path.setText(target.toString(),false);
        try { IO.execute(() -> {
            try
            {
                List<Entry> result = new ArrayList<>(); boolean truncated = false;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(target))
                {
                    for (Path child : stream)
                    {
                        boolean folder = Files.isDirectory(child);
                        if (folder || QuestMedia.selectable(child,video)) result.add(new Entry(child,folder));
                        if (result.size() >= 4096) { truncated = true; break; }
                    }
                }
                result.sort(Comparator.<Entry,Boolean>comparing(e -> !e.directory).thenComparing(e -> e.path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
                String hint = tr(truncated ? "truncated" : video ? "video_hint" : "image_hint");
                Minecraft.getMinecraft().addScheduledTask(() -> { if (current != revision || closed) return; directory = target; entries = result; status = hint; files.setScrollY(0); scroll.setValue(0); files.refreshWidgets(); });
            }
            catch (Exception ex) { Minecraft.getMinecraft().addScheduledTask(() -> { if (current == revision && !closed) { status = tr("folder_error"); failed = true; } }); }
        }); } catch (RejectedExecutionException ex) { status = tr("error.busy"); failed = true; }
    }
    public void openPath()
    {
        Path chosen;
        try { String text = path.getText().trim(); if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1,text.length()-1); chosen = Paths.get(text); if (!chosen.isAbsolute() && directory != null) chosen = directory.resolve(chosen); chosen = chosen.toAbsolutePath().normalize(); }
        catch (RuntimeException ex) { status = tr("error.invalid"); failed = true; return; }
        // Directory/file probing is kept off the render thread as well.
        final Path target = chosen; int current = ++revision;
        try { IO.execute(() -> { boolean folder = Files.isDirectory(target), file = Files.isRegularFile(target); Minecraft.getMinecraft().addScheduledTask(() -> {
            if (current != revision || closed) return;
            if (folder) browse(target);
            else if (file && QuestMedia.selectable(target,video)) { if (video) lastVideo = target.getParent(); else lastImage = target.getParent(); closed = true; closeGui(true); accept.accept(target); }
            else { failed = true; status = tr("error.format"); }
        }); }); } catch (RejectedExecutionException ex) { status = tr("error.busy"); failed = true; }
    }
    @Override public boolean onInit() { setSize(Math.min(590,getScreen().getScaledWidth()-16),Math.min(360,getScreen().getScaledHeight()-16)); return true; }
    @Override public void addWidgets() { add(path); add(go); add(up); add(home); add(game); add(files); add(scroll); add(select); add(cancel); }
    @Override public void alignWidgets() {
        path.setPosAndSize(8,28,width-82,18); go.setPosAndSize(width-68,28,60,18);
        up.setPosAndSize(8,50,66,18); home.setPosAndSize(80,50,70,18); game.setPosAndSize(156,50,86,18);
        files.setPosAndSize(8,74,width-24,height-130); files.alignWidgets(); scroll.setPosAndSize(width-16,74,8,height-130);
        select.setPosAndSize(width-160,height-26,76,18); cancel.setPosAndSize(width-78,height-26,70,18);
    }
    @Override public boolean mousePressed(MouseButton button) { path.setFocused(path.isMouseOver()); if (button == MouseButton.BACK) { closed = true; } return super.mousePressed(button); }
    @Override public boolean keyPressed(int key, char character) { if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { openPath(); return true; } return super.keyPressed(key,character); }
    @Override public boolean onClosedByKey(int key) { if (key == Keyboard.KEY_ESCAPE) { closed = true; closeGui(true); } return false; }
    @Override public void onBack() { closed = true; closeGui(true); }
    @Override public void tick() { super.tick(); Keyboard.enableRepeatEvents(path.isFocused()); }
    @Override public void drawBackground(Theme theme,int x,int y,int w,int h) {
        Color4I.rgb(0x18232D).draw(x,y,w,h); theme.drawString(tr(video ? "choose_video" : "choose_image"),x+8,y+9,Color4I.WHITE,0);
        theme.drawString(theme.trimStringToWidth(status,w-16),x+8,y+h-45,failed ? Color4I.RED : Color4I.GRAY,0);
    }
}
