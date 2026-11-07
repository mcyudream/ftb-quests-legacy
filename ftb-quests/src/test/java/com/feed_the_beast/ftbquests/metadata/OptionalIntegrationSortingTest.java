package com.feed_the_beast.ftbquests.metadata;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.toposort.*;
import net.minecraftforge.fml.common.versioning.*;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** The reported indirect cycle must be checked with Forge's actual sorter, not a pairwise check. */
class OptionalIntegrationSortingTest
{
    private static final String PONDER="required-after:cleanroom@[0.4.0,);required-after:forge@[14.23.5.2847,);required-after:mixinbooter@[9.1,);after:crafttweaker@[4.1.20,);after:modularmachinery;after:media_api@[1.0.2,2.0.0)";
    private static final String ZENUTILS="after:crafttweaker;after:mixinbooter@[10.6,);after:configanytime;after:ftbquests;before:contenttweaker";
    private static final String MACHINERY="required-after:forge@[14.21.0.2371,);required-after:crafttweaker@[4.0.4,);required-after:zenutils@[1.12.8,);after:jei@[4.13.1.222,);after:gregtech@[2.7.4-beta,);after:appliedenergistics2@[rv6-stable-7,);after:fluxnetworks@[4.1.0,);after:tconstruct@[1.12.2-2.12.0.157,);after:thermalexpansion@[5.5.0,)";

    @BeforeAll static void forgeApiDiscovery(){ModAPIManager.INSTANCE.registerDataTableAndParseAPI(new ASMDataTable());}

    private static String questsRules()throws Exception
    {
        AtomicReference<String> result=new AtomicReference<>();
        try(InputStream input=OptionalIntegrationSortingTest.class.getResourceAsStream("/com/feed_the_beast/ftbquests/FTBQuests.class")){
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){
                    return descriptor.equals("Lnet/minecraftforge/fml/common/Mod;")?new AnnotationVisitor(Opcodes.ASM9){
                        @Override public void visit(String key,Object value){if(key.equals("dependencies"))result.set((String)value);}
                    }:null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        return Objects.requireNonNull(result.get());
    }
    @Test void oldQuestsOrderingReproducesTheIndirectUserCycle()throws Exception
    {
        assertThrows(ModSortingException.class,()->sort(questsRules()+";after:ponder-legacy",true,true,0));
    }
    @Test void actualQuestsRulesSortWithPonderZenUtilsAndModularMachineryRegardlessOfInputOrder()throws Exception
    {
        for(int seed=0;seed<16;seed++){
            List<String> order=sort(questsRules(),true,true,seed);
            assertBefore(order,"ftbquests","zenutils");assertBefore(order,"zenutils","modularmachinery");
            assertBefore(order,"modularmachinery","ponder-legacy");assertBefore(order,"modularmachinery","modulargrid");
        }
    }
    @Test void missingPonderAndMediaRemainOptional()throws Exception
    {
        List<String> order=sort(questsRules(),false,false,1);
        assertTrue(order.contains("ftbquests"));assertFalse(order.contains("ponder-legacy"));assertFalse(order.contains("media_api"));
    }
    @Test void genuineQuestsPrerequisitesRetainTheirOrdering()throws Exception
    {
        List<String> order=sort(questsRules(),true,true,5);
        for(String id:List.of("ftblib","itemfilters","crafttweaker","ftbutilities","media_api"))assertBefore(order,id,"ftbquests");
    }
    @Test void aPonderAddonMayDependOnQuestsWithoutCreatingAReverseEdge()throws Exception
    {
        List<ModContainer> mods=mods(questsRules(),true,true);
        mods.add(container("quest_tutorials","required-after:ftbquests;required-after:ponder-legacy"));
        List<String> order=sort(mods,10);assertBefore(order,"ftbquests","quest_tutorials");assertBefore(order,"ponder-legacy","quest_tutorials");
    }
    private static void assertBefore(List<String> order,String first,String second)
    {assertTrue(order.indexOf(first)>=0&&order.indexOf(second)>order.indexOf(first),()->first+" must precede "+second+": "+order);}
    private static List<ModContainer> mods(String quests,boolean ponder,boolean media)
    {
        List<ModContainer> mods=new ArrayList<>();
        for(String id:List.of("cleanroom","forge","mixinbooter","configanytime","crafttweaker","ftblib","itemfilters","ftbutilities","jei"))mods.add(container(id,""));
        if(media)mods.add(container("media_api","required-after:cleanroom@[0.4.0,)"));
        mods.add(container("ftbquests",quests));mods.add(container("zenutils",ZENUTILS));mods.add(container("contenttweaker","required-after:crafttweaker"));
        mods.add(container("modularmachinery",MACHINERY));mods.add(container("modulargrid","required-after:modularmachinery@[2.1.0,)"));
        if(ponder)mods.add(container("ponder-legacy",PONDER));
        return mods;
    }
    private static List<String> sort(String quests,boolean ponder,boolean media,int seed){return sort(mods(quests,ponder,media),seed);}
    private static List<String> sort(List<ModContainer> mods,int seed)
    {
        Collections.shuffle(mods,new Random(seed));Map<String,ModContainer> byId=new LinkedHashMap<>();mods.forEach(mod->byId.put(mod.getModId(),mod));
        try{
            var field=Loader.class.getDeclaredField("namedMods");field.setAccessible(true);Object previous=field.get(Loader.instance());field.set(Loader.instance(),byId);
            var controller=Loader.class.getDeclaredField("modController");controller.setAccessible(true);Object previousController=controller.get(Loader.instance());
            controller.set(Loader.instance(),new LoadController(Loader.instance()));
            try{return new ModSorter(mods,byId).sort().stream().map(ModContainer::getModId).toList();}
            finally{field.set(Loader.instance(),previous);controller.set(Loader.instance(),previousController);}
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static ModContainer container(String id,String rules)
    {
        ModMetadata metadata=new ModMetadata();metadata.modId=id;metadata.name=id;metadata.version="1";
        List<ArtifactVersion> after=new ArrayList<>(),before=new ArrayList<>();
        for(String rule:rules.split(";"))if(!rule.isEmpty()){
            String[] parts=rule.split(":",2);(parts[0].endsWith("before")?before:after).add(VersionParser.parseVersionReference(parts[1]));
        }
        return new DummyModContainer(metadata){
            @Override public boolean isImmutable(){return false;}
            @Override public String getSortingRules(){return rules;}
            @Override public List<ArtifactVersion> getDependencies(){return after;}
            @Override public List<ArtifactVersion> getDependants(){return before;}
        };
    }
}
