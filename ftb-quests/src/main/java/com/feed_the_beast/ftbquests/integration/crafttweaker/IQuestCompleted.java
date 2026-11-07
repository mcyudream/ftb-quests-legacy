package com.feed_the_beast.ftbquests.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.player.IPlayer;
import stanhebben.zenscript.annotations.*;

@ZenRegister
@ZenClass("mods.ftbquests.IQuestCompleted")
@FunctionalInterface
public interface IQuestCompleted
{
    @ZenMethod void complete(IPlayer player, String objectID, int teamUID);
}
