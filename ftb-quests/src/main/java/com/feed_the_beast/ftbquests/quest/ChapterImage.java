package com.feed_the_beast.ftbquests.quest;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.config.ConfigString;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObject;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * @author LatvianModder
 */
public final class ChapterImage implements Movable
{
	public Chapter chapter;
	public double x, y;
	public double width, height;
	public double rotation;
	public Icon image;
	public List<String> hover;
	public String click;
	public boolean dev;
	public String elementId = java.util.UUID.randomUUID().toString();
    public String lineStart = "", lineEnd = "";
    public int lineWidth = 1;
    public String kind = "image";
	public String mediaSource = "";
	public String text = "";
	public int color = 0xFFFFFF;
	public int opacity = 255;
	public int layer = 0;
	public boolean locked = false;
	private NBTTagCompound extensionData = new NBTTagCompound();

	public ChapterImage(Chapter c)
	{
		chapter = c;
		x = y = 0D;
		width = 1D;
		height = 1D;
		rotation = 0D;
		image = Icon.getIcon("minecraft:textures/gui/presets/isles.png");
		hover = new ArrayList<>();
		click = "";
		dev = false;
	}

	public void writeData(NBTTagCompound nbt)
	{
		com.feed_the_beast.ftbquests.util.TrackedNBT.mergeMissing(nbt, extensionData);
		nbt.setDouble("x", x);
		nbt.setDouble("y", y);
		nbt.setDouble("width", width);
		nbt.setDouble("height", height);
		nbt.setDouble("rotation", rotation);
		nbt.setString("image", image.toString());

		NBTTagList hoverTag = new NBTTagList();

		for (String s : hover)
		{
			hoverTag.appendTag(new NBTTagString(s));
		}

		nbt.setTag("hover", hoverTag);
		nbt.setString("click", click);
		nbt.setBoolean("dev", dev);
		nbt.setString("element_id", elementId);
        if (!lineStart.isEmpty()) nbt.setString("line_start", lineStart);
        if (!lineEnd.isEmpty()) nbt.setString("line_end", lineEnd);
        if (lineWidth != 1) nbt.setInteger("line_width", lineWidth);
        if (!kind.equals("image")) nbt.setString("kind", kind);
		if (!mediaSource.isEmpty()) nbt.setString("media_source", mediaSource);
		if (!text.isEmpty()) nbt.setString("text", text);
		if (color != 0xFFFFFF) nbt.setInteger("color", color);
		if (opacity != 255) nbt.setInteger("opacity", opacity);
		if (layer != 0) nbt.setInteger("layer", layer);
		if (locked) nbt.setBoolean("locked", true);
	}

	public void readData(NBTTagCompound input)
	{
		com.feed_the_beast.ftbquests.util.TrackedNBT nbt = new com.feed_the_beast.ftbquests.util.TrackedNBT(input);
		x = nbt.getDouble("x");
		y = nbt.getDouble("y");
		width = nbt.getDouble("width");
		height = nbt.getDouble("height");
		rotation = nbt.getDouble("rotation");
		image = Icon.getIcon(nbt.getString("image"));

		hover.clear();
		NBTTagList hoverTag = nbt.getTagList("hover", Constants.NBT.TAG_STRING);

		for (int i = 0; i < hoverTag.tagCount(); i++)
		{
			hover.add(hoverTag.getStringTagAt(i));
		}

		click = nbt.getString("click");
		dev = nbt.getBoolean("dev");
		elementId = nbt.hasKey("element_id") ? nbt.getString("element_id") : elementId;
        lineStart = nbt.getString("line_start"); lineEnd = nbt.getString("line_end");
        lineWidth = nbt.hasKey("line_width") ? Math.max(1,Math.min(8,nbt.getInteger("line_width"))) : 1;
        kind = nbt.hasKey("kind") ? nbt.getString("kind") : "image";
		mediaSource = nbt.getString("media_source"); text = nbt.getString("text");
		color = nbt.hasKey("color") ? nbt.getInteger("color") : 0xFFFFFF;
		opacity = nbt.hasKey("opacity") ? Math.max(0,Math.min(255,nbt.getInteger("opacity"))) : 255;
		layer = nbt.getInteger("layer"); locked = nbt.getBoolean("locked");
		extensionData = nbt.unknown();
	}

	public void writeNetData(DataOut data)
	{
		data.writeNBT(extensionData);
		data.writeDouble(x);
		data.writeDouble(y);
		data.writeDouble(width);
		data.writeDouble(height);
		data.writeDouble(rotation);
		data.writeIcon(image);
		data.writeCollection(hover, DataOut.STRING);
		data.writeString(click);
		data.writeBoolean(dev);
		data.writeString(elementId); data.writeString(lineStart); data.writeString(lineEnd); data.writeVarInt(lineWidth);
        data.writeString(kind); data.writeString(mediaSource); data.writeString(text);
		data.writeInt(color); data.writeByte(opacity); data.writeInt(layer); data.writeBoolean(locked);
	}

	public void readNetData(DataIn data)
	{
		NBTTagCompound extras = data.readNBT();
		extensionData = extras == null ? new NBTTagCompound() : extras;
		x = data.readDouble();
		y = data.readDouble();
		width = data.readDouble();
		height = data.readDouble();
		rotation = data.readDouble();
		image = data.readIcon();
		data.readCollection(hover, DataIn.STRING);
		click = data.readString();
		dev = data.readBoolean();
		elementId = data.readString(); lineStart = data.readString(); lineEnd = data.readString(); lineWidth = data.readVarInt();
        kind = data.readString(); mediaSource = data.readString(); text = data.readString();
		color = data.readInt(); opacity = data.readByte() & 255; layer = data.readInt(); locked = data.readBoolean();
	}

	@SideOnly(Side.CLIENT)
	public void getConfig(ConfigGroup config)
	{
		config.addDouble("x", () -> x, v -> x = v, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		config.addDouble("y", () -> y, v -> y = v, 0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
		config.addDouble("width", () -> width, v -> width = v, 1, 0, Double.POSITIVE_INFINITY);
		config.addDouble("height", () -> height, v -> height = v, 1, 0, Double.POSITIVE_INFINITY);
		config.addDouble("rotation", () -> rotation, v -> rotation = v, 0, -180, 180);
		config.addString("image", () -> image.toString(), v -> image = Icon.getIcon(v), "minecraft:textures/gui/presets/isles.png");
		config.addList("hover", hover, new ConfigString(""), ConfigString::new, ConfigString::getString);
		config.addString("click", () -> click, v -> click = v, "");
		config.addBool("dev", () -> dev, v -> dev = v, false);
		config.addString("line_start", () -> lineStart, v -> lineStart = v, "");
        config.addString("line_end", () -> lineEnd, v -> lineEnd = v, "");
        config.addInt("line_width", () -> lineWidth, v -> lineWidth = v, 1, 1, 8);
        config.addString("kind", () -> kind, v -> kind = v, "image");
        config.add("media_source", new com.feed_the_beast.ftbquests.client.ConfigMediaSource(() -> mediaSource, v -> mediaSource = v, () -> kind), new ConfigString(""));
		config.addString("text", () -> text, v -> text = v, "");
		config.addInt("color", () -> color, v -> color = v, 0xFFFFFF, 0, 0xFFFFFF);
		config.addInt("opacity", () -> opacity, v -> opacity = v, 255, 0, 255);
		config.addInt("layer", () -> layer, v -> layer = v, 0, -100, 100);
		config.addBool("locked", () -> locked, v -> locked = v, false);
	}

	@Override
	public Chapter getChapter()
	{
		return chapter;
	}

	@Override
	public double getX()
	{
		return x;
	}

	@Override
	public double getY()
	{
		return y;
	}

	@Override
	public double getWidth()
	{
		return width;
	}

	@Override
	public double getHeight()
	{
		return height;
	}

	@Override
	public String getShape()
	{
		return "square";
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void move(Chapter to, double _x, double _y)
	{
		if (locked) return;
		x = _x;
		y = _y;

		if (to != chapter)
		{
			chapter.images.remove(this);
			MessageEditObject.send(chapter);

			chapter = to;
			chapter.images.add(this);
		}

		MessageEditObject.send(chapter);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void drawMoved()
	{
		GlStateManager.pushMatrix();
		GlStateManager.translate(0.5D, 0.5D, 0);
		GlStateManager.rotate((float) rotation, 0, 0, 1);
		GlStateManager.scale(0.5D, 0.5D, 1);
		image.withColor(Color4I.WHITE.withAlpha(50)).draw(-1, -1, 2, 2);
		GlStateManager.popMatrix();

		QuestShape.get(getShape()).outline.withColor(Color4I.WHITE.withAlpha(30)).draw(0, 0, 1, 1);
	}
}
