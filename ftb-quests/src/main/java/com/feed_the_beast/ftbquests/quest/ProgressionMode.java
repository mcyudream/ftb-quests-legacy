package com.feed_the_beast.ftbquests.quest;

import com.feed_the_beast.ftblib.lib.util.IWithID;
import com.feed_the_beast.ftblib.lib.util.misc.NameMap;

public enum ProgressionMode implements IWithID {
    DEFAULT("default"), LINEAR("linear"), FLEXIBLE("flexible");

    public static final NameMap<ProgressionMode> NAME_MAP = NameMap.createWithBaseTranslationKey(
            DEFAULT, "ftbquests.file.progression_mode", values());
    private final String id;
    ProgressionMode(String id) { this.id = id; }
    @Override public String getId() { return id; }
}
