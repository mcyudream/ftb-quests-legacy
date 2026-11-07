package com.feed_the_beast.ftblib.lib.util;

/** Reassembles UTF-16 character events so one input commit never contains half a code point. */
public final class UnicodeTextInput {
    private char high;
    public String accept(char value) {
        if (Character.isHighSurrogate(value)) { high = value; return ""; }
        if (Character.isLowSurrogate(value)) {
            char first = high; high = 0;
            return first == 0 ? "" : new String(new char[]{first, value});
        }
        high = 0;
        return Character.toString(value);
    }
    public void reset() { high = 0; }
}
