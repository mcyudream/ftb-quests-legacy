package com.feed_the_beast.ftbquests.util;

import java.net.*;
import java.util.*;
import java.util.regex.Pattern;

/** Link-only fallback remains available without the optional Media API mod. */
public final class DouyinLink
{
    private static final Set<String> HOSTS=Set.of("douyin.com","www.douyin.com","m.douyin.com","v.douyin.com","iesdouyin.com","www.iesdouyin.com","open.douyin.com");
    private static final Pattern LINK=Pattern.compile("https?://[^\\s<>\\\"'，。！？）)\\]]+",Pattern.CASE_INSENSITIVE);
    private DouyinLink(){}
    public static String page(String text)
    {
        if(text==null)return "";var match=LINK.matcher(text);
        while(match.find())try{
            URI uri=URI.create(match.group());
            if(uri.getHost()!=null&&HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))&&uri.getUserInfo()==null&&uri.getPort()==-1)
                return new URI("https",null,uri.getHost().toLowerCase(Locale.ROOT),-1,uri.getPath(),uri.getQuery(),null).toString();
        }catch(IllegalArgumentException|URISyntaxException ignored){}
        return "";
    }
    public static boolean matches(String text){return !page(text).isEmpty();}
}
