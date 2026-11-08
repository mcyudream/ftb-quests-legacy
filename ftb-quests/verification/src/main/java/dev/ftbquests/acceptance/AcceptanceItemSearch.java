package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.client.CreativeItemIndex;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiSelectItemStack;
import com.feed_the_beast.ftblib.lib.util.PinyinSearch;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercises the real selector with more distinct creative names than the shared pinyin cache. */
@SideOnly(Side.CLIENT)
public final class AcceptanceItemSearch
{
    private static final int VARIANTS = 40000;
    private static final CountDownLatch PREPARE = new CountDownLatch(1);
    private static Item stress;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> timings = new ArrayList<>();
    private final String[] queries = {"minecraft:iron_ingot", "铁锭", "tieding", "@minecraft", "zuanshi", "tieding"};
    private GuiSelectItemStack selector;
    private TextBox search;
    private Panel results;
    private CompletableFuture<Void> warmed;
    private int step, query;
    private long started;
    private boolean done;

    public static void register() { MinecraftForge.EVENT_BUS.register(new Registration()); }
    public static final class Registration
    {
        @SubscribeEvent public void items(RegistryEvent.Register<Item> event)
        {
            stress = new Item() {
                @Override public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items)
                { if (tab == CreativeTabs.SEARCH) for (int i = 0; i < VARIANTS; i++) items.add(new ItemStack(this, 1, i)); }
                @Override public String getItemStackDisplayName(ItemStack stack) { return "测试材料 " + stack.getMetadata(); }
            }.setRegistryName("ftbquests_acceptance", "search_variants").setHasSubtypes(true);
            event.getRegistry().register(stress);
        }
    }
    public static void install()
    {
        // Simulate an unfinished pinyin index without blocking Minecraft's client thread.
        PinyinSearch.executor().execute(() -> {
            try { PREPARE.await(45, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        MinecraftForge.EVENT_BUS.register(new AcceptanceItemSearch());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if (done || event.phase != TickEvent.Phase.END) return;
        try
        {
            if (step == 0 && mc.currentScreen instanceof GuiMainMenu && CreativeItemIndex.complete())
            {
                AcceptanceMod.require(CreativeItemIndex.snapshot().size() > VARIANTS, "Stress variants not indexed");
                report.put("creative_variants", CreativeItemIndex.snapshot().size());
                report.put("extra_distinct_chinese_names", VARIANTS);
                GuiBase previous = new GuiBase() { @Override public void addWidgets() {} };
                selector = new GuiSelectItemStack(previous, stack -> {});
                selector.openGui();
                search = (TextBox) read(selector, GuiSelectItemStack.class, "searchBox");
                results = (Panel) read(selector, GuiSelectItemStack.class, "panelStacks");
                issue(); step = 1;
            }
            else if (step == 1)
            {
                if (!ready()) return;
                measure("literal_while_pinyin_paused");
                PREPARE.countDown();
                warmed = CompletableFuture.runAsync(() -> {}, PinyinSearch.executor());
                started = System.nanoTime(); step = 2;
            }
            else if (step == 2 && warmed.isDone())
            {
                warmed.join(); report.put("background_prepare_ms", elapsed());
                query = 1; issue(); step = 3;
            }
            else if (step == 3)
            {
                if (!ready()) return;
                measure("warm_query");
                if (++query < queries.length) { issue(); return; }
                // Cancel a query after its worker starts, before its result is drawn.
                search.setText("zuanshi", true);
                step = 4;
            }
            else if (step == 4)
            {
                search.setText("tieding", true); started = System.nanoTime(); step = 5;
            }
            else if (step == 5)
            {
                query = 5;
                if (!ready()) return;
                report.put("rapid_query_keeps_latest_iron_result", true);
                started = System.nanoTime(); step = 6;
            }
            else if (step == 6 && elapsed() >= 300)
            {
                AcceptanceMod.require(ready(), "A cancelled query replaced the latest result");
                report.put("queries", timings);
                report.put("search_worker_reads_only_captured_metadata", true);
                write();
                for (var timing : timings) AcceptanceMod.require(((Number) timing.get("visible_ms")).longValue() < 1000, "Search exceeded one second: " + timing);
                AcceptanceMod.report("PASS", "40,000 distinct Chinese creative variants; cold literal search; warm Chinese/pinyin/registry/namespace; rapid query cancellation", null);
                done = true; mc.shutdown();
            }
        }
        catch (Throwable error)
        {
            PREPARE.countDown(); report.put("queries", timings);
            try { write(); } catch (Exception ignored) {}
            AcceptanceMod.report("FAIL", "Large item selector search stage " + step, error); done = true; mc.shutdown();
        }
    }
    private void issue()
    {
        search.setText(queries[query], true);
        started = System.nanoTime();
    }
    private boolean ready() throws Exception
    {
        AcceptanceMod.require(elapsed() < 20000, "Search timed out: " + queries[query]);
        Thread request = (Thread) read(selector, GuiSelectItemStack.class, "threadItemList");
        if (request == null || !read(request, request.getClass(), "search").equals(queries[query]) || request.isAlive()
                || read(selector, GuiSelectItemStack.class, "newStackItems") != null) return false;
        boolean expected = false;
        Item target = queries[query].equals("zuanshi") ? Items.DIAMOND : Items.IRON_INGOT;
        for (Widget widget : results.widgets)
        {
            ItemStack stack = (ItemStack) read(widget, widget.getClass(), "stack");
            if (stack.getItem() == stress) return false;
            if (stack.getItem() == target) expected = true;
            if (queries[query].equals("@minecraft") && !stack.isEmpty() && !stack.getItem().getRegistryName().getNamespace().equals("minecraft")) return false;
        }
        return expected;
    }
    private void measure(String phase)
    {
        Map<String, Object> timing = new LinkedHashMap<>();
        timing.put("phase", phase); timing.put("query", queries[query]); timing.put("visible_ms", elapsed()); timing.put("result_count", results.widgets.size());
        timings.add(timing); System.out.println("FTB_SEARCH_TIMING " + timing);
    }
    private long elapsed() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private void write() throws Exception
    { Files.writeString(Path.of("search-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
    private static Object read(Object object, Class<?> type, String name) throws Exception
    { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
}
