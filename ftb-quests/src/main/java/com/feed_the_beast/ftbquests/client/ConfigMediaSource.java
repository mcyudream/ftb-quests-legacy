package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.config.*;
import com.feed_the_beast.ftblib.lib.gui.IOpenableGui;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftbquests.texteditor.GuiDescriptionComponent;
import com.google.gson.JsonObject;
import java.util.function.*;

/** Adds the same import dialog to a canvas media_source without changing its stored string type. */
public final class ConfigMediaSource extends ConfigString.SimpleString
{
    private final Supplier<String> kind;
    public ConfigMediaSource(Supplier<String> get, Consumer<String> set, Supplier<String> kind) { super(get,set,null); this.kind = kind; }
    @Override public void onClicked(IOpenableGui gui, ConfigValueInstance inst, MouseButton button, Runnable callback)
    {
        if (!inst.getCanEdit()) return;
        JsonObject json = new JsonObject(); json.addProperty("type",kind.get().equals("video") ? "video" : "image"); json.addProperty("source",getString());
        new GuiDescriptionComponent(json,value -> { setString(value.has("source") ? value.get("source").getAsString() : ""); callback.run(); gui.openGui(); },"source").openGui();
    }
}
