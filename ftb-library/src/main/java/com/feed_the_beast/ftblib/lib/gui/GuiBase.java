package com.feed_the_beast.ftblib.lib.gui;

import com.feed_the_beast.ftblib.FTBLibConfig;
import com.feed_the_beast.ftblib.events.client.CustomClickEvent;
import com.feed_the_beast.ftblib.lib.client.ClientUtils;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiLoading;
import com.feed_the_beast.ftblib.lib.gui.misc.YesNoCallback;
import com.feed_the_beast.ftblib.lib.util.NetUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;

/**
 * @author LatvianModder
 */
public abstract class GuiBase extends Panel implements IOpenableGui
{
	public static class PositionedTextData
	{
		public final int posX, posY;
		public final int width, height;
		public final ClickEvent clickEvent;
		public final HoverEvent hoverEvent;
		public final String insertion;

		public PositionedTextData(int x, int y, int w, int h, Style s)
		{
			posX = x;
			posY = y;
			width = w;
			height = h;
			clickEvent = s.getClickEvent();
			hoverEvent = s.getHoverEvent();
			insertion = s.getInsertion();
		}
	}

	private int mouseX, mouseY;
	private float partialTicks;
	private boolean refreshWidgets;
	private ScaledResolution screen;
	public boolean fixUnicode;
	private GuiScreen prevScreen;
	private FocusableTextInput focusedTextInput;
	private static GuiTextField platformTextInput;
	private static GuiScreen platformTextInputScreen;
	public Panel contextMenu = null;

	public final FocusableTextInput getFocusedTextInput()
	{
		return focusedTextInput;
	}

	/** Called by text widgets after updating their own focus flag. */
	public final void onTextInputFocusChanged(FocusableTextInput input, boolean focused)
	{
		if (focused)
		{
			FocusableTextInput previous = focusedTextInput;
			focusedTextInput = input;
			if (previous != null && previous != input) previous.setFocused(false);
		}
		else if (focusedTextInput == input)
		{
			focusedTextInput = null;
		}
		updateKeyboardRepeatEvents();
	}

	private boolean hasTextInputFocus()
	{
		return contextMenu instanceof GuiBase ? ((GuiBase) contextMenu).hasTextInputFocus() : focusedTextInput != null;
	}

	private static void updateKeyboardRepeatEvents()
	{
		Minecraft mc = Minecraft.getMinecraft();
		GuiScreen active = mc.currentScreen;
		if (active instanceof IGuiWrapper || active == null)
		{
			boolean focused = active instanceof IGuiWrapper && ((IGuiWrapper) active).getGui().hasTextInputFocus();
			Keyboard.enableRepeatEvents(focused);
			// Cleanroom patches the vanilla field to activate the platform IME and mark the screen
			// as text-focused. Use that path on both baseline IMM and newer SDL backends.
			// This delegate is never drawn, added as a widget, or used to hold the user's text.
			if (platformTextInput == null) platformTextInput = new GuiTextField(-1, mc.fontRenderer, 0, 0, 1, 1);
			if (platformTextInputScreen != active || platformTextInput.isFocused() != focused)
			{
				platformTextInputScreen = active;
				platformTextInput.setFocused(focused);
			}
		}
		else
		{
			// The destination screen owns its own input method; do not override it while closing ours.
			platformTextInputScreen = null;
		}
	}

	private void clearTextInputFocus()
	{
		if (focusedTextInput != null) focusedTextInput.setFocused(false);
	}

	private static boolean containsWidget(Panel panel, Widget target)
	{
		for (Widget widget : panel.widgets)
		{
			if (widget == target || widget instanceof Panel && containsWidget((Panel) widget, target)) return true;
		}
		return false;
	}

	public GuiBase()
	{
		//noinspection ConstantConditions
		super(null);
		setSize(176, 166);
		setOnlyRenderWidgetsInside(false);
		setOnlyInteractWithWidgetsInside(false);
		prevScreen = Minecraft.getMinecraft().currentScreen;
	}

	@Override
	public final GuiBase getGui()
	{
		return this;
	}

	@Override
	public void alignWidgets()
	{
	}

	public final void initGui()
	{
		if (parent instanceof GuiBase)
		{
			screen = parent.getScreen();
		}
		else
		{
			screen = new ScaledResolution(Minecraft.getMinecraft());
		}

		if (onInit())
		{
			super.refreshWidgets();
			fixUnicode = getScreen().getScaleFactor() % 2 == 1;
			alignWidgets();
			onPostInit();
		}
		updateKeyboardRepeatEvents();
	}

	public Theme getTheme()
	{
		return Theme.DEFAULT;
	}

	@Override
	public int getX()
	{
		return (getScreen().getScaledWidth() - width) / 2;
	}

	@Override
	public int getY()
	{
		return (getScreen().getScaledHeight() - height) / 2;
	}

	@Override
	public void setScrollX(double scroll)
	{
	}

	@Override
	public void setScrollY(double scroll)
	{
	}

	@Override
	public double getScrollX()
	{
		return 0;
	}

	@Override
	public double getScrollY()
	{
		return 0;
	}

	public boolean onInit()
	{
		return true;
	}

	protected boolean setFullscreen()
	{
		setWidth(screen.getScaledWidth());
		setHeight(screen.getScaledHeight());
		return true;
	}

	public void onPostInit()
	{
	}

	@Nullable
	public GuiScreen getPrevScreen()
	{
		if (prevScreen instanceof GuiWrapper && ((GuiWrapper) prevScreen).getGui() instanceof GuiLoading)
		{
			return ((GuiWrapper) prevScreen).getGui().getPrevScreen();
		}
		else if (prevScreen instanceof GuiChat)
		{
			return null;
		}

		return prevScreen;
	}

	@Override
	public final void closeGui(boolean openPrevScreen)
	{
		int mx = Mouse.getX();
		int my = Mouse.getY();

		Minecraft mc = Minecraft.getMinecraft();

		if (mc.player != null)
		{
			mc.player.closeScreen();

			if (mc.currentScreen == null)
			{
				mc.setIngameFocus();
			}
		}

		if (openPrevScreen)
		{
			mc.displayGuiScreen(getPrevScreen());
			Mouse.setCursorPosition(mx, my);
		}

		onClosed();
	}

	public boolean onClosedByKey(int key)
	{
		return key == Keyboard.KEY_ESCAPE || Minecraft.getMinecraft().gameSettings.keyBindInventory.isActiveAndMatches(key);
	}

	public void onBack()
	{
		closeGui(true);
	}

	public boolean doesGuiPauseGame()
	{
		return false;
	}

	@Override
	public final void refreshWidgets()
	{
		refreshWidgets = true;
	}

	public final void updateGui(int mx, int my, float pt)
	{
		mouseX = mx;
		mouseY = my;
		partialTicks = pt;

		if (refreshWidgets)
		{
			super.refreshWidgets();
			refreshWidgets = false;
			if (focusedTextInput instanceof Widget && !containsWidget(this, (Widget) focusedTextInput)) clearTextInputFocus();
		}

		posX = getX();
		posY = getY();

		if (contextMenu != null)
		{
			if (contextMenu instanceof GuiBase)
			{
				((GuiBase) contextMenu).updateGui(mx, my, pt);
			}
			else
			{
				contextMenu.updateMouseOver(mouseX, mouseY);
			}
		}

		updateMouseOver(mouseX, mouseY);
	}

	@Override
	public void updateMouseOver(int mouseX, int mouseY)
	{
		// Mouse events can arrive before drawScreen: caret placement must use the event's coordinates.
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		isMouseOver = checkMouseOver(mouseX, mouseY);
		setOffset(true);

		if (contextMenu != null)
		{
			contextMenu.updateMouseOver(mouseX, mouseY);
		}
		else
		{
			for (Widget widget : widgets)
			{
				widget.updateMouseOver(mouseX, mouseY);
			}
		}

		setOffset(false);
	}

	@Override
	public final void draw(Theme theme, int x, int y, int w, int h)
	{
		super.draw(theme, x, y, w, h);
	}

	@Override
	public void openContextMenu(@Nullable Panel panel)
	{
		int px = 0, py = 0;

		if (contextMenu != null)
		{
			px = contextMenu.posX;
			py = contextMenu.posY;
			contextMenu.onClosed();
			widgets.remove(contextMenu);
		}

		if (panel == null)
		{
			contextMenu = null;
			return;
		}

		clearTextInputFocus();

		int x = getX();
		int y = getY();

		if (contextMenu == null)
		{
			px = getMouseX() - x;
			py = getMouseY() - y;
		}

		contextMenu = panel;
		contextMenu.parent = this;
		widgets.add(contextMenu);
		contextMenu.refreshWidgets();
		px = Math.min(px, screen.getScaledWidth() - contextMenu.width - x) - 3;
		py = Math.min(py, screen.getScaledHeight() - contextMenu.height - y) - 3;
		contextMenu.setPos(Math.max(3 - x, px), Math.max(3 - y, py));

		if (contextMenu instanceof GuiBase)
		{
			((GuiBase) contextMenu).initGui();
		}
	}

	public ContextMenu openContextMenu(List<ContextMenuItem> menu)
	{
		ContextMenu contextMenu = new ContextMenu(this, menu);
		openContextMenu(contextMenu);
		return contextMenu;
	}

	@Override
	public void closeContextMenu()
	{
		openContextMenu((Panel) null);
		onInit();
	}

	@Override
	public void onClosed()
	{
		clearTextInputFocus();
		super.onClosed();
		closeContextMenu();
	}

	@Override
	public void drawBackground(Theme theme, int x, int y, int w, int h)
	{
		theme.drawGui(x, y, w, h, WidgetType.NORMAL);
	}

	public boolean drawDefaultBackground()
	{
		return true;
	}

	public void drawForeground(Theme theme, int x, int y, int w, int h)
	{
	}

	@Override
	public boolean mousePressed(MouseButton button)
	{
		// A button or another panel may consume the event before the old text box sees it.
		if (focusedTextInput instanceof Widget && !((Widget) focusedTextInput).isMouseOver()) clearTextInputFocus();
		if (button == MouseButton.BACK)
		{
			closeGui(true);
		}

		return super.mousePressed(button);
	}

	@Override
	public boolean keyPressed(int key, char keyChar)
	{
		if (super.keyPressed(key, keyChar))
		{
			return true;
		}
		else if (FTBLibConfig.debugging.gui_widget_bounds && key == Keyboard.KEY_B)
		{
			Theme.renderDebugBoxes = !Theme.renderDebugBoxes;
			return true;
		}

		return false;
	}

	@Override
	public boolean shouldAddMouseOverText()
	{
		return contextMenu == null;
	}

	public GuiScreen getWrapper()
	{
		return new GuiWrapper(this);
	}

	@Override
	public final void openGui()
	{
		openContextMenu((Panel) null);
		Minecraft.getMinecraft().displayGuiScreen(getWrapper());
	}

	@Override
	public final ScaledResolution getScreen()
	{
		if (screen == null)
		{
			return parent.getScreen();
		}

		return screen;
	}

	@Override
	public final int getMouseX()
	{
		return mouseX;
	}

	@Override
	public final int getMouseY()
	{
		return mouseY;
	}

	@Override
	public final float getPartialTicks()
	{
		return partialTicks;
	}

	public boolean isMouseOver(int x, int y, int w, int h)
	{
		return getMouseX() >= x && getMouseY() >= y && getMouseX() < x + w && getMouseY() < y + h;
	}

	public boolean isMouseOver(Widget widget)
	{
		if (widget == this)
		{
			return isMouseOver(getX(), getY(), width, height);
		}
		else if (isMouseOver(widget.getX(), widget.getY(), widget.width, widget.height))
		{
			boolean offset = widget.parent.isOffset();
			widget.parent.setOffset(false);
			boolean b = isMouseOver(widget.parent);
			widget.parent.setOffset(offset);
			return b;
		}

		return false;
	}

	@Override
	public boolean handleClick(String scheme, String path)
	{
		switch (scheme)
		{
			case "http":
			case "https":
			{
				try
				{
					final URI uri = new URI(scheme + ':' + path);
					if (Minecraft.getMinecraft().gameSettings.chatLinksPrompt)
					{
						final GuiScreen currentScreen = Minecraft.getMinecraft().currentScreen;

						Minecraft.getMinecraft().displayGuiScreen(new GuiConfirmOpenLink((result, id) ->
						{
							if (result)
							{
								try
								{
									NetUtils.openURI(uri);
								}
								catch (Exception ex)
								{
									ex.printStackTrace();
								}
							}
							Minecraft.getMinecraft().displayGuiScreen(currentScreen);
						}, scheme + ':' + path, 0, false));
					}
					else
					{
						NetUtils.openURI(uri);
					}

					return true;
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}

				return false;
			}
			case "file":
			{
				try
				{
					NetUtils.openURI(new URI("file:" + path));
					return true;
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}

				return false;
			}
			case "command":
			{
				ClientUtils.execClientCommand(path, false);
				return true;
			}
			case "curseforgepages":
			{
				String[] s = path.split(":", 2);

				if (s.length == 2)
				{
					return handleClick("https://www.curseforge.com/minecraft/mc-mods/" + s[0] + "/pages/" + s[1]);
				}

				return false;
			}
			case "custom":
				return new CustomClickEvent(new ResourceLocation(path)).post();
			default:
				return new CustomClickEvent(new ResourceLocation(scheme, path)).post();
		}
	}

	public void openYesNoFull(String title, String desc, YesNoCallback callback)
	{
		Minecraft.getMinecraft().displayGuiScreen(new GuiYesNo((result, id) ->
		{
			openGui();
			callback.onButtonClicked(result);
			refreshWidgets();
		}, title, desc, 0));
	}

	public final void openYesNo(String title, String desc, Runnable callback)
	{
		openYesNoFull(title, desc, result -> {
			if (result)
			{
				callback.run();
			}
		});
	}
}
