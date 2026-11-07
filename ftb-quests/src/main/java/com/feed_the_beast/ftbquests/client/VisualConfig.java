package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.FTBQuests;
import net.minecraftforge.common.config.Config;

@Config(modid = FTBQuests.MOD_ID, name = "ftbquests/client", category = "visual")
public final class VisualConfig
{
    public static boolean questHoverAnimation = true;
    public static boolean navigationHoverAnimation = true;
    public static boolean detailPanelAnimation = true;
    public static boolean chapterPanelAnimation = true;
    public static boolean smoothZoom = true;
    public static boolean smoothAnimations = true;
    public static boolean rewardOpeningAnimation = true;
    public static boolean rewardOpeningSounds = true;
    @Config.RangeInt(min=0, max=255) public static int glowOpacity = 110;
    @Config.RangeInt(min=0, max=255) public static int buttonGlowOpacity = 40;
    @Config.RangeInt(min=30, max=1000) public static int smoothingMilliseconds = 220;
    @Config.RangeInt(min=30, max=1500) public static int sidebarAnimationMilliseconds = 350;
    @Config.RangeInt(min=30, max=1500) public static int detailAnimationMilliseconds = 320;
    public static boolean shaderBackground = false;
    public static String shader = "ftbquests:shaders/grid.frag";
    public static boolean renderKillTaskEntityModels = true;
    public static boolean openAdvancementTasks = true;
}
