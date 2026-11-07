package com.feed_the_beast.ftbquests.gui.tree;

import com.feed_the_beast.ftblib.lib.gui.Button;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftbquests.quest.theme.property.ThemeProperties;
import com.feed_the_beast.ftbquests.client.VisualConfig;
import net.minecraft.client.renderer.GlStateManager;

/**
 * @author LatvianModder
 */
public abstract class ButtonTab extends Button
{
	public final GuiQuestTree treeGui;
	protected final com.feed_the_beast.ftbquests.client.Easing hoverAnimation = new com.feed_the_beast.ftbquests.client.Easing(0);

	protected double hoverProgress()
	{
		return hoverAnimation.next(isMouseOver() ? 1 : 0, VisualConfig.sidebarAnimationMilliseconds);
	}

	protected void drawHoverGlow(int x, int y, int w, int h, double hover)
	{
		int alpha = (int) (hover * VisualConfig.buttonGlowOpacity);
		if (alpha > 0) Color4I.WHITE.withAlpha(alpha).draw(x + 1, y + 1, w - 2, h - 2);
	}

	protected void drawAnimatedIcon(int x, int y, int w, int h, int size, double hover)
	{
		double scale = VisualConfig.navigationHoverAnimation ? 1 + .12 * hover : 1;
		GlStateManager.pushMatrix();
		GlStateManager.translate(x + w / 2D, y + h / 2D, 0);
		GlStateManager.scale(scale, scale, 1);
		icon.draw(-size / 2, -size / 2, size, size);
		GlStateManager.popMatrix();
	}

	public ButtonTab(Panel panel, String title, Icon icon)
	{
		super(panel, title, icon);
		treeGui = (GuiQuestTree) panel.getGui();
		setSize(20, 18);
	}

	@Override
	public void draw(Theme theme, int x, int y, int w, int h)
	{
		double hover = hoverProgress();
		if (hover > 0)
		{
			Color4I backgroundColor = ThemeProperties.WIDGET_BACKGROUND.get(treeGui.selectedChapter);
			backgroundColor.withAlpha((int)Math.round(backgroundColor.alphai() * hover)).draw(x + 1, y, w - 2, h);
		}
		drawHoverGlow(x, y, w, h, hover);
		drawAnimatedIcon(x, y, w, h, 16, hover);
	}
}
