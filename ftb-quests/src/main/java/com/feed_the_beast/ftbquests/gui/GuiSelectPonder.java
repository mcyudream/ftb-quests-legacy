package com.feed_the_beast.ftbquests.gui;

import com.feed_the_beast.ftblib.lib.gui.*;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiButtonListBase;
import com.feed_the_beast.ftblib.lib.icon.ItemIcon;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;
import com.feed_the_beast.ftblib.lib.util.PinyinSearch;
import com.feed_the_beast.ftbquests.client.PonderLink;
import java.util.*;
import java.util.function.Consumer;

/** Metadata-only search: never compile every registered scene to populate a selector. */
public final class GuiSelectPonder extends GuiButtonListBase
{
    private final GuiBase previous;
    private final List<PonderLink.Option> options;
    private final Consumer<PonderLink.Option> selected;
    private final java.util.concurrent.CompletableFuture<Map<PonderLink.Option,PinyinSearch.Key>> searchIndex;
    private boolean indexed;
    private String lastQuery="";
    private PinyinSearch.Query query=PinyinSearch.query("");
    public GuiSelectPonder(GuiBase previous,List<PonderLink.Option> options,Consumer<PonderLink.Option> selected)
    {
        this.previous=previous;this.options=List.copyOf(options);this.selected=selected;setTitle(PonderLink.tr("choose"));setHasSearchBox(true);setBorder(2,2,2);
        searchIndex=java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Map<PonderLink.Option,PinyinSearch.Key> index=new HashMap<>();
            for(var option:this.options)index.put(option,PinyinSearch.key(option.title()+" "+option.value()));
            return Map.copyOf(index);
        },PinyinSearch.executor());
    }
    private final class EntryButton extends SimpleTextButton
    {
        final PonderLink.Option option;
        EntryButton(Panel panel,PonderLink.Option option){super(panel,option.title(),ItemIcon.getItemIcon(option.item()));this.option=option;setSize(Math.min(360,getScreen().getScaledWidth()-56),22);}
        @Override public void addMouseOverText(List<String> tooltip){tooltip.add(option.title());tooltip.add(option.value());}
        @Override public void onClicked(MouseButton button){if(button.isLeft()){GuiHelper.playClickSound();previous.openGui();selected.accept(option);}}
    }
    @Override public void addButtons(Panel panel)
    {
        if(options.isEmpty()){panel.add(new TextField(panel).setMaxWidth(300).setText(PonderLink.tr("no_entries")));return;}
        for(var option:options)panel.add(new EntryButton(panel,option));
    }
    @Override public String getFilterText(Widget widget)
    {return (widget.getTitle()+" "+(widget instanceof EntryButton entry?entry.option.value():"")).toLowerCase(Locale.ROOT);}
    @Override public boolean matchesFilter(Widget widget,String query)
    {
        if (super.matchesFilter(widget,query)) return true;
        if(!lastQuery.equals(query)) { lastQuery=query;this.query=PinyinSearch.query(query); }
        var index=searchIndex.getNow(Map.of());
        return widget instanceof EntryButton entry && index.containsKey(entry.option) && index.get(entry.option).matches(this.query);
    }
    @Override public void tick()
    {
        super.tick();
        if(!indexed&&searchIndex.isDone()) { indexed=true;refreshFilter(); }
    }
    @Override public Theme getTheme(){return FTBQuestsTheme.INSTANCE;}
}
