package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftblib.lib.config.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfig;
import com.feed_the_beast.ftbquests.gui.tree.GuiQuestTree;
import com.feed_the_beast.ftbquests.net.edit.MessageBulkEdit;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.EditHistory;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

public final class BulkEditor
{
    public static void open(GuiQuestTree gui)
    {
        List<Quest> selected = new ArrayList<>();
        for (Movable object : gui.selectedObjects) if (object instanceof Quest) selected.add((Quest)object);
        if (selected.isEmpty()) return;
        Quest template = new Quest(selected.get(0).chapter);
        template.readDataPreservingUnknown(EditHistory.snapshot(selected.get(0)));
        ConfigGroup group = ConfigGroup.newGroup("ftbquests");
        ConfigGroup apply = group.getGroup("bulk_apply"), values = group.getGroup("bulk_values");
        Set<String> fields = new HashSet<>();
        for (String field : MessageBulkEdit.FIELDS) apply.addBool(field, () -> fields.contains(field), v -> { if (v) fields.add(field); else fields.remove(field); }, false);
        values.addEnum("shape", () -> template.shape.isEmpty() ? "default" : template.shape, v -> template.shape = v.equals("default") ? "" : v, QuestShape.idMapWithDefault);
        values.addDouble("size", () -> template.size, v -> template.size=v, 1, 0.0625, 8);
        values.addBool("optional", () -> template.optional, v -> template.optional=v, false);
        values.addBool("hide", () -> template.hide, v -> template.hide=v, false);
        values.addBool("secret", () -> template.secret, v -> template.secret=v, false);
        values.addBool("can_repeat", () -> template.canRepeat, v -> template.canRepeat=v, false);
        values.addBool("or_tasks", () -> template.orTasks, v -> template.orTasks=v, false);
        values.addBool("hide_dependency_lines", () -> template.hideDependencyLines, v -> template.hideDependencyLines=v, false);
        values.addEnum("progression_mode", () -> template.progressionMode, v -> template.progressionMode=v, ProgressionMode.NAME_MAP);
        values.addEnum("require_sequential_tasks", () -> template.requireSequentialTasks, v -> template.requireSequentialTasks=v, EnumTristate.NAME_MAP);
        values.addEnum("hide_details_until_startable", () -> template.hideDetailsUntilStartable, v -> template.hideDetailsUntilStartable=v, EnumTristate.NAME_MAP);
        values.addInt("min_required_tasks", () -> template.minRequiredTasks, v -> template.minRequiredTasks=v, 0, 0, Integer.MAX_VALUE);
        values.addString("background_image", () -> template.backgroundImage, v -> template.backgroundImage=v, "");
        // These are authoring controls, so reuse the quest field names instead of unresolved bulk paths.
        for (ConfigGroup section : List.of(apply,values))
            for (ConfigValueInstance field : section.getValues())
                field.setDisplayName(new net.minecraft.util.text.TextComponentTranslation("ftbquests.quest."+field.getId()));
        new GuiEditConfig(group, (config, sender) -> {
            NBTTagCompound data = EditHistory.snapshot(template), patch = new NBTTagCompound();
            // Explicit defaults are needed when applying a cleared optional field.
            for (String field : fields)
            {
                if (data.hasKey(field)) patch.setTag(field,data.getTag(field).copy());
                else if (field.equals("shape") || field.equals("background_image")) patch.setString(field, "");
                else patch.setBoolean(field,false);
            }
            if (!patch.isEmpty()) new MessageBulkEdit(selected.stream().mapToInt(q -> q.id).toArray(),patch).sendToServer();
            gui.openGui();
        }).openGui();
    }
}
