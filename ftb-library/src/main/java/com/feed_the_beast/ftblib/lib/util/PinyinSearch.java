package com.feed_the_beast.ftblib.lib.util;

import com.ibm.icu.text.Transliterator;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;

/** Reuses Cleanroom's ICU; immutable search keys are built off the render thread. */
public final class PinyinSearch
{
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "FTB Pinyin Index"); thread.setDaemon(true); return thread;
    });
    private static final Map<String, Key> CACHE = new LinkedHashMap<>(512, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Key> entry) { return size() > 32768; }
    };
    private static Transliterator hanLatin;

    public record Key(String literal, String syllables, String compact, String initials)
    {
        public boolean matches(String query) { return matches(PinyinSearch.query(query)); }
        public boolean matches(Query query)
        {
            for (int i=0;i<query.literal.size();i++)
            {
                String token=query.literal.get(i);
                if (literal.contains(token)) continue;
                String latin=query.latin.get(i);
                if (!syllables.contains(latin) && !compact.contains(latin) && !initials.contains(latin)
                        && !compact.replace('v','u').contains(latin.replace('v','u'))) return false;
            }
            return true;
        }
    }

    public record Query(List<String> literal,List<String> latin) {}
    public static Query query(String value)
    {
        List<String> literal=List.of(normalize(value).strip().split("\\s+"));
        return new Query(literal,literal.stream().map(token->latin(token).replace("'", "").replace("’", "")).toList());
    }

    public static Executor executor() { return WORKER; }
    public static void warm(Collection<String> values)
    {
        List<String> names=List.copyOf(values);
        WORKER.execute(()->{ for(String value:names)key(value); });
    }
    public static void clear() { synchronized (CACHE) { CACHE.clear(); } }

    public static String normalize(String text)
    { return Normalizer.normalize(stripFormatting(text), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }

    public static Key key(String text)
    {
        String literal = normalize(text);
        synchronized (CACHE) { Key known = CACHE.get(literal); if (known != null) return known; }
        String converted = literal;
        if (literal.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN))
        {
            synchronized (PinyinSearch.class)
            {
                if (hanLatin == null) hanLatin = Transliterator.getInstance("Han-Latin");
                converted = hanLatin.transliterate(literal);
            }
        }
        String syllables = latin(converted);
        StringBuilder compact = new StringBuilder(), initials = new StringBuilder();
        boolean start = true;
        for (int c : syllables.codePoints().toArray())
        {
            if (Character.isLetterOrDigit(c))
            {
                compact.appendCodePoint(c);
                if (start) initials.appendCodePoint(c);
                start = false;
            }
            else start = true;
        }
        Key result = new Key(literal, syllables, compact.toString(), initials.toString());
        synchronized (CACHE) { CACHE.put(literal, result); }
        return result;
    }

    private static String latin(String text)
    {
        // u:, ü and v are accepted spellings for the same pinyin vowel.
        String decomposed = Normalizer.normalize(text.replace("u:", "v"), Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < decomposed.length(); )
        {
            int c = decomposed.codePointAt(i); i += Character.charCount(c);
            if (c == 'u' && i < decomposed.length() && decomposed.charAt(i) == '\u0308') { c = 'v'; i++; }
            int type = Character.getType(c);
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK && type != Character.ENCLOSING_MARK) result.appendCodePoint(c);
        }
        return result.toString();
    }

    private static String stripFormatting(String text)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) { i++; continue; }
            result.append(c);
        }
        return result.toString();
    }
}
