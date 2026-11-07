package com.feed_the_beast.ftbquests.gui.tree;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.WidgetLayout;

/**
 * @author LatvianModder
 */
public class PanelOtherButtonsBottom extends PanelOtherButtons
{
	public PanelOtherButtonsBottom(Panel panel)
	{
		super(panel);
	}

	@Override
	public void addWidgets()
	{
		add(new ButtonAutopin(this));
		add(new ButtonSupport(this));
		add(new ButtonTab(this, net.minecraft.client.resources.I18n.format("ftbquests.visual.settings"), com.feed_the_beast.ftblib.lib.gui.GuiIcons.SETTINGS)
		{
			@Override public void onClicked(com.feed_the_beast.ftblib.lib.util.misc.MouseButton button)
			{ com.feed_the_beast.ftbquests.client.VisualSettings.open(treeGui); }
		});

		if (treeGui.file.canEdit())
		{
			add(new ButtonEditSettings(this));
		}
	}

	@Override
	public void alignWidgets()
	{
		setHeight(align(WidgetLayout.VERTICAL));
		setPos(treeGui.width - width, treeGui.height - height - 1);
	}
}
