package com.feed_the_beast.ftbquests.gui;

import com.feed_the_beast.ftblib.lib.icon.*;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.quest.loot.RewardTable;
import com.feed_the_beast.ftbquests.util.RewardOpening;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.model.ModelChest;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.*;

/** A presentation of an already granted server result. Closing/skipping never sends a claim. */
public final class GuiLootOpening extends GuiScreen
{
    private static final int W=320,H=238,DURATION=3200,STOP=30;
    public final RewardOpening.Result result;
    private final GuiScreen previous;
    private final List<RewardKey> rewards=new ArrayList<>(),previews=new ArrayList<>();
    private final String title;
    private final ModelChest chestModel=new ModelChest();
    private static final ResourceLocation CHEST_TEXTURE=new ResourceLocation("textures/entity/chest/normal.png");
    private final Theme theme=FTBQuestsTheme.INSTANCE;
    private long started=System.nanoTime(),lastTickSound;
    private boolean skipped,openSound,revealSound;
    private int lastCell=-1,scroll;
    private float scale=1;
    private int originX,originY;
    public GuiLootOpening(RewardOpening.Result result,GuiScreen previous)
    {
        this.result=result;this.previous=previous;
        RewardTable table=ClientQuestFile.exists()?ClientQuestFile.INSTANCE.getRewardTable(result.tableId):null;
        title=table==null?tr("title"):table.lootCrate!=null&&!table.lootCrate.itemName.isEmpty()?table.lootCrate.itemName:table.getTitle();
        for(var entry:result.entries)rewards.add(key(entry));
        if(table!=null&&!table.hideTooltip)
            for(var entry:table.rewards)if(entry.weight>0&&previews.size()<32)previews.add(new RewardKey(entry.reward.getTitle(),entry.reward.getIcon()));
        if(previews.isEmpty())previews.add(new RewardKey(tr("mystery"),ItemIcon.getItemIcon(new ItemStack(Blocks.CHEST))));
        skipped=!VisualConfig.rewardOpeningAnimation;
    }
    public static RewardKey key(RewardOpening.Entry entry)
    {
        if(!entry.stack.isEmpty())return new RewardKey(entry.stack.getRarity().color+entry.stack.getDisplayName(),ItemIcon.getItemIcon(entry.stack)).setStack(entry.stack);
        if(entry.index>=0&&ClientQuestFile.exists())
        {
            RewardTable table=ClientQuestFile.INSTANCE.getRewardTable(entry.tableId);
            if(table!=null&&entry.index<table.rewards.size()){
                var reward=table.rewards.get(entry.index).reward;return new RewardKey(reward.getTitle(),reward.getIcon());
            }
        }
        return new RewardKey(entry.text.getFormattedText().isEmpty()?tr("other"):entry.text.getFormattedText(),entry.icon.isEmpty()?ItemIcon.getItemIcon(new ItemStack(Items.EXPERIENCE_BOTTLE)):entry.icon);
    }
    public static String tr(String key,Object... args){return I18n.format("ftbquests.opening."+key,args);}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void initGui()
    {scale=Math.min(1F,Math.min((width-16F)/W,(height-16F)/H));scale=Math.max(.1F,scale);originX=Math.round((width-W*scale)/2);originY=Math.round((height-H*scale)/2);}
    private double elapsed(){return skipped?DURATION:(System.nanoTime()-started)/1_000_000D;}
    public boolean isRevealed(){return elapsed()>=DURATION;}
    public void skipAnimation(){skipped=true;}
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
    private static double smooth(double value){return Easing.curve(value);}
    private double reel(){return STOP*smooth((elapsed()-650)/(DURATION-650D));}
    @Override public void updateScreen()
    {
        if(!VisualConfig.rewardOpeningSounds)return;
        if(!openSound){openSound=true;if(!skipped)sound(SoundEvents.BLOCK_CHEST_OPEN,.45F,.9F);}
        if(isRevealed()){if(!revealSound){revealSound=true;sound(SoundEvents.ENTITY_PLAYER_LEVELUP,.25F,1.15F);}return;}
        int cell=(int)reel();long now=System.nanoTime();
        if(elapsed()>650&&cell!=lastCell&&now-lastTickSound>90_000_000L){lastCell=cell;lastTickSound=now;sound(SoundEvents.UI_BUTTON_CLICK,.15F,1.3F);}
    }
    private void sound(net.minecraft.util.SoundEvent event,float volume,float pitch)
    {mc.getSoundHandler().playSound(new PositionedSoundRecord(event.getSoundName(),net.minecraft.util.SoundCategory.MASTER,volume,pitch,false,0,net.minecraft.client.audio.ISound.AttenuationType.NONE,0,0,0));}
    private boolean over(int mx,int my,int x,int y,int w,int h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private void text(String text,int x,int y){theme.drawString(text,x,y,theme.getContentColor(WidgetType.NORMAL),Theme.SHADOW);}
    private void center(String text,int y){text(text,(W-theme.getStringWidth(text))/2,y);}
    private void muted(String text,int y){theme.drawString(text,(W-theme.getStringWidth(text))/2,y,theme.getContentColor(WidgetType.DISABLED),0);}
    private String trim(String text,int width){return fontRenderer.trimStringToWidth(text,width);}
    private void border(int x,int y,int w,int h,int color){drawRect(x,y,x+w,y+1,color);drawRect(x,y+h-1,x+w,y+h,color);drawRect(x,y,x+1,y+h,color);drawRect(x+w-1,y,x+w,y+h,color);}
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks)
    {
        drawDefaultBackground();
        int mx=(int)((mouseX-originX)/scale),my=(int)((mouseY-originY)/scale);
        GlStateManager.pushMatrix();GlStateManager.translate(originX,originY,0);GlStateManager.scale(scale,scale,1);
        theme.drawGui(0,0,W,H,WidgetType.NORMAL);
        GuiHelper.drawHollowRect(0,0,W,H,com.feed_the_beast.ftbquests.quest.theme.property.ThemeProperties.WIDGET_BORDER.get(),false);
        center(trim(title,W-64),12);
        muted(result.boxes>1?tr("batch",result.boxes):tr("subtitle"),27);
        boolean closeHover=over(mx,my,W-22,6,16,16);
        theme.drawButton(W-22,6,16,16,WidgetType.mouseOver(closeHover));GuiIcons.CLOSE.draw(W-20,8,12,12);
        List<String> tooltip=null;
        if(!isRevealed())
        {
            double t=elapsed(),opening=smooth((t-280)/480D);
            chest(W/2,74,opening,t<650?Math.sin(t*.025)*smooth(t/160D)*(1-smooth((t-350)/300D)):0);
            center(tr(t<650?"opening":"rolling"),96);
            reel((float)reel());
            muted(tr("granted"),190);
        }
        else
        {
            chest(W/2,74,1,0);center(tr("revealed"),96);
            double settle=skipped?0:4*(1-smooth((elapsed()-DURATION)/240D));
            GlStateManager.pushMatrix();GlStateManager.translate(0,settle,0);
            if(rewards.isEmpty())
            {center(tr("empty"),128);muted(tr("empty_hint"),146);}
            else
            {
                int columns=3,rows=(rewards.size()+columns-1)/columns;
                scroll=Math.max(0,Math.min(scroll,Math.max(0,rows-2)));
                for(int slot=0;slot<6;slot++)
                {
                    int index=scroll*columns+slot;if(index>=rewards.size())break;
                    int inRow=Math.min(columns,rewards.size()-(index/columns)*columns);
                    int x=(W-inRow*100)/2+(slot%columns)*100,y=(rewards.size()<=3?127:111)+(slot/columns)*37;RewardKey reward=rewards.get(index);
                    boolean hover=over(mx,(int)Math.floor(my-settle),x,y,96,33);
                    if(hover)theme.drawButton(x,y,96,33,WidgetType.MOUSE_OVER);
                    theme.drawContainerSlot(x+3,y+7,16,16);reward.icon.draw(x+3,y+7,16,16);
                    text(trim(reward.title,70),x+24,y+5);
                    text(trim("×"+result.entries.get(index).count,70),x+24,y+19);
                    if(hover)tooltip=List.of(reward.title,"×"+result.entries.get(index).count);
                }
                if(rows>2)muted(tr("scroll",scroll+1,rows-1),186);
            }
            if(result.omitted>0)muted(tr("more",result.omitted),199);
            else if(!rewards.isEmpty())muted(tr("granted"),199);
            GlStateManager.popMatrix();
        }
        boolean hover=over(mx,my,110,211,100,20);
        theme.drawButton(110,211,100,20,WidgetType.mouseOver(hover));
        String label=tr(isRevealed()?"done":"skip");theme.drawString(label,(W-theme.getStringWidth(label))/2,217,theme.getContentColor(WidgetType.mouseOver(hover)),Theme.SHADOW);
        GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);
        if(tooltip!=null)drawHoveringText(tooltip,mouseX,mouseY);
    }
    private void reel(float position)
    {
        theme.drawPanelBackground(12,110,W-24,65);
        GuiHelper.pushScissor(new ScaledResolution(mc),originX+Math.round(16*scale),originY+Math.round(113*scale),Math.round((W-32)*scale),Math.round(60*scale));
        for(int i=Math.max(0,(int)position-4);i<=Math.min(STOP+3,(int)position+4);i++)
        {
            double exactX=W/2D+(i-position)*52-24;
            int x=(int)Math.floor(exactX);
            GlStateManager.pushMatrix();GlStateManager.translate(exactX-x,0,0);
            RewardKey reward=i==STOP?(rewards.isEmpty()?new RewardKey(tr("empty"),Icon.EMPTY):rewards.get(0)):previews.get(i%previews.size());
            theme.drawContainerSlot(x+11,119,26,26);reward.icon.draw(x+12,120,24,24);
            String label=trim(reward.title,46);text(label,x+(48-theme.getStringWidth(label))/2,156);
            GlStateManager.popMatrix();
        }
        GuiHelper.popScissor(new ScaledResolution(mc));
        GuiHelper.drawHollowRect(W/2-17,115,34,34,theme.getContentColor(WidgetType.MOUSE_OVER),false);
        text("▼",W/2-theme.getStringWidth("▼")/2,104);
    }
    /** Vanilla chest model and texture; resource packs apply to this preview too. */
    private void chest(int cx,int cy,double open,double shake)
    {
        GlStateManager.pushMatrix();GlStateManager.translate(cx+(float)shake,cy,100F);
        GlStateManager.scale(30F,30F,30F);GlStateManager.rotate(18,1,0,0);GlStateManager.rotate(-30,0,1,0);
        GlStateManager.translate(-.5F,-.5F,-.5F);
        GlStateManager.enableDepth();GlStateManager.enableRescaleNormal();RenderHelper.enableStandardItemLighting();
        mc.getTextureManager().bindTexture(CHEST_TEXTURE);GlStateManager.color(1,1,1,1);
        chestModel.chestLid.rotateAngleX=-(float)(open*Math.PI/2);chestModel.renderAll();
        RenderHelper.disableStandardItemLighting();GlStateManager.disableRescaleNormal();GlStateManager.disableDepth();
        GlStateManager.popMatrix();GlStateManager.color(1,1,1,1);
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException
    {
        if(button!=0)return;int mx=(int)((mouseX-originX)/scale),my=(int)((mouseY-originY)/scale);
        if(over(mx,my,W-22,6,16,16)){mc.displayGuiScreen(previous);return;}
        if(over(mx,my,110,211,100,20)){if(isRevealed())mc.displayGuiScreen(previous);else skipAnimation();}
    }
    @Override protected void keyTyped(char typedChar,int keyCode)throws IOException
    {
        if(keyCode==Keyboard.KEY_ESCAPE||keyCode==Keyboard.KEY_RETURN||keyCode==Keyboard.KEY_SPACE||mc.gameSettings.keyBindInventory.isActiveAndMatches(keyCode))
        {if(isRevealed())mc.displayGuiScreen(previous);else skipAnimation();}
    }
    @Override public void handleMouseInput()throws IOException
    {
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();
        if(isRevealed()&&wheel!=0)scroll=Math.max(0,Math.min(scroll+(wheel>0?-1:1),Math.max(0,(rewards.size()+2)/3-2)));
    }
}
