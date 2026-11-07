package com.feed_the_beast.ftbquests.util;

import java.io.IOException;
import java.nio.file.*;

/** Client-installed videos. This path never requests bytes from a game server. */
public final class LocalQuestVideo
{
    private LocalQuestVideo(){}
    public static Path directory(Path config){return config.resolve("ftbquests/media");}
    public static Path resolve(Path config,String source)throws IOException
    {
        if(source.startsWith("media:")){
            String name=source.substring(6);
            if(!QuestMedia.validName(name)||!QuestMedia.video(name))throw new IOException("local.invalid");
            Path root=directory(config);Path file=root.resolve(name);
            // Old clients may already have an imported file. Reuse it read-only;
            // never fetch or generate a cache when the installed copy is absent.
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){
                root=config.resolve("ftbquests/.media-cache");file=root.resolve(name);
            }
            check(root,file);try{QuestMedia.validate(file,name);}catch(IOException error){throw new IOException("local.invalid",error);}return file.toRealPath();
        }
        if(source.startsWith("local:")){
            String relative=source.substring(6).replace('\\','/');
            if(relative.isBlank()||relative.startsWith("/")||relative.contains(":"))throw new IOException("local.invalid");
            Path root=config.resolve("ftbquests/videos").toAbsolutePath().normalize();Path file=root.resolve(relative).normalize();
            if(!file.startsWith(root)||!QuestMedia.video(file.getFileName().toString()))throw new IOException("local.invalid");
            check(root,file);return file.toRealPath();
        }
        throw new IOException("local.invalid");
    }
    private static void check(Path root,Path file)throws IOException
    {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("local.missing");
        if(!file.toRealPath().startsWith(root.toRealPath()))throw new IOException("local.invalid");
    }
}
