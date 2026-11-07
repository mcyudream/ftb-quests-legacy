package com.feed_the_beast.ftbquests.integration.jei;

import com.feed_the_beast.ftbquests.quest.QuestObjectBase;
import net.minecraftforge.fml.common.Loader;

/**
 * @author LatvianModder
 */
public class FTBQuestsJEIHelper
{
	private static boolean lootcratesDirty;
	@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public static void showRecipes(net.minecraft.item.ItemStack stack, boolean uses)
    {
        if (!stack.isEmpty() && Loader.isModLoaded("jei")) openRecipes(stack,uses);
    }
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private static void openRecipes(net.minecraft.item.ItemStack stack, boolean uses)
    {
        var runtime=FTBQuestsJEIIntegration.runtime;
        if(runtime!=null) runtime.getRecipesGui().show(runtime.getRecipeRegistry().createFocus(uses?mezz.jei.api.recipe.IFocus.Mode.INPUT:mezz.jei.api.recipe.IFocus.Mode.OUTPUT,stack));
    }
    public static int QUESTS = 1;
	public static int LOOTCRATES = 2;

	public static void refresh(QuestObjectBase object)
	{
		int i = object.refreshJEI();

		if (i != 0 && Loader.isModLoaded("jei"))
		{
			if ((i & QUESTS) != 0)
			{
				QuestRegistry.INSTANCE.queue(object);
			}

			if ((i & LOOTCRATES) != 0)
			{
				lootcratesDirty = true;
			}
		}
	}

	/** JEI changes are coalesced on the client thread after input has been handled. */
	public static void flush()
	{
		if (!Loader.isModLoaded("jei")) return;
		if (FTBQuestsJEIIntegration.runtime == null) return;
		QuestRegistry.INSTANCE.flush();
		if (lootcratesDirty)
		{
			lootcratesDirty = false;
			LootCrateRegistry.INSTANCE.refresh();
		}
	}
}
