package com.feed_the_beast.ftbquests.gui.tree;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.Widget;
import com.feed_the_beast.ftblib.lib.gui.WidgetLayout;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.client.Easing;
import com.feed_the_beast.ftbquests.client.PanelTransition;
import com.feed_the_beast.ftbquests.client.VisualConfig;
import net.minecraft.client.renderer.GlStateManager;

/**
 * @author LatvianModder
 */
public class PanelChapterHover extends Panel
{
	public final GuiQuestTree treeGui;
	public ButtonChapter chapter = null;
	public int type = -1;
	private final Easing visibility = new Easing(0);
	private final PanelTransition transition;
	private boolean closing;
	private double progress = 1;

	public PanelChapterHover(Panel panel)
	{
		super(panel);
		treeGui = (GuiQuestTree) panel.getGui();
		transition = treeGui.panelTransition;
		setPosAndSize(-1, -1, 0, 0);
		setOnlyRenderWidgetsInside(true);
		setOnlyInteractWithWidgetsInside(true);
	}

	@Override public boolean isEnabled() { return chapter != null && !closing; }
	private double slide() { return PanelTransition.supported() ? -6 * (1 - progress) : 0; }
	@Override public int getMouseX() { return (int)Math.floor(super.getMouseX() - slide()); }
	@Override public void updateMouseOver(int mouseX, int mouseY)
	{ super.updateMouseOver(isEnabled() ? (int)Math.floor(mouseX - slide()) : Integer.MIN_VALUE / 2, isEnabled() ? mouseY : Integer.MIN_VALUE / 2); }

	@Override
	public void addWidgets()
	{
		if (chapter != null)
		{
			type = 0;

			for (Chapter c : treeGui.file.chapters)
			{
				if (c.group == chapter.chapter && (treeGui.file.canEdit() || c.isVisible(treeGui.file.self)))
				{
					if (widgets.isEmpty())
					{
						type = 1;
						add(new ButtonExpandedChapter(this, chapter.chapter));
					}

					ButtonExpandedChapter b = new ButtonExpandedChapter(this, c);
					b.setX(20);
					add(b);
				}
			}

			if (type == 0)
			{
				add(new ButtonExpandedChapter(this, chapter.chapter));
			}
			if (!chapter.chapter.subtitle.isEmpty())
			{
				var description = new com.feed_the_beast.ftblib.lib.gui.BlankPanel(this, "ChapterDescription")
				{
					@Override public int getContentHeight() { return com.feed_the_beast.ftbquests.client.DescriptionComponents.contentHeight(this); }
					@Override public void tick() {
						super.tick();
						if (com.feed_the_beast.ftbquests.client.DescriptionComponents.resizeMedia(this)) {
							setHeight(Math.min(Math.max(20, align(WidgetLayout.VERTICAL)), Math.max(20, treeGui.height / 3)));
							setScrollY(Math.max(0,Math.min(getScrollY(),getContentHeight()-height)));
							parent.setHeight(Math.min(parent.align(WidgetLayout.VERTICAL), Math.max(20,treeGui.height-2)));
						}
					}
					@Override public void drawBackground(Theme theme, int x, int y, int w, int h)
					{ theme.drawGui(x, y, w, h, com.feed_the_beast.ftblib.lib.gui.WidgetType.NORMAL); }
				};
				description.setWidth(Math.max(40, Math.min(240, treeGui.width - 24)));
				description.setOnlyRenderWidgetsInside(true);
				description.setOnlyInteractWithWidgetsInside(true);
				com.feed_the_beast.ftbquests.client.DescriptionComponents.add(description, chapter.chapter.subtitle.toArray(new String[0]));
				description.setHeight(Math.min(Math.max(20, description.align(WidgetLayout.VERTICAL)), Math.max(20, treeGui.height / 3)));
				add(description);
			}

			setHeight(Math.min(align(WidgetLayout.VERTICAL), Math.max(20, treeGui.height - 2)));
			setWidth(Math.max(20, getContentWidth()));

			for (Widget widget : widgets)
			{
				widget.setWidth(width - widget.posX);
			}

			setX(chapter.getX());
		}
		else
		{
			type = -1;
			setPosAndSize(-1, -1, 0, 0);
		}
	}

	@Override
	public int getY()
	{
		if (chapter != null)
		{
			treeGui.chapterPanel.setOffset(true);
			int y = Math.max(1, Math.min(chapter.getY() - 1, treeGui.height - height - 1));
			treeGui.chapterPanel.setOffset(false);
			return y;
		}

		return -1;
	}

	@Override
	public void alignWidgets()
	{
	}

	@Override
	public void drawBackground(Theme theme, int x, int y, int w, int h)
	{
	}

	@Override
	public void draw(Theme theme, int x, int y, int w, int h)
	{
		if (type != -1)
		{
			boolean animate = VisualConfig.chapterPanelAnimation && !treeGui.file.canEdit();
			if (!animate) visibility.snap(closing ? 0 : 1);
			progress = Math.max(0, Math.min(1, visibility.next(closing ? 0 : 1, VisualConfig.sidebarAnimationMilliseconds)));
			if (closing && progress < .001)
			{
				chapter = null;
				closing = false;
				refreshWidgets();
				return;
			}
			if (progress >= .9995) progress = 1;
			GlStateManager.pushMatrix();
			try
			{
				GlStateManager.translate(0F, 0F, 500F);
				transition.draw(() -> super.draw(theme, x, y, w, h), progress, 1, x, y, slide(), 0);
			}
			finally { GlStateManager.popMatrix(); }
		}
	}

	@Override
	public void tick()
	{
		if (closing || !isMouseOverAnyWidget())
		{
			ButtonChapter c = null;

			for (Widget widget : treeGui.chapterPanel.widgets)
			{
				if (widget instanceof ButtonChapter && widget.isMouseOver())
				{
					c = (ButtonChapter) widget;
				}
			}

			if (c != null && (chapter != c || closing))
			{
				if (chapter == null) visibility.snap(0);
				chapter = c;
				closing = false;
				visibility.next(1, VisualConfig.sidebarAnimationMilliseconds);
				refreshWidgets();
			}
			else if (c == null && chapter != null && !closing)
			{
				closing = true;
				visibility.next(0, VisualConfig.sidebarAnimationMilliseconds);
			}
		}

		super.tick();
	}
}
