package com.feed_the_beast.ftbquests.quest.reward;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.data.FTBLibAPI;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Chapter;
import com.feed_the_beast.ftbquests.quest.Quest;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * @author LatvianModder
 */
public class CommandReward extends Reward
{
	public String command;
	public boolean playerCommand;
	public int permissionLevel = 4;
	public boolean silent = false;
	public String feedbackMessage = "";

	public CommandReward(Quest quest)
	{
		super(quest);
		command = "/say Hi, @team!";
	}

	@Override
	public RewardType getType()
	{
		return FTBQuestsRewards.COMMAND;
	}

	@Override
	public void writeData(NBTTagCompound nbt)
	{
		super.writeData(nbt);
		nbt.setString("command", command);
		if (permissionLevel != 4) nbt.setInteger("permission_level", permissionLevel);
		if (silent) nbt.setBoolean("silent", true);
		if (!feedbackMessage.isEmpty()) nbt.setString("feedback_message", feedbackMessage);

		if (playerCommand)
		{
			nbt.setBoolean("player_command", true);
		}
	}

	@Override
	public void readData(NBTTagCompound nbt)
	{
		super.readData(nbt);
		command = nbt.getString("command");
		playerCommand = nbt.getBoolean("player_command");
		permissionLevel = nbt.hasKey("permission_level") ? Math.max(0, Math.min(4, nbt.getInteger("permission_level"))) : 4;
		silent = nbt.getBoolean("silent");
		feedbackMessage = nbt.getString("feedback_message");
	}

	@Override
	public void writeNetData(DataOut data)
	{
		super.writeNetData(data);
		data.writeString(command);
		data.writeBoolean(playerCommand);
		data.writeByte(permissionLevel);
		data.writeBoolean(silent);
		data.writeString(feedbackMessage);
	}

	@Override
	public void readNetData(DataIn data)
	{
		super.readNetData(data);
		command = data.readString();
		playerCommand = data.readBoolean();
		permissionLevel = Math.max(0, Math.min(4, data.readByte()));
		silent = data.readBoolean();
		feedbackMessage = data.readString();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getConfig(ConfigGroup config)
	{
		super.getConfig(config);
		config.addString("command", () -> command, v -> command = v, "/say Hi, @team!").setDisplayName(new TextComponentTranslation("ftbquests.reward.ftbquests.command"));
		config.addBool("player", () -> playerCommand, v -> playerCommand = v, false);
		config.addInt("permission_level", () -> permissionLevel, v -> permissionLevel = v, 4, 0, 4);
		config.addBool("silent", () -> silent, v -> silent = v, false);
		config.addString("feedback_message", () -> feedbackMessage, v -> feedbackMessage = v, "");
	}

	@Override
	public void claim(EntityPlayerMP player, boolean notify)
	{
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("p", player.getName());

		BlockPos pos = player.getPosition();
		overrides.put("x", pos.getX());
		overrides.put("y", pos.getY());
		overrides.put("z", pos.getZ());

		Chapter chapter = getQuestChapter();

		if (chapter != null)
		{
			overrides.put("chapter", chapter);
		}

		overrides.put("quest", quest);
		com.feed_the_beast.ftblib.lib.data.ForgeTeam team = com.feed_the_beast.ftblib.lib.data.Universe.get().getPlayer(player).team;
		overrides.put("team", FTBLibAPI.getTeam(player.getUniqueID()));
		overrides.put("team_id", team.getId());
		overrides.put("long_team_id", team.getUID());
		overrides.put("member_count", team.getMembers().size());
		overrides.put("online_member_count", team.getOnlineMembers().size());

		String s = command.startsWith("/") ? command.substring(1) : command;

		s = com.feed_the_beast.ftbquests.util.CommandTemplates.expand(s, overrides);
		net.minecraft.command.ICommandSender source = new net.minecraft.command.ICommandSender()
		{
			@Override public String getName() { return player.getName(); }
			@Override public net.minecraft.util.text.ITextComponent getDisplayName() { return player.getDisplayName(); }
			@Override public void sendMessage(net.minecraft.util.text.ITextComponent message) { if (!silent) player.sendMessage(message); }
			@Override public boolean canUseCommand(int level, String name) { return playerCommand ? player.canUseCommand(level, name) : level <= permissionLevel; }
			@Override public BlockPos getPosition() { return player.getPosition(); }
			@Override public net.minecraft.util.math.Vec3d getPositionVector() { return player.getPositionVector(); }
			@Override public net.minecraft.world.World getEntityWorld() { return player.world; }
			@Override public net.minecraft.entity.Entity getCommandSenderEntity() { return player; }
			@Override public boolean sendCommandFeedback() { return !silent; }
			@Override public void setCommandStat(net.minecraft.command.CommandResultStats.Type type, int value) { player.setCommandStat(type, value); }
			@Override public net.minecraft.server.MinecraftServer getServer() { return player.server; }
		};
		player.server.getCommandManager().executeCommand(source, s);
		if (notify && !feedbackMessage.isEmpty()) player.sendMessage(new net.minecraft.util.text.TextComponentString(com.feed_the_beast.ftbquests.util.CommandTemplates.expand(feedbackMessage, overrides)));
	}

	@Override
	public String getAltTitle()
	{
		return I18n.format("ftbquests.reward.ftbquests.command") + ": " + TextFormatting.RED + command;
	}
}
