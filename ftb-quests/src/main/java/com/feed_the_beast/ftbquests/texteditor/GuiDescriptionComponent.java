package com.feed_the_beast.ftbquests.texteditor;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.icon.Color4I;
import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiSelectItemStack;
import com.feed_the_beast.ftbquests.client.*;
import com.feed_the_beast.ftbquests.gui.GuiSelectQuestObject;
import com.feed_the_beast.ftbquests.quest.*;
import com.feed_the_beast.ftbquests.util.*;
import com.google.gson.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import java.util.*;
import java.util.function.Consumer;

/** Structured component editing retains fields unknown to this version. */
public final class GuiDescriptionComponent extends GuiBase
{
    private final JsonObject original;
    private final String kind;
    private final Consumer<JsonObject> accept;
    private final Map<String, TextBox> inputs = new LinkedHashMap<>();
    private final Map<String, SimpleTextButton> selectors = new LinkedHashMap<>();
    private final Panel fields;
    private final PanelScrollBar scroll;
    private final SimpleTextButton save, cancel;
    private String error = "";
    private String status = "";
    private ClientMediaAssets.Import importing;

    public GuiDescriptionComponent(JsonObject json, Consumer<JsonObject> accept, String... fieldOverride)
    {
        original = json.deepCopy(); this.accept = accept;
        kind = json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "text";
        if (kind.equals("video")) status = GuiMediaFilePicker.tr("online_hint");
        if (kind.equals("ponder")) status = PonderLink.tr("hint");
        fields = new Panel(this) {
            @Override public void addWidgets() { addAll(inputs.values()); addAll(selectors.values()); }
            @Override public void alignWidgets() {
                int y = 0;
                for (var entry : inputs.entrySet()) {
                    SimpleTextButton selector = selectors.get(entry.getKey());
                    entry.getValue().setPosAndSize(90, y + 2, Math.max(24, width - 94 - (selector == null ? 0 : 82)), 18);
                    if (selector != null) selector.setPosAndSize(width - 80, y + 2, 76, 18);
                    y += 24;
                }
                scroll.setMaxValue(y);
            }
            @Override public void drawOffsetBackground(Theme theme, int x, int y, int w, int h) {
                int row = 0;
                for (String field : inputs.keySet()) {
                    String key = (kind.equals("image") || kind.equals("video")) && (field.equals("width") || field.equals("height")) ? "media_" + field : "field." + field;
                    String label = kind.equals("ponder") && field.equals("value") ? PonderLink.tr("target") : GuiQuestTextEditor.tr(key);
                    theme.drawString(label, x + 4, y + 7 + row++ * 24, Color4I.GRAY, 0);
                }
            }
        };
        scroll = new PanelScrollBar(this, fields);
        String[] names = fieldOverride.length > 0 ? fieldOverride : switch (kind) {
            case "image", "video" -> new String[]{"source", "cover", "width", "height"};
            case "entity" -> new String[]{"source", "nbt", "width", "height"};
            case "font" -> new String[]{"text", "font", "scale", "color", "width"};
            case "item" -> new String[]{"item", "text", "nbt", "size"};
            case "quest" -> new String[]{"value", "text", "hover"};
            case "table" -> new String[]{"rows", "width"};
            case "localize", "keybind" -> new String[]{"value"};
            case "heading", "obfuscated" -> new String[]{"text"};
            default -> new String[]{"text", "value", "hover"};
        };
        for (String name : names)
        {
            TextBox input = new TextBox(fields) {
                @Override public void addMouseOverText(List<String> tooltip) {
                    super.addMouseOverText(tooltip);
                    if ((kind.equals("image") || kind.equals("video")) && (name.equals("width") || name.equals("height"))) tooltip.add(GuiQuestTextEditor.tr("media_size_hint"));
                    if (kind.equals("ponder") && name.equals("value")) tooltip.add(PonderLink.tr("format"));
                }
            };
            String value = json.has(name) ? json.get(name).isJsonPrimitive() ? json.get(name).getAsString() : json.get(name).toString()
                    : name.equals("rows") ? "[[\"A\",\"B\"],[\"1\",\"2\"]]" : "";
            input.charLimit = Math.max(1_000_000, value.length()); input.setText(value, false); inputs.put(name, input);
            if (kind.equals("ponder") && name.equals("value")) input.ghostText = "minecraft:compass#pack:scene";
            if ((kind.equals("image") || kind.equals("video")) && (name.equals("source") || name.equals("cover")))
                selectors.put(name, fieldButton(GuiMediaFilePicker.tr("browse"), () -> chooseMedia(name, kind.equals("video") && name.equals("source"))));
            else if (kind.equals("quest") && name.equals("value"))
                selectors.put(name, fieldButton(GuiQuestTextEditor.tr("choose_quest"), this::chooseQuest));
            else if (kind.equals("item") && name.equals("item"))
                selectors.put(name, fieldButton(GuiQuestTextEditor.tr("choose_item"), this::chooseItem));
            else if (kind.equals("ponder") && name.equals("value"))
                selectors.put(name, fieldButton(PonderLink.tr("choose_button"), () -> PonderLink.choose(this, option -> {
                    inputs.get("value").setText(option.value(),false);
                    if (field("text").isEmpty()) inputs.get("text").setText(option.title(),false);
                    error = "";
                })));
        }
        save = button(GuiQuestTextEditor.tr("insert_apply"), this::apply);
        cancel = button(I18n.format("gui.cancel"), this::onBack);
    }
    private SimpleTextButton fieldButton(String label, Runnable action) { return new SimpleTextButton(fields,label,Icon.EMPTY) {
        @Override public void onClicked(MouseButton button) { if (button.isLeft() && !isImporting()) action.run(); }
        @Override public boolean renderTitleInCenter() { return true; }
        @Override public WidgetType getWidgetType() { return isImporting() ? WidgetType.DISABLED : super.getWidgetType(); }
    }; }
    public boolean isImporting() { return importing != null && !importing.result.isDone(); }
    public String field(String name) { TextBox value = inputs.get(name); return value == null ? "" : value.getText(); }
    public void chooseMedia(String field, boolean video)
    {
        if (isImporting()) return;
        new GuiMediaFilePicker(video, path -> {
            error = ""; status = "";
            ClientMediaAssets.Import operation = ClientMediaAssets.importFile(path, video); importing = operation;
            operation.result.whenComplete((source, failure) -> {
                if (importing != operation || operation.result.isCancelled()) return;
                if (failure == null) { inputs.get(field).setText(source,false); status = GuiMediaFilePicker.tr(video?"video_imported":"imported"); }
                else { error = GuiMediaFilePicker.tr("error." + MediaIO.error(failure)); status = ""; }
            });
        }).openGui();
    }
    private void chooseQuest()
    {
        if (!ClientQuestFile.exists()) return;
        ConfigQuestObject config = new ConfigQuestObject(ClientQuestFile.INSTANCE, ClientQuestFile.INSTANCE.getID(field("value")), object -> object instanceof Quest);
        new GuiSelectQuestObject(config,this,() -> {
            Quest quest = ClientQuestFile.INSTANCE.getQuest(config.getObject()); if (quest == null) return;
            inputs.get("value").setText(quest.getCodeString(),false);
            if (field("text").isEmpty()) inputs.get("text").setText(quest.getUnformattedTitle(),false);
        }).openGui();
    }
    private void chooseItem()
    {
        ItemStack initial = ItemStack.EMPTY;
        try { NBTTagCompound nbt = JsonToNBT.getTagFromJson(field("nbt").isEmpty() ? "{}" : field("nbt")); nbt.setString("id",field("item")); nbt.setByte("Count",(byte)1); initial = new ItemStack(nbt); }
        catch (Exception ignored) {}
        new GuiSelectItemStack(this,initial,true,stack -> {
            if (stack.isEmpty()) return;
            NBTTagCompound nbt = stack.serializeNBT(); inputs.get("item").setText(nbt.getString("id"),false);
            nbt.removeTag("id"); nbt.removeTag("Count"); inputs.get("nbt").setText(nbt.toString(),false);
            if (field("text").isEmpty()) inputs.get("text").setText(stack.getDisplayName(),false);
            if (field("size").isEmpty()) inputs.get("size").setText("32",false);
        }).openGui();
    }
    private SimpleTextButton button(String text, Runnable action)
    {
        return new SimpleTextButton(this, text, Icon.EMPTY) {
            @Override public void onClicked(MouseButton button) { if (button.isLeft()) action.run(); }
            @Override public boolean renderTitleInCenter() { return true; }
            @Override public WidgetType getWidgetType() { return this == save && isImporting() ? WidgetType.DISABLED : super.getWidgetType(); }
        };
    }
    private void apply()
    {
        if (isImporting()) return;
        JsonObject json = original.deepCopy();
        if (kind.equals("ponder")) {
            try { PonderTarget.parse(field("value")); }
            catch (IllegalArgumentException ex) { error = PonderLink.tr("invalid"); return; }
        }
        for (var entry : inputs.entrySet())
        {
            String name = entry.getKey(), value = entry.getValue().getText();
            if (value.isEmpty()) { json.remove(name); continue; }
            try
            {
                switch (name)
                {
                    case "rows" -> {
                        JsonArray rows = new JsonParser().parse(value).getAsJsonArray();
                        if (rows.size() > 128) throw new IllegalArgumentException();
                        for (JsonElement row : rows) { if (row.getAsJsonArray().size() > 64) throw new IllegalArgumentException(); for (JsonElement cell : row.getAsJsonArray()) if (!cell.isJsonPrimitive()) throw new IllegalArgumentException(); }
                        json.add(name, rows);
                    }
                    case "width", "height" -> { int size = Integer.parseInt(value); if (size < 8 || size > 4096) throw new IllegalArgumentException(); json.addProperty(name, size); }
                    case "size" -> { int size = Integer.parseInt(value); if (size < 8 || size > 256) throw new IllegalArgumentException(); json.addProperty(name,size); }
                    case "color" -> json.addProperty(name, Integer.decode(value));
                    case "scale" -> { float scale = Float.parseFloat(value); if (!Float.isFinite(scale) || scale < .5F || scale > 4F) throw new IllegalArgumentException(); json.addProperty(name, scale); }
                    case "nbt" -> { JsonToNBT.getTagFromJson(value); json.addProperty(name,value); }
                    default -> json.addProperty(name, kind.equals("ponder") && name.equals("value") ? PonderTarget.parse(value).value() : value);
                }
            }
            catch (Exception ex) { error = GuiQuestTextEditor.tr("invalid_field", GuiQuestTextEditor.tr("field." + name)); return; }
        }
        if (kind.equals("ponder") && !json.has("text")) json.addProperty("text",PonderLink.tr("open"));
        accept.accept(json);
    }
    @Override public boolean onInit() { setSize(Math.min(520, getScreen().getScaledWidth() - 16), Math.min(300, getScreen().getScaledHeight() - 16)); return true; }
    @Override public void addWidgets() { add(fields); add(scroll); add(save); add(cancel); }
    @Override public void alignWidgets() {
        fields.setPosAndSize(8, 28, width - 24, height - 70); fields.alignWidgets();
        scroll.setPosAndSize(width - 16, 28, 8, height - 70);
        save.setPosAndSize(width - 180, height - 26, 96, 18); cancel.setPosAndSize(width - 76, height - 26, 68, 18);
    }
    @Override public boolean mousePressed(MouseButton button) {
        if (button == MouseButton.BACK) { onBack(); return true; }
        for (TextBox input : inputs.values()) input.setFocused(input.isMouseOver());
        return super.mousePressed(button);
    }
    @Override public void onBack() { if (importing != null) { ClientMediaAssets.Import operation = importing; importing = null; operation.cancel(); } closeGui(true); }
    @Override public boolean onClosedByKey(int key) { if (key == Keyboard.KEY_ESCAPE) onBack(); return false; }
    @Override public void tick() { super.tick(); Keyboard.enableRepeatEvents(inputs.values().stream().anyMatch(TextBox::isFocused)); }
    @Override public void drawBackground(Theme theme, int x, int y, int w, int h) {
        Color4I.rgb(0x18232D).draw(x, y, w, h);
        theme.drawString(GuiQuestTextEditor.tr("component_title", GuiQuestTextEditor.tr("kind." + kind)), x + 8, y + 9, Color4I.WHITE, 0);
        String message = isImporting() ? GuiMediaFilePicker.tr(importing.phase(), importing.percent()) : error.isEmpty() ? status : error;
        theme.drawString(theme.trimStringToWidth(message, w - 16), x + 8, y + h - 39, error.isEmpty() ? Color4I.GRAY : Color4I.RED, 0);
    }
}
