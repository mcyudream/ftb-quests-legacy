package com.feed_the_beast.ftbquests.integration.crafttweaker;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.player.IPlayer;
import stanhebben.zenscript.annotations.*;

@ZenRegister
@ZenClass("mods.ftbquests.IQuestTaskCheck")
@FunctionalInterface
public interface IQuestTaskCheck
{
    /** Returns the absolute progress to keep; the task maximum is supplied separately. */
    @ZenMethod long check(IPlayer player, long progress, long maximum);
}
