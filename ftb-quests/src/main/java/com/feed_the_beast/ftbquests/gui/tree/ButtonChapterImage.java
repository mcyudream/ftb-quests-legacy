package com.feed_the_beast.ftbquests.gui.tree;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.gui.Button;
import com.feed_the_beast.ftblib.lib.gui.ContextMenuItem;
import com.feed_the_beast.ftblib.lib.gui.GuiHelper;
import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfig;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObject;
import com.feed_the_beast.ftbquests.quest.ChapterImage;
import com.feed_the_beast.ftbquests.quest.theme.property.ThemeProperties;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * @author LatvianModder
 */
public class ButtonChapterImage extends Button
{
	public GuiQuestTree treeGui;
	public ChapterImage chapterImage;

	public ButtonChapterImage(Panel panel, ChapterImage i)
	{
		super(panel, "", i.image);
		treeGui = (GuiQuestTree) panel.getGui();
		setSize(20, 20);
		chapterImage = i;
	}

    private String entitySpec="";
    private com.feed_the_beast.ftbquests.client.EntityModelIcon model;
    private com.feed_the_beast.ftbquests.client.EntityModelIcon entityIcon()
    {
        String spec=chapterImage.mediaSource+chapterImage.text;
        if(model==null || !entitySpec.equals(spec)){entitySpec=spec;model=new com.feed_the_beast.ftbquests.client.EntityModelIcon(chapterImage.mediaSource,chapterImage.text.isEmpty()?"{}":chapterImage.text);}
        return model;
    }

    private double[] endpoint(String reference,double x,double y)
    {
        for (var widget : parent.widgets)
        {
            boolean found = widget instanceof ButtonQuest && reference.equals("quest:"+((ButtonQuest)widget).quest.getCodeString())
                    || widget instanceof ButtonChapterImage && reference.equals("element:"+((ButtonChapterImage)widget).chapterImage.elementId);
            if (found) return new double[]{widget.getX()+widget.width/2D,widget.getY()+widget.height/2D};
        }
        return new double[]{x,y};
    }

	@Override
	public boolean checkMouseOver(int mouseX, int mouseY)
	{
		if (treeGui.questPanel.mouseOverQuest != null || treeGui.movingObjects || treeGui.viewQuestPanel.isMouseOver() || treeGui.chapterHoverPanel.isMouseOverAnyWidget())
		{
			return false;
		}

		if (chapterImage.click.isEmpty() && !chapterImage.kind.equals("video") && !treeGui.file.canEdit())
		{
			return false;
		}

		return super.checkMouseOver(mouseX, mouseY);
	}

	@Override
	public boolean mousePressed(MouseButton button)
	{
		if (isMouseOver())
		{
			if (!chapterImage.click.isEmpty() || chapterImage.kind.equals("video") || treeGui.file.canEdit() && !button.isLeft())
			{
				onClicked(button);
				return true;
			}
		}

		return false;
	}

	@Override
	public void onClicked(MouseButton button)
	{
		if (chapterImage.kind.equals("video") && (button.isLeft() || button.isRight() && !treeGui.file.canEdit()))
		{
			var video = com.feed_the_beast.ftbquests.client.VideoBackend.get(chapterImage.mediaSource);
			if (button.isRight()) video.replay(); else video.toggle();
			return;
		}
		if (treeGui.file.canEdit() && button.isRight())
		{
			List<ContextMenuItem> contextMenu = new ArrayList<>();

			contextMenu.add(new ContextMenuItem(I18n.format("selectServer.edit"), ThemeProperties.EDIT_ICON.get(), () -> {
				ConfigGroup group = ConfigGroup.newGroup(FTBQuests.MOD_ID);
				chapterImage.getConfig(group.getGroup("chapter").getGroup("image"));
				new GuiEditConfig(group, (group1, sender) -> MessageEditObject.send(chapterImage.chapter)).openGui();
			}));

			contextMenu.add(new ContextMenuItem(I18n.format("gui.move"), ThemeProperties.MOVE_UP_ICON.get(chapterImage.chapter), () -> {
				treeGui.movingObjects = true;
				treeGui.selectedObjects.clear();
				treeGui.toggleSelected(chapterImage);
			})
			{
				@Override
				public void addMouseOverText(List<String> list)
				{
					list.add(TextFormatting.DARK_GRAY + I18n.format("ftbquests.gui.move_tooltip"));
				}
			});

			contextMenu.add(new ContextMenuItem(I18n.format("selectServer.delete"), ThemeProperties.DELETE_ICON.get(), () -> {
				chapterImage.chapter.images.remove(chapterImage);
				MessageEditObject.send(chapterImage.chapter);
			}).setYesNo(I18n.format("delete_item", chapterImage.image.toString())));

			getGui().openContextMenu(contextMenu);
		}
		else if (button.isLeft())
		{
			if (!chapterImage.click.isEmpty())
			{
				GuiHelper.playClickSound();
				if (chapterImage.click.startsWith("zs:"))
					new com.feed_the_beast.ftbquests.net.MessageScriptTextClick(chapterImage.chapter.id, chapterImage.click.substring(3)).sendToServer();
				else handleClick(chapterImage.click);
			}
		}
		else if (treeGui.file.canEdit() && button.isMiddle())
		{
			if (!treeGui.selectedObjects.contains(chapterImage))
			{
				treeGui.toggleSelected(chapterImage);
			}

			treeGui.movingObjects = true;
		}
	}

	@Override
	public void addMouseOverText(List<String> list)
	{
		for (String s : chapterImage.hover)
		{
			if (s.startsWith("{") && s.endsWith("}"))
			{
				list.add(I18n.format(s.substring(1, s.length() - 1)));
			}
			else
			{
				list.add(s);
			}
		}
	}

	@Override
	public boolean shouldDraw()
	{
		return chapterImage.layer > 0;
	}

	@Override
	public void draw(Theme theme, int x, int y, int w, int h)
	{
		GlStateManager.pushMatrix();
		GlStateManager.translate(0,0,chapterImage.layer);
		if (chapterImage.kind.equals("text"))
		{
			String[] lines = com.feed_the_beast.ftbquests.client.FTBQuestsClient.addI18nAndColors(chapterImage.text).split("\\n", -1);
			var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
			int maximum = 1; for (String line : lines) maximum = Math.max(maximum,font.getStringWidth(line));
			GlStateManager.translate(x + w/2D, y + h/2D, 0);
			GlStateManager.rotate((float)chapterImage.rotation,0,0,1);
			GlStateManager.scale(w/(double)maximum,h/(double)(lines.length * font.FONT_HEIGHT),1);
			for (int i=0;i<lines.length;i++) font.drawString(lines[i],-maximum/2F,(i-lines.length/2F)*font.FONT_HEIGHT,(chapterImage.opacity<<24)|chapterImage.color,false);
		}
		else if (chapterImage.kind.equals("anchor"))
		{
			if (treeGui.file.canEdit()) com.feed_the_beast.ftblib.lib.icon.Color4I.rgb(0xFFFF00).withAlpha(chapterImage.opacity).draw(x+w/2-2,y+h/2-2,4,4);
		}
		else if (chapterImage.kind.equals("line"))
		{
            double[] start=endpoint(chapterImage.lineStart,x,y),end=endpoint(chapterImage.lineEnd,x+w,y+h);
            float previous=org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_LINE_WIDTH);
            GlStateManager.translate(x+w/2D,y+h/2D,0);GlStateManager.rotate((float)chapterImage.rotation,0,0,1);GlStateManager.translate(-x-w/2D,-y-h/2D,0);
            org.lwjgl.opengl.GL11.glLineWidth(chapterImage.lineWidth);
			GlStateManager.disableTexture2D();
			GlStateManager.color(((chapterImage.color>>16)&255)/255F,((chapterImage.color>>8)&255)/255F,(chapterImage.color&255)/255F,chapterImage.opacity/255F);
			org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINES);
			org.lwjgl.opengl.GL11.glVertex2d(start[0],start[1]); org.lwjgl.opengl.GL11.glVertex2d(end[0],end[1]); org.lwjgl.opengl.GL11.glEnd();
			GlStateManager.enableTexture2D(); GlStateManager.color(1,1,1,1);org.lwjgl.opengl.GL11.glLineWidth(previous);
		}
		else if (chapterImage.kind.equals("entity"))
        {
            entityIcon().draw(x,y,w,h);
        }
		else if (chapterImage.kind.equals("video"))
		{
			GlStateManager.translate(x+w/2D,y+h/2D,0); GlStateManager.rotate((float)chapterImage.rotation,0,0,1);
			com.feed_the_beast.ftbquests.client.VideoBackend.get(chapterImage.mediaSource).draw(-w/2,-h/2,w,h,chapterImage.image.toString());
		}
		else if (!chapterImage.mediaSource.isEmpty())
		{
			GlStateManager.translate(x+w/2D,y+h/2D,0); GlStateManager.rotate((float)chapterImage.rotation,0,0,1);
			com.feed_the_beast.ftbquests.client.CanvasMedia.draw(chapterImage.mediaSource,-w/2,-h/2,w,h,chapterImage.opacity,chapterImage.color);
		}
		else
		{
		GlStateManager.translate((int) (x + w / 2D), (int) (y + h / 2D), 0);
		GlStateManager.rotate((float) chapterImage.rotation, 0, 0, 1);
		GlStateManager.scale(w / 2D, h / 2D, 1);
		chapterImage.image.withColor(com.feed_the_beast.ftblib.lib.icon.Color4I.rgb(chapterImage.color).withAlpha(chapterImage.opacity)).draw(-1, -1, 2, 2);
		}
		GlStateManager.popMatrix();
	}
}
