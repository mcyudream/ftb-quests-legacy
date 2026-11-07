package com.feed_the_beast.ftbquests.events;

import com.feed_the_beast.ftbquests.quest.QuestObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Event;

/** A server-validated action explicitly declared in a visible quest or chapter. */
public final class ScriptTextClickEvent extends Event
{
    private final EntityPlayerMP player;
    private final QuestObject source;
    private final String action;

    public ScriptTextClickEvent(EntityPlayerMP player, QuestObject source, String action)
    { this.player = player; this.source = source; this.action = action; }
    public EntityPlayerMP getPlayer() { return player; }
    public QuestObject getSource() { return source; }
    public String getAction() { return action; }
}
