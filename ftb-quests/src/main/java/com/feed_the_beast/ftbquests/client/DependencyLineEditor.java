package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfig;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.net.edit.MessageEditObject;
import com.feed_the_beast.ftbquests.quest.*;
import net.minecraft.client.resources.I18n;
import java.util.*;

public final class DependencyLineEditor
{
    public static void addMenu(List<ContextMenuItem> menu, GuiQuestTree gui, Quest quest)
    {
        menu.add(new ContextMenuItem(I18n.format("ftbquests.gui.edit_lines"), GuiIcons.INFO, () -> {
            ConfigGroup group = ConfigGroup.newGroup("ftbquests"); Set<Quest> changed = new HashSet<>();
            group.setDisplayName(new net.minecraft.util.text.TextComponentTranslation("ftbquests.gui.edit_lines"));
            for (Chapter chapter : gui.file.chapters) for (Quest target : chapter.quests)
                for (QuestObject dependency : target.dependencies)
                    if (dependency instanceof Quest && (target == quest || dependency == quest))
                    {
                        int id = dependency.id; ConfigGroup line = group.getGroup(target.getCodeString()+"_"+dependency.getCodeString());
                        line.setDisplayName(new net.minecraft.util.text.TextComponentString(target.getTitle()+" ← "+dependency.getTitle()));
                        line.addBool("hidden", () -> target.hiddenDependencyLines.contains(id), v -> { if(v) target.hiddenDependencyLines.add(id); else target.hiddenDependencyLines.remove(id); changed.add(target); },false).setDisplayName(new net.minecraft.util.text.TextComponentTranslation("ftbquests.gui.line_hidden")).setInfo(new net.minecraft.util.text.TextComponentTranslation("ftbquests.gui.line_hidden.info"));
                        line.addBool("hover_only", () -> target.hoverDependencyLines.contains(id), v -> { if(v) target.hoverDependencyLines.add(id); else target.hoverDependencyLines.remove(id); changed.add(target); },false).setDisplayName(new net.minecraft.util.text.TextComponentTranslation("ftbquests.gui.line_hover_only")).setInfo(new net.minecraft.util.text.TextComponentTranslation("ftbquests.gui.line_hover_only.info"));
                    }
            new GuiEditConfig(group, (config,sender) -> { for(Quest target:changed) MessageEditObject.send(target); gui.openGui(); }).openGui();
        }));
    }
}
