package com.feed_the_beast.ftbquests.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.player.IPlayer;
import stanhebben.zenscript.annotations.*;

@ZenRegister
@ZenClass("mods.ftbquests.IQuestReward")
@FunctionalInterface
public interface IQuestReward
{
    @ZenMethod void give(IPlayer player);
}
