package com.feed_the_beast.ftblib.lib.gui;

import com.feed_the_beast.ftblib.FTBLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Native AWT text peers provide OS composition/candidate UI when GLFW cannot. */
@SideOnly(Side.CLIENT)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = FTBLib.MOD_ID, value = Side.CLIENT)
public final class NativeTextInput
{
    private static final AtomicBoolean OPEN = new AtomicBoolean();
    private static volatile Dialog active;
    private static volatile GuiScreen activeOwner;

    public static boolean isOpen() { return OPEN.get(); }

    public static boolean open(String value, boolean multiline, Consumer<String> accepted)
    {
        if (!OPEN.compareAndSet(false, true)) return true;
        Minecraft mc = Minecraft.getMinecraft(); GuiScreen owner = mc.currentScreen;
        String title = I18n.format("ftblib.text_input.title"), hint = I18n.format("ftblib.text_input.hint");
        String ok = I18n.format("gui.accept"), cancel = I18n.format("gui.cancel");
        EventQueue.invokeLater(() -> {
            try
            {
                if (GraphicsEnvironment.isHeadless()) throw new HeadlessException("No native desktop input available");
                Dialog dialog = new Dialog((Frame)null, title, false);
                active = dialog; activeOwner = owner;
                dialog.setName("ftblib-native-text-input");
                dialog.setLayout(new BorderLayout(8, 8));
                TextComponent input = multiline ? new TextArea(value, 15, 64, TextArea.SCROLLBARS_BOTH) : new java.awt.TextField(value, 42);
                input.setName("ftblib-native-text-value");
                input.setFont(new Font(Font.DIALOG, Font.PLAIN, 17)); input.enableInputMethods(true);
                dialog.add(new Label(hint), BorderLayout.NORTH); dialog.add(input, BorderLayout.CENTER);
                java.awt.Panel actions = new java.awt.Panel(new FlowLayout(FlowLayout.RIGHT));
                java.awt.Button accept = new java.awt.Button(ok), dismiss = new java.awt.Button(cancel);
                actions.add(accept); actions.add(dismiss); dialog.add(actions, BorderLayout.SOUTH);
                AtomicBoolean finished = new AtomicBoolean();
                Consumer<Boolean> finish = save -> {
                    if (!finished.compareAndSet(false, true)) return;
                    if (save) input.getInputContext().endComposition();
                    String result = input.getText(); dialog.dispose(); active = null; activeOwner = null; OPEN.set(false);
                    mc.addScheduledTask(() -> {
                        // A delayed dialog must never write into another screen or quest.
                        if (save && mc.currentScreen == owner) accepted.accept(result);
                    });
                };
                accept.addActionListener(e -> finish.accept(true)); dismiss.addActionListener(e -> finish.accept(false));
                dialog.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { finish.accept(false); } });
                input.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) { e.consume(); finish.accept(false); }
                        else if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) { e.consume(); finish.accept(true); }
                    }
                });
                dialog.pack(); dialog.setMinimumSize(new Dimension(460, multiline ? 300 : 140)); dialog.setLocationRelativeTo(null);
                dialog.setVisible(true); input.requestFocusInWindow(); input.setCaretPosition(input.getText().length());
            }
            catch (Throwable error)
            {
                Dialog failed=active;if(failed!=null)failed.dispose();active=null;activeOwner=null;
                OPEN.set(false); FTBLib.LOGGER.warn("Could not open system text input", error);
                mc.addScheduledTask(() -> { if (mc.player != null) mc.player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("ftblib.text_input.unavailable")); });
            }
        });
        return true;
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event)
    {
        Dialog dialog=active;
        if(event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END && dialog!=null && Minecraft.getMinecraft().currentScreen!=activeOwner)
            EventQueue.invokeLater(() -> dialog.dispatchEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSING)));
    }

    public static boolean isButton(int mouseX, int mouseY, int x, int y, int w, int h)
    { return mouseX >= x + w - 13 && mouseX < x + w - 1 && mouseY >= y + 1 && mouseY < y + h - 1; }

    public static void drawButton(Theme theme, int x, int y, int w, int h)
    { theme.drawString("中", x + w - 12, y + (h - theme.getFontHeight()) / 2, com.feed_the_beast.ftblib.lib.icon.Color4I.LIGHT_BLUE, 0); }
}
