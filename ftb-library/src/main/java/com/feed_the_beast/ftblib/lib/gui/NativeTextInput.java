package com.feed_the_beast.ftblib.lib.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.function.Consumer;

/** Binary compatibility for the retired external editor. Text input stays in the game widget. */
@Deprecated
@SideOnly(Side.CLIENT)
public final class NativeTextInput {
    private NativeTextInput() {}
    public static boolean isOpen() { return false; }
    public static boolean open(String value, boolean multiline, Consumer<String> accepted) { return false; }
    public static boolean isButton(int mouseX, int mouseY, int x, int y, int w, int h) { return false; }
    public static void drawButton(Theme theme, int x, int y, int w, int h) {}
}
