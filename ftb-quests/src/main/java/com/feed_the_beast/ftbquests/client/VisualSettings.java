package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfig;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import java.io.File;
import java.lang.reflect.Modifier;

/** Client preferences are editable by players and never modify the server quest book. */
public final class VisualSettings
{
    public static void open(GuiQuestTree tree)
    {
        ConfigGroup root = ConfigGroup.newGroup(FTBQuests.MOD_ID), group = root.getGroup("visual");
        group.addBool("questHoverAnimation", () -> VisualConfig.questHoverAnimation, v -> VisualConfig.questHoverAnimation=v, true);
        group.addBool("navigationHoverAnimation", () -> VisualConfig.navigationHoverAnimation, v -> VisualConfig.navigationHoverAnimation=v, true);
        group.addBool("detailPanelAnimation", () -> VisualConfig.detailPanelAnimation, v -> VisualConfig.detailPanelAnimation=v, true);
        group.addBool("chapterPanelAnimation", () -> VisualConfig.chapterPanelAnimation, v -> VisualConfig.chapterPanelAnimation=v, true);
        group.addBool("smoothZoom", () -> VisualConfig.smoothZoom, v -> VisualConfig.smoothZoom=v, true);
        group.addBool("smoothAnimations", () -> VisualConfig.smoothAnimations, v -> VisualConfig.smoothAnimations=v, true);
        group.addBool("rewardOpeningAnimation", () -> VisualConfig.rewardOpeningAnimation, v -> VisualConfig.rewardOpeningAnimation=v, true);
        group.addBool("rewardOpeningSounds", () -> VisualConfig.rewardOpeningSounds, v -> VisualConfig.rewardOpeningSounds=v, true);
        group.addInt("glowOpacity", () -> VisualConfig.glowOpacity, v -> VisualConfig.glowOpacity=v, 110, 0, 255);
        group.addInt("buttonGlowOpacity", () -> VisualConfig.buttonGlowOpacity, v -> VisualConfig.buttonGlowOpacity=v, 40, 0, 255);
        group.addInt("smoothingMilliseconds", () -> VisualConfig.smoothingMilliseconds, v -> VisualConfig.smoothingMilliseconds=v, 220, 30, 1000);
        group.addInt("sidebarAnimationMilliseconds", () -> VisualConfig.sidebarAnimationMilliseconds, v -> VisualConfig.sidebarAnimationMilliseconds=v, 350, 30, 1500);
        group.addInt("detailAnimationMilliseconds", () -> VisualConfig.detailAnimationMilliseconds, v -> VisualConfig.detailAnimationMilliseconds=v, 320, 30, 1500);
        group.addBool("shaderBackground", () -> VisualConfig.shaderBackground, v -> VisualConfig.shaderBackground=v, false);
        group.addString("shader", () -> VisualConfig.shader, v -> VisualConfig.shader=v, "ftbquests:shaders/grid.frag");
        group.addBool("renderKillTaskEntityModels", () -> VisualConfig.renderKillTaskEntityModels, v -> VisualConfig.renderKillTaskEntityModels=v, true);
        group.addBool("openAdvancementTasks", () -> VisualConfig.openAdvancementTasks, v -> VisualConfig.openAdvancementTasks=v, true);
        new GuiEditConfig(root, (config, sender) -> { save(); tree.openGui(); }).openGui();
    }
    private static void save()
    {
        Configuration config = new Configuration(new File(Loader.instance().getConfigDir(), "ftbquests/client.cfg"));
        config.load();
        try
        {
            for (var field : VisualConfig.class.getDeclaredFields())
            {
                if (!Modifier.isPublic(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() == boolean.class) config.get("visual", field.getName(), field.getBoolean(null)).set(field.getBoolean(null));
                else if (field.getType() == int.class) config.get("visual", field.getName(), field.getInt(null)).set(field.getInt(null));
                else if (field.getType() == String.class) config.get("visual", field.getName(), (String)field.get(null)).set((String)field.get(null));
            }
            config.save();
        }
        catch (IllegalAccessException ex) { throw new IllegalStateException("Could not save quest visual settings", ex); }
    }
}
