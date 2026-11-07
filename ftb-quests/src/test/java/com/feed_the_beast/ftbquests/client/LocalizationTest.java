package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.config.ConfigBoolean;
import com.feed_the_beast.ftblib.lib.util.StringUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest
{
    @Test void everyEnglishQuestKeyHasANonemptyChineseEntry()throws Exception
    {
        Properties en=language("ftbquests","en_us"),zh=language("ftbquests","zh_cn");
        for(String key:en.stringPropertyNames())assertTrue(zh.containsKey(key)&&!zh.getProperty(key).isBlank(),key);
    }
    @Test void visualSettingLabelsUseTheActualNormalizedConfigIds()throws Exception
    {
        for(String language:List.of("en_us","zh_cn")) {
            Properties labels=language("ftbquests",language);
            for(var field:VisualConfig.class.getDeclaredFields()) {
                String key="ftbquests.visual."+StringUtils.getID(field.getName(),StringUtils.FLAG_ID_DEFAULTS);
                assertTrue(labels.containsKey(key),key+" in "+language);
            }
        }
    }
    @Test void booleanGuiTranslationDoesNotChangeSerializedOrCommandValues()throws Exception
    {
        Properties en=language("ftblib","en_us"),zh=language("ftblib","zh_cn");
        for(boolean value:List.of(false,true)) {
            ConfigBoolean config=new ConfigBoolean(value);assertEquals(Boolean.toString(value),config.getString());
            String key=((TextComponentTranslation)config.getStringForGUI()).getKey();assertTrue(en.containsKey(key)&&zh.containsKey(key));
            NBTTagCompound tag=new NBTTagCompound();config.writeToNBT(tag,"flag");ConfigBoolean restored=new ConfigBoolean(!value);restored.readFromNBT(tag,"flag");assertEquals(value,restored.getBoolean());
        }
    }
    private Properties language(String namespace,String language)throws Exception
    {
        Properties result=new Properties();
        try(var input=new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/"+namespace+"/lang/"+language+".lang")),StandardCharsets.UTF_8)){result.load(input);}
        return result;
    }
}
