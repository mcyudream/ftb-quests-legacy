package dev.ftbquests.acceptance;

import com.feed_the_beast.ftblib.events.player.ForgePlayerLoggedInEvent;
import com.feed_the_beast.ftblib.lib.config.*;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfig;
import com.feed_the_beast.ftblib.lib.util.NBTUtils;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.tree.*;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.quest.loot.RewardTable;
import com.feed_the_beast.ftbquests.quest.task.*;
import com.feed_the_beast.ftbquests.quest.reward.*;
import com.feed_the_beast.ftbquests.util.EditHistory;
import com.feed_the_beast.ftbquests.util.ServerQuestData;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Inspect actual schema IDs and menu labels on the production loader, separately from product jars. */
@SideOnly(Side.CLIENT)
public final class AcceptanceLocalization
{
    private static final int CHAPTER=0x68000001,QUEST=0x68000002,SECOND=0x68000003,TASK=0x68000004,REWARD=0x68000005;
    private final Minecraft mc=Minecraft.getMinecraft();
    private final Map<String,Set<String>> missing=new TreeMap<>();
    private final Map<String,String> labels=new TreeMap<>();
    private final Map<String,Object> checks=new LinkedHashMap<>();
    private final Map<String,List<String>> menus=new LinkedHashMap<>();
    private int ticks,step,wait,fields,taskTypes,rewardTypes;
    private volatile int serverStep;private volatile Throwable failure;
    private GuiQuestTree tree;
    public static void install(){MinecraftForge.EVENT_BUS.register(new AcceptanceLocalization());}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END||step==100)return;
        ticks++;
        try
        {
            if(failure!=null)throw new AssertionError("Server",failure);
            if(ticks>1800)throw new AssertionError("Localization timeout at "+step);
            if(step==0&&mc.currentScreen instanceof GuiMainMenu)
            {language("zh_cn");step=1;mc.launchIntegratedServer("localization-acceptance","Localization",new WorldSettings(8,GameType.CREATIVE,false,false,WorldType.FLAT));}
            else if(step==1&&mc.player!=null&&ClientQuestFile.exists())
            {
                step=2;server(()->{
                    AcceptanceMod.roundTrip();var file=ServerQuestFile.INSTANCE;
                    Chapter chapter=new Chapter(file);chapter.id=CHAPTER;chapter.title="本地化验收";chapter.onCreated();
                    Quest q=new Quest(chapter);q.id=QUEST;q.title="菜单翻译检查";q.description.add("原任务正文保持不变");q.onCreated();
                    Quest second=new Quest(chapter);second.id=SECOND;second.title="批量编辑检查";second.x=4;second.onCreated();
                    CheckmarkTask task=new CheckmarkTask(q);task.id=TASK;task.onCreated();
                    ItemReward reward=new ItemReward(q);reward.id=REWARD;reward.item=new ItemStack(Items.DIAMOND);reward.onCreated();
                    var nbt=EditHistory.snapshot(q);nbt.setLong("localization_unknown",9_000_000_001L);q.readDataPreservingUnknown(nbt);
                    file.refreshIDMap();file.clearCachedData();var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    NBTUtils.getPersistedData(player,true).setBoolean("ftbquests_editing_mode",true);task.changeProgress(file.getData(player),ChangeProgress.COMPLETE);
                    ServerQuestData.onPlayerLoggedIn(new ForgePlayerLoggedInEvent(Universe.get().getPlayer(player)));serverStep=1;
                });
            }
            else if(step==2&&serverStep==1&&ClientQuestFile.INSTANCE.getQuest(QUEST)!=null&&ClientQuestFile.INSTANCE.canEdit())
            {
                tree=ClientQuestFile.INSTANCE.questTreeGui;tree.open(quest(),false);
                auditSchemas();VisualSettings.open(tree);auditConfigView("visual-zh");
                AcceptanceMod.require("开启".equals(new ConfigBoolean(true).getStringForGUI().getUnformattedText()),"Boolean GUI is untranslated");
                AcceptanceMod.require(new ConfigBoolean(true).getString().equals("true"),"Boolean storage changed");
                wait=ticks;step=3;
            }
            else if(step==3&&ticks-wait>10)
            {shot("visual-settings-zh.png");current().closeGui(false);tree.open(quest(),false);questButton().onClicked(MouseButton.RIGHT);auditMenu("quest-right-click-zh");wait=ticks;step=4;}
            else if(step==4&&ticks-wait>8)
            {shot("quest-right-click-zh.png");tree.closeContextMenu();tree.selectedObjects.clear();tree.selectedObjects.add(quest());tree.selectedObjects.add(ClientQuestFile.INSTANCE.getQuest(SECOND));questButton().onClicked(MouseButton.RIGHT);auditMenu("multi-quest-right-click-zh");wait=ticks;step=5;}
            else if(step==5&&ticks-wait>8)
            {shot("multi-quest-right-click-zh.png");tree.closeContextMenu();BulkEditor.open(tree);auditConfigView("bulk-zh");wait=ticks;step=6;}
            else if(step==6&&ticks-wait>8)
            {shot("bulk-edit-zh.png");current().closeGui(false);tree.open(quest(),false);tree.selectedObjects.clear();new ButtonExpandedChapter(tree.questPanel,quest().chapter).onClicked(MouseButton.RIGHT);auditMenu("chapter-right-click-zh");wait=ticks;step=7;}
            else if(step==7&&ticks-wait>8)
            {shot("chapter-right-click-zh.png");tree.closeContextMenu();tree.open(quest(),false);((ButtonTask)tree.viewQuestPanel.panelTasks.widgets.stream().filter(w->w instanceof ButtonTask).findFirst().orElseThrow()).onClicked(MouseButton.RIGHT);auditMenu("task-right-click-zh");wait=ticks;step=8;}
            else if(step==8&&ticks-wait>8)
            {shot("task-right-click-zh.png");tree.closeContextMenu();((ButtonReward)tree.viewQuestPanel.panelRewards.widgets.stream().filter(w->w instanceof ButtonReward).findFirst().orElseThrow()).onClicked(MouseButton.RIGHT);auditMenu("reward-right-click-zh");wait=ticks;step=9;}
            else if(step==9&&ticks-wait>8)
            {shot("reward-right-click-zh.png");tree.closeContextMenu();((ButtonAddTask)tree.viewQuestPanel.panelTasks.widgets.stream().filter(w->w instanceof ButtonAddTask).findFirst().orElseThrow()).onClicked(MouseButton.LEFT);auditMenu("add-task-zh");tree.closeContextMenu();((ButtonAddReward)tree.viewQuestPanel.panelRewards.widgets.stream().filter(w->w instanceof ButtonAddReward).findFirst().orElseThrow()).onClicked(MouseButton.LEFT);auditMenu("add-reward-zh");tree.closeContextMenu();tree.closeQuest();tree.updateGui(tree.width/2+80,tree.height/2+60,0);tree.questPanel.mousePressed(MouseButton.RIGHT);auditMenu("canvas-right-click-zh");wait=ticks;step=10;}
            else if(step==10&&ticks-wait>8)
            {
                shot("canvas-right-click-zh.png");tree.closeContextMenu();checks.put("chinese_visual_and_context_menus",true);
                writeAudit();AcceptanceMod.require(missing.isEmpty(),"Missing runtime translations: "+missing.keySet());
                language("en_us");auditSchemas();tree.open(quest(),false);VisualSettings.open(tree);auditConfigView("visual-en");wait=ticks;step=11;
            }
            else if(step==11&&ticks-wait>8)
            {
                shot("visual-settings-en.png");AcceptanceMod.require(new ConfigBoolean(true).getStringForGUI().getUnformattedText().equals("Enabled"),"English boolean label missing");
                current().closeGui(false);language("zh_cn");checks.put("english_fallback_and_language_switch",true);writeAudit();
                AcceptanceMod.require(missing.isEmpty(),"Missing English runtime translations: "+missing.keySet());
                step=12;server(()->{
                    var file=ServerQuestFile.INSTANCE;var q=file.getQuest(QUEST);var player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                    AcceptanceMod.require(EditHistory.snapshot(q).getLong("localization_unknown")==9_000_000_001L,"Unknown data lost");
                    AcceptanceMod.require(q.description.equals(List.of("原任务正文保持不变"))&&file.getTask(TASK).isComplete(file.getData(player)),"UI localization changed description/progress");
                    AcceptanceMod.roundTrip();serverStep=2;
                });
            }
            else if(step==12&&serverStep==2)
            {checks.put("definitions_unknown_fields_and_progress_preserved",true);writeAudit();AcceptanceMod.report("PASS","Chinese/English actual config schema and context-menu translations, all registered core task/reward types, localized boolean/default labels, legacy definition round trip",null);step=100;mc.shutdown();}
        }
        catch(Throwable error){try{writeAudit();}catch(Exception ignored){}AcceptanceMod.report("FAIL","Localization stage "+step,error);step=100;mc.shutdown();}
    }
    private Quest quest(){return ClientQuestFile.INSTANCE.getQuest(QUEST);}
    private ButtonQuest questButton(){return (ButtonQuest)tree.questPanel.widgets.stream().filter(w->w instanceof ButtonQuest q&&q.quest.id==QUEST).findFirst().orElseThrow();}
    private void auditSchemas()
    {
        var file=ClientQuestFile.INSTANCE;auditObject(file);auditObject(quest().chapter);auditObject(quest());auditObject(new RewardTable(file));
        ConfigGroup image=ConfigGroup.newGroup("ftbquests").getGroup("chapter").getGroup("image");new ChapterImage(quest().chapter).getConfig(image);auditGroup(image);
        for(var type:TaskType.getRegistry()){auditObject(type.provider.create(quest()));auditRaw(type.getDisplayName(),"task type "+type.getRegistryName());taskTypes++;}
        for(var type:RewardType.getRegistry()){auditObject(type.provider.create(quest()));auditRaw(type.getDisplayName(),"reward type "+type.getRegistryName());rewardTypes++;}
        checks.put("all_loaded_task_reward_and_authoring_schemas_checked",true);
    }
    private void auditObject(QuestObjectBase object)
    {ConfigGroup root=ConfigGroup.newGroup("ftbquests");object.getConfig(object.createSubGroup(root));auditGroup(root);}
    private void auditGroup(ConfigGroup group)
    {
        for(var inst:group.getValues())if(!inst.getHidden()) {
            fields++;auditText(inst.getDisplayName(),inst.getPath());
            var value=inst.getValue();auditText(value.getStringForGUI(),inst.getPath()+" value");
            if(value instanceof ConfigEnum<?> config)auditEnum(config,inst.getPath());
            List<String> info=new ArrayList<>();value.addInfo(inst,info);for(String line:info)auditRaw(line,inst.getPath()+" tooltip");
        }
        for(var child:group.getGroups())auditGroup(child);
    }
    private <T> void auditEnum(ConfigEnum<T> value,String scope)
    {for(T variant:value.getNameMap())auditText(value.getNameMap().getDisplayName(null,variant),scope+" enum");}
    private void auditText(ITextComponent text,String scope)
    {
        if(text instanceof TextComponentTranslation translation) {
            String key=translation.getKey();
            if(!I18n.hasKey(key))missing.computeIfAbsent(key,k->new TreeSet<>()).add(scope);
            else labels.put(mc.gameSettings.language+":"+key,text.getUnformattedText());
        }
        auditRaw(text.getUnformattedText(),scope);
    }
    private void auditRaw(String text,String scope)
    {
        var matcher=Pattern.compile("(?:ftbquests|ftblib|ftbutilities)\\.[a-zA-Z0-9_.]+").matcher(TextFormatting.getTextWithoutFormattingCodes(text));
        while(matcher.find())missing.computeIfAbsent(matcher.group(),k->new TreeSet<>()).add(scope);
    }
    private void auditConfigView(String scope)throws Exception
    {
        GuiEditConfig gui=(GuiEditConfig)current();Field group=GuiEditConfig.class.getDeclaredField("group");group.setAccessible(true);auditGroup((ConfigGroup)group.get(gui));
        for(Widget widget:all(gui)) {
            auditRaw(widget.getTitle(),scope);
            if(widget.getClass().getSimpleName().equals("ButtonConfigEntry")){Field text=widget.getClass().getDeclaredField("keyText");text.setAccessible(true);auditRaw((String)text.get(widget),scope);}
        }
        checks.put(scope,true);
    }
    private void auditMenu(String scope)
    {
        AcceptanceMod.require(tree.contextMenu instanceof ContextMenu,"Menu did not open: "+scope);
        ContextMenu menu=(ContextMenu)tree.contextMenu;List<String> result=new ArrayList<>();
        for(var item:menu.items){result.add(item.title);auditRaw(item.title,scope);}
        menus.put(scope,result);
    }
    private void language(String language)
    {mc.getLanguageManager().setCurrentLanguage(mc.getLanguageManager().getLanguages().stream().filter(l->l.getLanguageCode().equals(language)).findFirst().orElseThrow());mc.gameSettings.language=language;mc.refreshResources();}
    private void writeAudit()throws Exception
    {Map<String,Object> report=new LinkedHashMap<>();report.put("missing_keys",missing);report.put("labels",labels);report.put("context_menus",menus);report.put("fields_checked",fields);report.put("task_types_checked",taskTypes);report.put("reward_types_checked",rewardTypes);report.put("checks",checks);Files.writeString(Paths.get("localization-runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private static List<Widget> all(Panel panel){List<Widget> result=new ArrayList<>();for(Widget widget:panel.widgets){result.add(widget);if(widget instanceof Panel child)result.addAll(all(child));}return result;}
    private static GuiBase current(){return ((GuiWrapper)Minecraft.getMinecraft().currentScreen).getGui();}
    private void server(Checked action){mc.getIntegratedServer().addScheduledTask(()->{try{action.run();}catch(Throwable error){failure=error;}});}
    private interface Checked{void run()throws Exception;}
    private void shot(String name){ScreenShotHelper.saveScreenshot(mc.gameDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
