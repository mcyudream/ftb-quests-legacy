package com.feed_the_beast.ftbquests.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.player.IPlayer;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

@ZenRegister
@ZenClass("mods.ftbquests.IQuestTextClick")
@FunctionalInterface
public interface IQuestTextClick
{
    @ZenMethod void click(IPlayer player, String action, String sourceID, int teamUID);
}
