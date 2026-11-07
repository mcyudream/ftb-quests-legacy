package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.net.edit.MessagePasteImage;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public final class CanvasClipboard
{
    public static void copyObjects(GuiQuestTree gui)
    {
        if (gui.selectedObjects.isEmpty()) return;
        net.minecraft.client.gui.GuiScreen.setClipboardString(com.feed_the_beast.ftbquests.util.QuestCopies.capture(gui.selectedObjects).toString());
    }
    public static boolean pasteObjects(GuiQuestTree gui)
    {
        try
        {
            String text = net.minecraft.client.gui.GuiScreen.getClipboardString();
            if (text.length() > 1_000_000 || !text.contains("ftbquests_clipboard")) return false;
            var data = net.minecraft.nbt.JsonToNBT.getTagFromJson(text);
            if (data.getInteger("ftbquests_clipboard") != 1) return false;
            com.feed_the_beast.ftbquests.net.edit.MessagePasteObjects.send(gui.selectedChapter.id,gui.questPanel.questX,gui.questPanel.questY,data);
            return true;
        }
        catch (Exception ex) { FTBQuests.LOGGER.warn("Could not paste quest objects", ex); return false; }
    }
    public static byte[] imageBytes() throws Exception
    {
        var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null;
        Image image = (Image) clipboard.getData(DataFlavor.imageFlavor);
        int width=image.getWidth(null), height=image.getHeight(null);
        if(width<1 || height<1 || (long)width*height>16_777_216L) throw new IllegalArgumentException("Image dimensions exceed 16 megapixels");
        BufferedImage raster=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=raster.createGraphics();try{graphics.drawImage(image,0,0,null);}finally{graphics.dispose();}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(raster,"png",bytes);
        if(bytes.size()>QuestAssets.MAX_BYTES)throw new IllegalArgumentException("Image exceeds 8 MiB");
        return bytes.toByteArray();
    }
    public static boolean pasteImage(GuiQuestTree gui)
    {
        try
        {
            byte[] bytes=imageBytes();if(bytes==null)return false;
            MessagePasteImage.send(gui.selectedChapter.id,gui.questPanel.questX,gui.questPanel.questY,bytes);return true;
        }
        catch(Exception ex){FTBQuests.LOGGER.warn("Could not paste clipboard image",ex);return false;}
    }
}
