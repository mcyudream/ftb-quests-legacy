package com.feed_the_beast.ftbquests.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalQuestVideoTest
{
    @TempDir Path config;
    private Path imported(Path directory)throws Exception{
        Path source=config.resolve("选择 视频.mp4");Files.write(source,new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m',0,0,0,0});
        return QuestMedia.prepare(source,true,directory,()->false);
    }
    @Test void importedVideoResolvesFromInstalledDirectoryWithoutGeneratingCache()throws Exception{
        Path file=imported(LocalQuestVideo.directory(config));
        assertEquals(file.toRealPath(),LocalQuestVideo.resolve(config,"media:"+file.getFileName()));
        assertFalse(Files.exists(config.resolve("ftbquests/.media-cache")));
    }
    @Test void missingVideoFailsWithoutCreatingAnyDirectoriesOrFiles()throws Exception{
        var error=assertThrows(IOException.class,()->LocalQuestVideo.resolve(config,"media:"+"a".repeat(64)+".mp4"));
        assertEquals("local.missing",error.getMessage());
        try(var files=Files.list(config)){assertEquals(0,files.count());}
    }
    @Test void explicitLocalPathsAllowPackSubdirectoriesAndSpaces()throws Exception{
        Path file=config.resolve("ftbquests/videos/教程/演示.mp4");Files.createDirectories(file.getParent());Files.write(file,new byte[]{1});
        assertEquals(file.toRealPath(),LocalQuestVideo.resolve(config,"local:教程/演示.mp4"));
        assertEquals(file.toRealPath(),LocalQuestVideo.resolve(config,"local:教程\\演示.mp4"));
    }
    @Test void rejectsTraversalAbsolutePathsAndSymlinkEscapes()throws Exception{
        for(String source:List.of("local:../../private.mp4","local:/private.mp4","local:C:\\private.mp4","local:file.txt","media:../../private.mp4"))
            assertThrows(IOException.class,()->LocalQuestVideo.resolve(config,source));
        Path outside=config.resolve("external");Files.createDirectories(outside);Files.write(outside.resolve("test.mp4"),new byte[]{1});
        Path root=config.resolve("ftbquests/videos");Files.createDirectories(root);Files.createSymbolicLink(root.resolve("escape"),outside);
        assertThrows(IOException.class,()->LocalQuestVideo.resolve(config,"local:escape/test.mp4"));
    }
    @Test void alreadyPresentLegacyCacheIsReadOnlyAndNeverRecreated()throws Exception{
        Path file=imported(config.resolve("ftbquests/.media-cache"));
        byte[] before=Files.readAllBytes(file);
        assertEquals(file.toRealPath(),LocalQuestVideo.resolve(config,"media:"+file.getFileName()));
        assertArrayEquals(before,Files.readAllBytes(file));assertFalse(Files.exists(LocalQuestVideo.directory(config)));
    }
    @Test void douyinLinkOnlyFallbackWorksWithoutMediaApi()throws Exception{
        assertEquals("https://v.douyin.com/IVOk7RWtfKU/",DouyinLink.page("复制链接 https://v.douyin.com/IVOk7RWtfKU/ 打开抖音"));
        assertFalse(DouyinLink.matches("https://douyin.com.evil.example/x"));
        assertFalse(DouyinLink.matches("https://douyin.com@evil.example/x"));
    }
}
