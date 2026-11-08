package com.feed_the_beast.ftblib.client;

import com.feed_the_beast.ftblib.FTBLib;
import com.feed_the_beast.ftblib.lib.util.PinyinSearch;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds the creative selector once, in bounded batches on the client thread. */
@Mod.EventBusSubscriber(modid = FTBLib.MOD_ID, value = Side.CLIENT)
public final class CreativeItemIndex
{
    private static Iterator<Item> remaining;
    private static final List<ItemStack> building = new ArrayList<>();
    private static final Map<ItemStack, String> names = new IdentityHashMap<>();
    private static final List<SearchEntry> buildingSearch = new ArrayList<>();
    private static volatile List<ItemStack> snapshot = Collections.emptyList();
    private static volatile Map<ItemStack, String> searchNames = Collections.emptyMap();
    private static volatile List<SearchEntry> searchEntries = Collections.emptyList();
    private static volatile int generation;
    private static volatile int epoch;
    private static final AtomicInteger searchGeneration = new AtomicInteger();
    private static int queued;
    private static boolean enabled;
    private static long published;

    public record SearchEntry(ItemStack stack, String mod, PinyinSearch.IndexedKey key) {}

    public static void begin() { enabled = true; invalidate(); }
    public static void invalidate()
    {
        epoch++;
        remaining = null;
        building.clear(); names.clear(); buildingSearch.clear(); queued = 0;
        snapshot = Collections.emptyList(); searchNames = Collections.emptyMap(); searchEntries = Collections.emptyList(); generation++;
    }
    public static int generation() { return generation; }
    public static List<ItemStack> snapshot() { return snapshot; }
    public static List<SearchEntry> searchEntries() { return searchEntries; }
    public static int searchGeneration() { return searchGeneration.get(); }
    public static boolean complete() { return enabled && remaining != null && !remaining.hasNext(); }
    public static String searchName(ItemStack stack)
    { String name = searchNames.get(stack); return name == null ? (stack.getDisplayName()+" "+stack.getItem().getRegistryName()).toLowerCase(Locale.ROOT) : name; }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)
    {
        if (!enabled || event.phase != TickEvent.Phase.END || complete()) return;
        if (remaining == null) remaining = Item.REGISTRY.iterator();
        long deadline = System.nanoTime() + 2_000_000;
        do
        {
            if (!remaining.hasNext()) break;
            Item item = remaining.next();
            NonNullList<ItemStack> stacks = NonNullList.create();
            try { item.getSubItems(CreativeTabs.SEARCH, stacks); }
            catch (RuntimeException ex) { FTBLib.LOGGER.warn("Could not index creative variants for " + item.getRegistryName(), ex); }
            for (ItemStack stack : stacks)
            {
                if (stack.isEmpty()) continue;
                add(stack);
            }
        }
        while (remaining.hasNext() && System.nanoTime() < deadline);
        if (!remaining.hasNext())
        {
            add(new ItemStack(Blocks.COMMAND_BLOCK));
            add(new ItemStack(Blocks.BARRIER));
            add(new ItemStack(Blocks.STRUCTURE_VOID));
        }
        long now = System.nanoTime();
        if (remaining.hasNext() && now - published < 250_000_000L) return;
        published = now;
        snapshot = Collections.unmodifiableList(new ArrayList<>(building));
        searchNames = Collections.unmodifiableMap(new IdentityHashMap<>(names));
        searchEntries = List.copyOf(buildingSearch);
        List<SearchEntry> pending = List.copyOf(buildingSearch.subList(queued, buildingSearch.size()));
        queued = buildingSearch.size();
        int expectedEpoch = epoch;
        PinyinSearch.executor().execute(() -> {
            long notified = System.nanoTime();
            for (SearchEntry entry : pending)
            {
                if (epoch != expectedEpoch) return;
                entry.key.prepare();
                long ready = System.nanoTime();
                if (ready - notified >= 100_000_000L) { searchGeneration.incrementAndGet(); notified = ready; }
            }
            if (epoch == expectedEpoch) searchGeneration.incrementAndGet();
        });
        generation++;
    }

    private static void add(ItemStack stack)
    {
        Item item = stack.getItem();
        String name;
        try { name = (stack.getDisplayName()+" "+item.getRegistryName()).toLowerCase(Locale.ROOT); }
        catch (RuntimeException ex) { name = String.valueOf(item.getRegistryName()); }
        building.add(stack); names.put(stack, name);
        buildingSearch.add(new SearchEntry(stack, item.getRegistryName().getNamespace(), new PinyinSearch.IndexedKey(name)));
    }
}
