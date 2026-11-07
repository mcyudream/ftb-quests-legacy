package com.feed_the_beast.ftbquests.integration.ponder.client;

import com.feed_the_beast.ftbquests.client.PonderLink;
import com.feed_the_beast.ftbquests.util.PonderTarget;
import net.createmod.ponder.api.guide.Tutorial;
import net.createmod.ponder.client.guide.*;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** Guide types are isolated so animation-only Ponder 1.4 can still open scene links. */
final class ModernPonderBridge
{
    static String title(ResourceLocation scene){var page=PonderIndex.getTutorials().get(scene);return page==null?"":page.title();}
    static void addOptions(Map<String,PonderLink.Option> entries)
    {
        for(var page:PonderIndex.getTutorials().all())for(var component:page.components())
        {
            var stack=PonderBridge.item(component);String value=component+"#"+page.id();
            entries.putIfAbsent(component.toString(),new PonderLink.Option(component.toString(),stack.getDisplayName()+" · "+PonderLink.tr("all"),stack));
            entries.put(value,new PonderLink.Option(value,stack.getDisplayName()+" · "+page.title(),stack));
        }
    }
    static GuiScreen open(PonderTarget target,GuiScreen previous)
    {
        ResourceLocation component=new ResourceLocation(target.component()),selected=target.scene().isEmpty()?null:new ResourceLocation(target.scene());
        if(!Tutorials.exists(component))throw new IllegalArgumentException("No Ponder tutorial for "+component);
        Tutorial page=selected==null?null:PonderIndex.getTutorials().forComponent(component).stream().filter(p->p.id().equals(selected)||Objects.equals(p.animation(),selected)).findFirst().orElse(null);
        List<PonderScene> scenes=PonderIndex.getSceneAccess().compile(component);
        if(selected!=null&&page==null&&scenes.stream().noneMatch(s->s.getId().equals(selected)))throw new IllegalArgumentException("No matching Ponder scene");
        if(page!=null&&page.presentation()==Tutorial.Presentation.GUIDE)return new GuideScreen(component,page.id()).returningTo(previous);
        ResourceLocation scene=page!=null&&page.animation()!=null?page.animation():selected;
        boolean preferGuide=Tutorials.preferGuide()&&(page==null||page.presentation()!=Tutorial.Presentation.ANIMATION);
        if(scenes.isEmpty()||preferGuide)return new GuideScreen(component,page!=null?page.id():scene).returningTo(previous);
        PonderUI animation=new ReturningAnimation(scenes,component,previous);
        if(scene!=null)animation.selectScene(scene);
        return animation;
    }
    private static final class ReturningAnimation extends PonderUI
    {
        private final ResourceLocation component;
        private final GuiScreen previous;
        ReturningAnimation(List<PonderScene> scenes,ResourceLocation component,GuiScreen previous)
        {super(scenes);this.component=component;this.previous=previous;returningTo(previous);}
        @Override public void initGui()
        {
            var id=getActiveScene().getId();
            var page=PonderIndex.getTutorials().forComponent(component).stream().filter(p->p.id().equals(id)||Objects.equals(p.animation(),id)).findFirst().orElse(null);
            boolean hasGuide=page==null||page.presentation()!=Tutorial.Presentation.ANIMATION;
            withGuide(hasGuide?()->new GuideScreen(component,id).returningTo(previous):null);
            super.initGui();
            for(var button:buttonList)if(button.id==10)button.enabled=hasGuide;
        }
    }
}
