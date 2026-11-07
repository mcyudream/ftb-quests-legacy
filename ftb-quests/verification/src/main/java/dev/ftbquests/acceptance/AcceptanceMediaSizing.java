package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.texteditor.*;
import com.feed_the_beast.ftbquests.util.QuestAssets;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import java.nio.file.*;
import java.util.*;

/** Checks real preview reflow, explicit legacy sizes, scrolling, and native video metadata. */
final class AcceptanceMediaSizing
{
    private final GuiQuestTree tree;
    private final Quest quest;
    private final List<String> original;
    private final Map<String,Object> checks;
    private final boolean withVideo = Boolean.getBoolean("ftbquests.acceptance.video");
    private final String imageSource, videoSource;
    private final GuiQuestTextEditor editor;
    private JsonObject accepted;
    private VideoBackend video;
    private int ticks, stage;

    AcceptanceMediaSizing(GuiQuestTree tree, Quest quest, String imageSource, String videoSource, Map<String,Object> checks) throws Exception
    {
        this.tree=tree;this.quest=quest;this.imageSource=imageSource;this.videoSource=videoSource;this.checks=checks;
        original=List.copyOf(quest.description);
        byte[] bytes=Files.readAllBytes(Path.of("picker-fixtures/横向 图片.png"));String name=QuestAssets.name(bytes,"png");
        Path cached=Path.of("config/ftbquests/.asset-cache").resolve(name);Files.createDirectories(cached.getParent());Files.write(cached,bytes);
        editor=new GuiQuestTextEditor(quest);editor.openGui();
        String source="# 自动铺满宽度\n![横向图片](asset:"+name+")\n"+
            "{\"type\":\"image\",\"source\":\""+imageSource+"\"}\n"+
            "{\"type\":\"image\",\"source\":\""+imageSource+"\",\"width\":96}\n"+
            "{\"type\":\"image\",\"source\":\""+imageSource+"\",\"width\":96,\"height\":48,\"unknown\":42}\n"+
            "{\"type\":\"video\",\"source\":\""+videoSource+"\"}\n后续正文不能被媒体覆盖\n";
        editor.source.edit(()->editor.source.buffer.replaceRange(0,editor.source.buffer.text().length(),source));
        if(withVideo)video=VideoBackend.get(videoSource);
    }
    boolean tick() throws Exception
    {
        ticks++;
        if(ticks>650)throw new AssertionError("Responsive media timeout at "+stage);
        if(video!=null)
        {
            video.draw(-1000,-1000,64,64,"");
            if(video.status().isEmpty()&&!video.isPlaying()&&!video.hasFrame())video.toggle();
        }
        if(stage==0&&ticks>25&&(!withVideo||video.hasFrame()))
        {
            verify(editor.preview);
            checks.put("wide_preview_landscape_portrait_and_fixed_sizes",true);shot("media-auto-wide.png");
            editor.setSize(420,270);editor.refreshWidgets();click(editor,GuiQuestTextEditor.tr("preview"));stage=1;ticks=0;
        }
        else if(stage==1&&ticks>12)
        {
            AcceptanceMod.require(editor.widgets.contains(editor.preview),"Narrow preview is hidden");verify(editor.preview);
            AcceptanceMod.require(media(editor.preview).get(1).height>512,"Tall auto image is still limited to the old 512-pixel box");
            checks.put("narrow_preview_resizes_media_and_scroll_extent",true);shot("media-auto-narrow.png");
            editor.preview.setScrollY(editor.preview.attachedScrollbar.getMaxValue());stage=2;ticks=0;
        }
        else if(stage==2&&ticks>8)
        {
            Widget last=editor.preview.widgets.get(editor.preview.widgets.size()-1);
            AcceptanceMod.require(last.posY+last.height-editor.preview.getScrollY()<=editor.preview.height,"Last paragraph cannot be reached by scrolling");
            shot("media-auto-scroll-bottom.png");
            JsonObject json=new JsonObject();json.addProperty("type","image");json.addProperty("source",imageSource);json.addProperty("future",42);
            GuiDescriptionComponent form=new GuiDescriptionComponent(json,value->{accepted=value;editor.openGui();});form.openGui();
            AcceptanceMod.require(form.field("width").isEmpty()&&form.field("height").isEmpty(),"The media form forces fixed dimensions");
            AcceptanceMod.require(!GuiQuestTextEditor.tr("media_size_hint").startsWith("ftbquests."),"Auto-size hint is not localized");
            click(form,GuiQuestTextEditor.tr("insert_apply"));stage=3;ticks=0;
        }
        else if(stage==3&&ticks>5)
        {
            AcceptanceMod.require(accepted!=null&&!accepted.has("width")&&!accepted.has("height")&&accepted.get("future").getAsInt()==42,"Blank dimensions were persisted or unknown fields lost");
            AcceptanceMod.require(quest.description.equals(original),"Preview/layout modified saved quest JSON");
            checks.put("blank_size_form_keeps_auto_and_unknown_fields",true);
            checks.put("preview_reflow_does_not_modify_saved_description",true);
            if(video!=null){video.toggle();checks.put("square_native_video_auto_height_and_controls",true);}
            tree.open(quest,false);return true;
        }
        return false;
    }
    private void verify(Panel panel)
    {
        List<Widget> media=media(panel);AcceptanceMod.require(media.size()==5,"Missing responsive media widgets");
        Widget landscape=media.get(0),portrait=media.get(1),widthOnly=media.get(2),fixed=media.get(3),movie=media.get(4);
        AcceptanceMod.require(landscape.width==panel.width&&landscape.height==Math.round(panel.width/2F),"Landscape image does not fill width with its native aspect");
        AcceptanceMod.require(portrait.width==panel.width&&portrait.height==panel.width*2,"Portrait image does not fill width with its native aspect");
        AcceptanceMod.require(widthOnly.width==96&&widthOnly.height==192,"Width-only image does not preserve aspect");
        AcceptanceMod.require(fixed.width==96&&fixed.height==48,"Explicit legacy size changed");
        int movieHeight=withVideo?panel.width:Math.round(panel.width*9F/16F);
        AcceptanceMod.require(movie.width==panel.width&&movie.height==movieHeight+32,"Auto video height or controls do not follow its dimensions");
        if(withVideo)AcceptanceMod.require(video.dimensions().width()==64&&video.dimensions().height()==64,"Video dimensions do not come from the real decoder");
        int bottom=0;
        for(Widget widget:panel.widgets){AcceptanceMod.require(widget.posY>=bottom,"Resized media overlaps following content");bottom=widget.posY+widget.height;}
        AcceptanceMod.require(panel.attachedScrollbar.getMaxValue()>=bottom-panel.height,"Scroll range was not updated after media resize");
        AcceptanceMod.require(org.lwjgl.opengl.GL11.glGetError()==0,"Responsive media caused GL error");
    }
    private static List<Widget> media(Panel panel){return panel.widgets.stream().filter(w->w.getClass().getSimpleName().equals("DescriptionMediaWidget")).toList();}
    private static void click(GuiBase gui,String title)
    {
        Widget button=gui.widgets.stream().filter(w->w instanceof Button&&w.getTitle().equals(title)).findFirst().orElseThrow();
        gui.updateGui(button.getX()+3,button.getY()+3,0);gui.mousePressed(MouseButton.LEFT);gui.mouseReleased(MouseButton.LEFT);
    }
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
