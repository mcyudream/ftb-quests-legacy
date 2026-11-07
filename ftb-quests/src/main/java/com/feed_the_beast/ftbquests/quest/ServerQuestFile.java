package com.feed_the_beast.ftbquests.quest;

import com.feed_the_beast.ftblib.lib.data.FTBLibAPI;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.util.FileUtils;
import com.feed_the_beast.ftbquests.FTBQuests;
import com.feed_the_beast.ftbquests.net.edit.MessageDeleteObjectResponse;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import io.sommers.packmode.api.PackModeAPI;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author LatvianModder
 */
public class ServerQuestFile extends QuestFile
{
	public static ServerQuestFile INSTANCE;

	public final Universe universe;
	public boolean shouldSave = false;
	private boolean isLoading = false;
	private File folder;
	private long retrySaveAfter;
	private boolean saveRetryPending;
	private static final long SAVE_RETRY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);

	public ServerQuestFile(Universe u)
	{
		universe = u;
	}

	private String getFolderName()
	{
		if (universe.world.getGameRules().hasRule("questfile"))
		{
			return universe.world.getGameRules().getString("questfile");
		}

		if (Loader.isModLoaded("packmode"))
		{
			return getPackmodeFolderName();
		}

		return "normal";
	}

	private static String getPackmodeFolderName()
	{
		return PackModeAPI.getInstance().getCurrentPackMode();
	}

	public void load()
	{
		com.feed_the_beast.ftbquests.util.EditHistory.clear(this);
		folderName = getFolderName();
		folder = new File(Loader.instance().getConfigDir(), "ftbquests/" + folderName);
		try { com.feed_the_beast.ftbquests.util.TransactionalStorage.recoverDirectory(folder.toPath()); }
		catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }

		if (folder.exists())
		{
			FTBQuests.LOGGER.info("Loading quests from " + folder.getAbsolutePath());
			isLoading = true;
			readDataFull(folder);
			isLoading = false;
		}
	}

	@Override
	public boolean isClient()
	{
		return false;
	}

	@Override
	public boolean isLoading()
	{
		return isLoading;
	}

	@Override
	public File getFolder()
	{
		return folder;
	}

	@Override
	@Nullable
	public ServerQuestData getData(int team)
	{
		if (team == 0)
		{
			return null;
		}

		ForgeTeam t = universe.getTeam(team);
		return t.isValid() ? ServerQuestData.get(t) : null;
	}

	@Override
	@Nullable
	public ServerQuestData getData(String team)
	{
		if (team.isEmpty())
		{
			return null;
		}

		ForgeTeam t = universe.getTeam(team);
		return t.isValid() ? ServerQuestData.get(t) : null;
	}

	@Override
	@Nullable
	public ServerQuestData getData(UUID player)
	{
		return getData(FTBLibAPI.getTeamID(player));
	}

	@Override
	public Collection<ServerQuestData> getAllData()
	{
		Collection<ForgeTeam> teams = universe.getTeams();
		List<ServerQuestData> list = new ArrayList<>(teams.size());

		for (ForgeTeam team : teams)
		{
			if (team.isValid())
			{
				list.add(ServerQuestData.get(team));
			}
		}

		return list;
	}

	@Override
	public void deleteObject(int id)
	{
		QuestObjectBase object = getBase(id);

		if (object != null)
		{
			object.deleteChildren();
			object.deleteSelf();
			refreshIDMap();
			save();
		}

		new MessageDeleteObjectResponse(id).sendToAll();
	}

	public void save()
	{
		shouldSave = true;
		universe.markDirty();
	}

	public void saveNow()
	{
		shouldSave = true;
		try
		{
			writeDataFull(getFolder());
			shouldSave = false;
			saveRetryPending = false;
		}
		catch (RuntimeException failure)
		{
			if (isStorageFailure(failure))
			{
				saveRetryPending = true;
				retrySaveAfter = saveTimeNanos() + SAVE_RETRY_NANOS;
				FTBQuests.LOGGER.error("Quest save failed; edits remain pending and will retry in 5 seconds. Keep the transaction backups and any unsaved snapshot.", failure);
			}
			throw failure;
		}
	}

	protected long saveTimeNanos() { return System.nanoTime(); }

	private static boolean isStorageFailure(Throwable failure)
	{
		for (Throwable cause = failure; cause != null; cause = cause.getCause())
			if (cause instanceof java.io.IOException) return true;
		return false;
	}

	/** World autosaves must not crash the server or discard pending edits on an I/O failure. */
	public boolean trySaveNow()
	{
		if (saveRetryPending && saveTimeNanos() - retrySaveAfter < 0) return false;
		try { saveNow(); return true; }
		catch (RuntimeException failure)
		{
			if (!isStorageFailure(failure)) throw failure;
			return false;
		}
	}

	public void retrySaveIfDue()
	{
		if (shouldSave && saveRetryPending && saveTimeNanos() - retrySaveAfter >= 0) trySaveNow();
	}

	public void unload()
	{
		if (shouldSave)
		{
			saveNow();
			shouldSave = false;
		}

		deleteChildren();
		deleteSelf();
	}
}
