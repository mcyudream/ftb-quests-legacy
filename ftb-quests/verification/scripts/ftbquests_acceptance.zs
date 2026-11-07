import mods.ftbquests.Quests;
import crafttweaker.player.IPlayer;

Quests.customTask("tag:zs_task", function(player as IPlayer, progress as long, maximum as long) as long {
    return maximum;
}, 1, 7);
Quests.customReward("tag:zs_reward", function(player as IPlayer) {
    player.give(<minecraft:gold_ingot> * 2);
});
Quests.onCompleted("tag:zs_acceptance", function(player as IPlayer, id as string, teamUID as int) {
    player.give(<minecraft:emerald>);
});
Quests.onTextClick("acceptance", function(player as IPlayer, action as string, sourceID as string, teamUID as int) {
    player.give(<minecraft:redstone>);
});
events.onPlayerTick(function(event as crafttweaker.event.PlayerTickEvent) {
    val world = event.player.world;
    if (!Quests.exists(world, "6f000001")) return;
    if (Quests.findObjects(world, "tag:zs_acceptance").length != 0) return;
    val id = Quests.createQuest(world, "6f000001", 12.0, 6.0, {
        title: "ZS created", tags: ["zs_acceptance"],
        text: ["{\"type\":\"markdown\",\"text\":\"[ZenScript action](zs:acceptance)\"}"],
        tasks: [{type: "custom", tags: ["zs_task"]}],
        rewards: [{type: "custom", tags: ["zs_reward"]}]
    });
    Quests.editObject(world, id, {title: "ZS edited"});
    print("FTB Quests acceptance: actual ZenScript create/edit completed");
});
