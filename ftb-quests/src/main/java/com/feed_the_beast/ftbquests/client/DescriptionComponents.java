package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.*;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.text.*;
import java.util.*;

/** Each JSON component occupies one description line. Plain legacy text is rendered as before. */
public final class DescriptionComponents
{
    private static String string(JsonObject json,String key,String fallback){return json.has(key)?json.get(key).getAsString():fallback;}
    private static int integer(JsonObject json,String key,int fallback){return json.has(key)&&!json.get(key).isJsonNull()?json.get(key).getAsInt():fallback;}
    public static void add(Panel panel,String[] lines)
    {
        DescriptionData.watch(lines);
        addLines(panel,lines);
    }
    public static void addPreview(Panel panel,String[] lines)
    {
        addLines(panel,lines);
    }
    /** Reflow only when available width or asynchronously decoded dimensions changed. */
    public static boolean resizeMedia(Panel panel)
    {
        boolean changed = false;
        for (Widget widget : panel.widgets) if (widget instanceof DescriptionMediaWidget media) changed |= media.resize();
        return changed;
    }
    public static int contentHeight(Panel panel)
    {
        if (panel.widgets.isEmpty()) return 0;
        int min = Integer.MAX_VALUE, max = 0;
        for (Widget widget : panel.widgets) { min = Math.min(min,widget.posY); max = Math.max(max,widget.posY+widget.height); }
        return max-min+panel.contentHeightExtra;
    }
    private static void addLines(Panel panel,String[] lines)
    {
        List<String> plain=new ArrayList<>();
        for(String line:lines)
        {
            JsonObject json=null;
            if(line.trim().startsWith("{"))try{json=new JsonParser().parse(line).getAsJsonObject();}catch(RuntimeException ignored){}
            if(json==null)
            {
                var heading=java.util.regex.Pattern.compile("^(#{1,6})[ \\t]+(.+)$").matcher(line);
                if(heading.matches())
                {
                    flush(panel,plain);
                    panel.add(new TextField(panel).setMaxWidth(panel.width).setScale(1.7F-heading.group(1).length()*.1F).setText(TextFormatting.BOLD+substitute(heading.group(2))));
                }
                else plain.add(line);
                continue;
            }
            flush(panel,plain);
            addLine(panel,line,0);
        }
        flush(panel,plain);
    }
    static void addLine(Panel panel,String line,int depth)
    {
        try
        {
            JsonObject json=new JsonParser().parse(line).getAsJsonObject();
            if(json.has("type") && "markdown".equals(string(json,"type",""))) MarkdownRenderer.add(panel,string(json,"text",""),depth);
            else if(json.has("type")) addComponent(panel,json);
            else panel.add(new TextField(panel).setMaxWidth(panel.width).setSpacing(9).setText(ITextComponent.Serializer.jsonToComponent(line)));
        }
        catch(Exception ex){panel.add(new TextField(panel).setMaxWidth(panel.width).setText(line));}
    }
    private static void flush(Panel panel,List<String> plain)
    {
        if(plain.isEmpty())return;
        String text=String.join("\n",plain);plain.clear();
        panel.add(new TextField(panel).setMaxWidth(panel.width).setSpacing(9).setText(FTBQuestsClient.addI18nAndColors(substitute(text))));
    }
    public static String substitute(String text)
    {
        var player=Minecraft.getMinecraft().player;if(player==null)return text;
        var matcher=java.util.regex.Pattern.compile("\\$\\{(persisted|player|kubejs):([a-zA-Z0-9_.-]+)}").matcher(text);StringBuffer result=new StringBuffer();
        while(matcher.find())
        {
            NBTBase value=matcher.group(1).equals("persisted")?com.feed_the_beast.ftblib.lib.util.NBTUtils.getPersistedData(player,false):player.getEntityData();
            if(matcher.group(1).equals("kubejs"))value=com.feed_the_beast.ftblib.lib.util.NBTUtils.getPersistedData(player,false).getCompoundTag("KubeJS");
            for(String segment:matcher.group(2).split("\\."))value=value instanceof NBTTagCompound?((NBTTagCompound)value).getTag(segment):null;
            String synced=DescriptionData.get(matcher.group(1)+":"+matcher.group(2));
            String replacement=synced!=null?synced:value instanceof NBTTagString?((NBTTagString)value).getString():value==null?"":value.toString();matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);return result.toString();
    }
    private static void addComponent(Panel panel,JsonObject json) throws Exception
    {
        String type=string(json,"type","text"),text=string(json,"text",type),value=string(json,"value","");
        int componentWidth=Math.min(panel.width,Math.max(8,integer(json,"width",panel.width))),componentHeight=Math.min(512,Math.max(8,integer(json,"height",componentWidth*9/16)));
        switch(type)
        {
            case "image": case "video":
                panel.add(new DescriptionMediaWidget(panel,json,type.equals("video")));break;
            case "entity":
            {
                Icon entity=new EntityModelIcon(string(json,"source",""),string(json,"nbt","{}"));
                panel.add(new Widget(panel)
                {
                    {setSize(componentWidth,componentHeight);}
                    @Override public void draw(Theme theme,int x,int y,int w,int h)
                    {
                        entity.draw(x,y,w,componentHeight);
                    }
                });break;
            }
            case "table":
            {
                JsonArray rows=json.getAsJsonArray("rows");if(rows==null || rows.size()>128)return;
                int columns=1;for(JsonElement row:rows)columns=Math.max(columns,row.getAsJsonArray().size());final int cols=columns;
                for(JsonElement row:rows)
                {
                    JsonArray cells=row.getAsJsonArray();int rowHeight=12;
                    List<String[]> wrapped=new ArrayList<>();for(JsonElement cell:cells){String[] data=Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(cell.getAsString(),Math.max(8,componentWidth/cols-6)).toArray(new String[0]);wrapped.add(data);rowHeight=Math.max(rowHeight,data.length*10+4);}final int rh=rowHeight;
                    panel.add(new Widget(panel){{setSize(componentWidth,rh);}@Override public void draw(Theme theme,int x,int y,int w,int h){for(int i=0;i<cols;i++){GuiHelper.drawHollowRect(x+i*w/cols,y,w/cols,h,Color4I.GRAY,false);if(i<wrapped.size()){int line=0;for(String cell:wrapped.get(i))theme.drawString(cell,x+i*w/cols+3,y+3+line++*10,Color4I.WHITE,0);}}}});
                }break;
            }
            case "item":
            {
                NBTTagCompound tag=JsonToNBT.getTagFromJson(string(json,"nbt","{}"));tag.setString("id",string(json,"item",value));tag.setByte("Count",(byte)1);ItemStack stack=new ItemStack(tag);
                if(json.has("size"))
                {
                    int size=Math.min(Math.max(8,componentWidth),Math.max(8,Math.min(256,integer(json,"size",32))));
                    panel.add(new SimpleButton(panel,text,ItemIcon.getItemIcon(stack),(widget,button)->com.feed_the_beast.ftbquests.integration.jei.FTBQuestsJEIHelper.showRecipes(stack,button.isRight())) {
                        {setSize(Math.min(componentWidth,Math.max(size+4,120)),size+4+(text.isEmpty()?0:12));}
                        @Override public void draw(Theme theme,int x,int y,int w,int h) {
                            GuiHelper.drawItem(stack,x+(w-size)/2,y+2,size/16D,size/16D,true);
                            if(!text.isEmpty()) {String caption=theme.trimStringToWidth(text,Math.max(1,w-4));theme.drawString(caption,x+(w-theme.getStringWidth(caption))/2,y+size+5,Color4I.WHITE,0);}
                        }
                        @Override public void addMouseOverText(List<String> tooltip) {tooltip.addAll(stack.getTooltip(Minecraft.getMinecraft().player,net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL));}
                    });break;
                }
                panel.add(new SimpleButton(panel,text,ItemIcon.getItemIcon(stack),(widget,button)->com.feed_the_beast.ftbquests.integration.jei.FTBQuestsJEIHelper.showRecipes(stack,button.isRight())){{setSize(Math.min(componentWidth,100),20);}@Override public void addMouseOverText(List<String> tooltip){tooltip.addAll(stack.getTooltip(Minecraft.getMinecraft().player,net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL));}});break;
            }
            case "font":
            {
                var font=QuestFonts.get(string(json,"font",""));
                float scale=Math.max(0.5F,Math.min(4F,json.has("scale")?json.get("scale").getAsFloat():1F));
                var wrapped=font.listFormattedStringToWidth(substitute(text),(int)(componentWidth/scale));int color=integer(json,"color",0xFFFFFF);
                panel.add(new Widget(panel){{setSize(componentWidth,(int)Math.ceil(wrapped.size()*font.FONT_HEIGHT*scale));}@Override public void draw(Theme theme,int x,int y,int w,int h){net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.translate(x,y,0);net.minecraft.client.renderer.GlStateManager.scale(scale,scale,1);int line=0;for(String value:wrapped)font.drawStringWithShadow(value,0,line++*font.FONT_HEIGHT,color);net.minecraft.client.renderer.GlStateManager.popMatrix();}});break;
            }
            case "localize": panel.add(new TextField(panel).setMaxWidth(componentWidth).setText(new TextComponentTranslation(value)));break;
            case "keybind":
                String key=value;for(var binding:Minecraft.getMinecraft().gameSettings.keyBindings)if(binding.getKeyDescription().equals(value)){key=binding.getDisplayName();break;}
                panel.add(new TextField(panel).setMaxWidth(componentWidth).setText(key));break;
            case "heading": panel.add(new TextField(panel).setMaxWidth(componentWidth).setScale(1.4F).setText(TextFormatting.BOLD+text));break;
            case "obfuscated": panel.add(new TextField(panel).setMaxWidth(componentWidth).setText(TextFormatting.OBFUSCATED+text));break;
            case "ponder":
            {
                String caption=json.has("text")&&!text.isEmpty()?substitute(text):PonderLink.tr("open");
                panel.add(new SimpleTextButton(panel,caption,PonderLink.icon(value))
                {
                    {setSize(componentWidth,24);}
                    @Override public void onClicked(MouseButton button){if(button.isLeft()){GuiHelper.playClickSound();handleClick("ponder",value);}}
                    @Override public void addMouseOverText(List<String> tooltip){super.addMouseOverText(tooltip);tooltip.add(json.has("hover")?string(json,"hover",""):PonderLink.tr("open"));tooltip.add(value);}
                });break;
            }
            default:
            {
                String action=type.equals("link")?value:type.equals("quest")?"quest:"+value:type.equals("command")?"command:"+value:type.equals("ponder")?"ponder:"+value:type.equals("zs")?"zs:"+value:"";
                TextField field=new TextField(panel)
                {
                    @Override public boolean mousePressed(MouseButton button){if(!isMouseOver() || !button.isLeft())return false;if(type.equals("copy")){net.minecraft.client.gui.GuiScreen.setClipboardString(value);return true;}return !action.isEmpty() && handleClick(action);}
                    @Override public void addMouseOverText(List<String> tooltip){if(json.has("hover"))tooltip.add(json.get("hover").getAsString());}
                };
                field.setMaxWidth(componentWidth).setText(substitute(text));panel.add(field);break;
            }
        }
    }
}
