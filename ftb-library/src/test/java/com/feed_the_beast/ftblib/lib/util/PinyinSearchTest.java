package com.feed_the_beast.ftblib.lib.util;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PinyinSearchTest
{
    @Test void itemNamesMatchChineseFullPinyinInitialsAndRegistryId()
    {
        var key=PinyinSearch.key("§b铁锭 minecraft:iron_ingot");
        for(String query:List.of("铁锭","铁","tieding","tie ding","TD","iron_ingot","minecraft:iron"))assertTrue(key.matches(query),query);
        assertFalse(key.matches("zuanshi"));assertFalse(key.matches("tieding gold"));
        assertTrue(key.matches(""));
    }
    @Test void ponderNamesIncludeMixedLatinSceneAndTraditionalChinese()
    {
        var key=PinyinSearch.key("AE2S 能源核心 · 結構搭建 ae2s:core#pack:structure");
        for(String query:List.of("nengyuanhexin","nyhx","jiegoudajian","jgdj","AE2S nyhx","pack:structure","結構"))assertTrue(key.matches(query),query);
        assertFalse(key.matches("core something_else"));
    }
    @Test void tonesUmlautsApostrophesAndWidthVariantsAreNormalized()
    {
        var key=PinyinSearch.key("绿宝石 女巫");
        for(String query:List.of("lvbaoshi","lübǎoshí","lu:baoshi","lubaoshi","ＬＶＢＡＯＳＨＩ","nvwu","nv'wu","nüwū"))assertTrue(key.matches(query),query);
    }
    @Test void englishAndLiteralPunctuationKeepWorking()
    {
        var key=PinyinSearch.key("English + [Tag] mod:machine/v2");
        assertTrue(key.matches("english"));assertTrue(key.matches("[tag]"));assertTrue(key.matches("machine/v2"));
        assertFalse(key.matches(".*"));assertTrue(key.matches("English v2"));
    }
    @Test void immutableCachedKeysAreSafeForConcurrentSelectors()throws Exception
    {
        var tasks=new ArrayList<CompletableFuture<PinyinSearch.Key>>();
        for(int i=0;i<30;i++)tasks.add(CompletableFuture.supplyAsync(()->PinyinSearch.key("思索教程")));
        for(var task:tasks)assertTrue(task.get(10,TimeUnit.SECONDS).matches("ssjc"));
        PinyinSearch.clear();assertTrue(PinyinSearch.key("钻石").matches("zuanshi"));
    }

    @Test void literalAndRegistryQueriesWorkBeforePinyinPreparation()
    {
        var key = new PinyinSearch.IndexedKey("§b铁锭 minecraft:iron_ingot");
        assertTrue(key.matches(PinyinSearch.query("铁锭")));
        assertTrue(key.matches(PinyinSearch.query("minecraft:iron_ingot")));
        assertTrue(key.matches(PinyinSearch.query("铁 iron")));
        assertFalse(key.matches(PinyinSearch.query("钻石")));
        assertFalse(key.matches(PinyinSearch.query("tieding")));
    }

    @Test void catalogKeysKeepPinyinAfterTheSharedCacheIsEvicted()
    {
        var key = new PinyinSearch.IndexedKey("铁锭 minecraft:iron_ingot");
        key.prepare();
        PinyinSearch.clear();
        assertTrue(key.matches(PinyinSearch.query("tieding")));
        assertTrue(key.matches(PinyinSearch.query("td")));
        assertTrue(key.matches(PinyinSearch.query("铁 ding")));
        assertFalse(key.matches(PinyinSearch.query("zuanshi")));
    }
}
