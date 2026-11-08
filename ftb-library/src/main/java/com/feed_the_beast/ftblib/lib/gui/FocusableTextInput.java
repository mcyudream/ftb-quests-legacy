package com.feed_the_beast.ftblib.lib.gui;

/** Text widgets share one keyboard focus per GUI, including widgets supplied by add-ons. */
public interface FocusableTextInput
{
    boolean isFocused();
    void setFocused(boolean focused);
}
