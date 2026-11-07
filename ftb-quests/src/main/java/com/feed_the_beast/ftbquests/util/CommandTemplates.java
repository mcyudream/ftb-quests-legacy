package com.feed_the_beast.ftbquests.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandTemplates
{
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z_]+)}|@([a-z_]+)(?![a-zA-Z0-9_])");
    private CommandTemplates() {}
    public static String expand(String text, Map<String, ?> values)
    {
        Matcher matcher = TOKEN.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find())
        {
            Object value = values.get(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value.toString()));
        }
        return matcher.appendTail(result).toString();
    }
}
