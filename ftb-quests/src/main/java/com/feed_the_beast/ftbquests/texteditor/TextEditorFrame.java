package com.feed_the_beast.ftbquests.texteditor;

import com.feed_the_beast.ftbquests.quest.Quest;

/** Compatibility entry point for the old editor menu. Opens inside Minecraft. */
public final class TextEditorFrame
{
    public static void open(Quest quest) { new GuiQuestTextEditor(quest).openGui(); }
}
