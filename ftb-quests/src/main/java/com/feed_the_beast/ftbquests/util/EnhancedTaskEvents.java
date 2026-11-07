package com.feed_the_beast.ftbquests.util;

import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.task.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = FTBQuests.MOD_ID)
public final class EnhancedTaskEvents
{
    @SubscribeEvent public static void register(RegistryEvent.Register<TaskType> event)
    {
        event.getRegistry().registerAll(
            FTBQuestsTasks.STRUCTURE = new TaskType(StructureTask::new).setRegistryName("structure"),
            FTBQuestsTasks.PLACE_BLOCK = new TaskType(q -> new BlockActionTask(q, BlockActionTask.Action.PLACE)).setRegistryName("place_block"),
            FTBQuestsTasks.BREAK_BLOCK = new TaskType(q -> new BlockActionTask(q, BlockActionTask.Action.BREAK)).setRegistryName("break_block"),
            FTBQuestsTasks.USE_BLOCK = new TaskType(q -> new BlockActionTask(q, BlockActionTask.Action.USE)).setRegistryName("use_block"),
            FTBQuestsTasks.REACH_DAY = new TaskType(q -> new WorldConditionTask(q, WorldConditionTask.Kind.REACH_DAY)).setRegistryName("reach_day"),
            FTBQuestsTasks.TIMER = new TaskType(q -> new WorldConditionTask(q, WorldConditionTask.Kind.TIMER)).setRegistryName("timer"),
            FTBQuestsTasks.PLAYER_NBT = new TaskType(q -> new WorldConditionTask(q, WorldConditionTask.Kind.PLAYER_NBT)).setRegistryName("player_nbt"),
            FTBQuestsTasks.ADVANCED_KILL = new TaskType(AdvancedKillTask::new).setRegistryName("advanced_kill")
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void placed(BlockEvent.PlaceEvent event)
    { count(event.getPlayer(), BlockActionTask.Action.PLACE, event.getPlacedBlock(), event.getBlockSnapshot().getReplacedBlock(), event.getPlayer().getHeldItem(event.getHand())); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event)
    { count(event.getPlayer(), BlockActionTask.Action.BREAK, event.getState(), event.getState(), event.getPlayer().getHeldItemMainhand()); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void used(PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getHand() != net.minecraft.util.EnumHand.MAIN_HAND || event.getUseBlock() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY) return;
        IBlockState state = event.getWorld().getBlockState(event.getPos());
        count(event.getEntityPlayer(), BlockActionTask.Action.USE, state, state, event.getItemStack());
    }

    private static void count(EntityPlayer player, BlockActionTask.Action action, IBlockState state, IBlockState replaced, ItemStack held)
    {
        if (!(player instanceof EntityPlayerMP) || ServerQuestFile.INSTANCE == null) return;
        QuestData data = ServerQuestFile.INSTANCE.getData(player);
        if (data == null) return;
        for (BlockActionTask task : ServerQuestFile.INSTANCE.collect(BlockActionTask.class))
            if (task.action == action && task.canStart(data) && task.matches(state, replaced, held)) data.getTaskData(task).addProgress(1);
    }
}
