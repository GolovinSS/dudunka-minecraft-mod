package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.FamilyAlbum;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Read-only snapshot, with paging and scrolling for small windows/long names. */
public final class FamilyAlbumScreen extends Screen {
    private final FamilyAlbum.Snapshot snapshot;
    private int page,scroll;
    private List<FormattedCharSequence> lines=List.of();
    private Button previous,next;
    private int left,panelWidth,top,bottom;
    public FamilyAlbumScreen(FamilyAlbum.Snapshot snapshot){super(Component.translatable("screen.dudunka.album"));this.snapshot=snapshot;}
    public static void open(FamilyAlbum.Snapshot snapshot){var mc=Minecraft.getInstance();if(mc.player!=null && mc.level!=null)mc.setScreen(new FamilyAlbumScreen(snapshot));}
    @Override protected void init(){
        panelWidth=Math.min(340,width-16);left=(width-panelWidth)/2;top=39+font.split(scope(),panelWidth-16).size()*10;bottom=height-42;
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->change(-1)).bounds(left,height-30,40,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->change(1)).bounds(left+panelWidth-40,height-30,40,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-45,height-30,90,20).build());
        rebuild();
    }
    private void change(int delta){page=Math.max(0,Math.min(snapshot.entries().size()-1,page+delta));scroll=0;rebuild();}
    private void rebuild(){
        var text=new ArrayList<Component>();
        if(snapshot.entries().isEmpty())text.add(Component.translatable("screen.dudunka.album_empty"));
        else {
            var e=snapshot.entries().get(page);
            text.add(e.name());
            text.add(Component.translatable("screen.dudunka.album_kind",Component.translatable("entity.dudunka."+e.kind().id)));
            text.add(Component.translatable("screen.dudunka.album_age",Component.translatable("stage.dudunka."+e.stage())));
            text.add(Component.translatable("screen.dudunka.album_trust",e.trust()));
            text.add(Component.translatable("screen.dudunka.album_mode",Component.translatable(e.staying()?"mode.dudunka.stay":"mode.dudunka.follow")));
            text.add(Component.empty());
            text.add(Component.translatable("screen.dudunka.album_home",Component.translatable("home.dudunka."+e.home().state().name().toLowerCase(Locale.ROOT))));
            if(e.home().state()==FamilyAlbum.HomeState.READY || e.home().state()==FamilyAlbum.HomeState.INCOMPLETE || e.home().state()==FamilyAlbum.HomeState.UNSAFE) {
                String[] conditions={"roof","bed","chest","light","food"};
                for(int i=0;i<conditions.length;i++)text.add(Component.translatable("screen.dudunka.album_condition",Component.translatable("home.dudunka.condition."+conditions[i]),Component.translatable((e.home().flags()&(1<<i))!=0?"message.dudunka.yes":"message.dudunka.no")));
            }
            text.add(Component.empty());text.add(Component.translatable("screen.dudunka.album_friends"));
            if(e.friends().isEmpty())text.add(Component.translatable("message.dudunka.friendship_empty"));
            for(var f:e.friends()) {
                String tier=f.score()>=70?"family":f.score()>=30?"close":f.score()>=10?"friends":"acquainted";
                text.add(Component.translatable("message.dudunka.friendship_row",f.name(),f.score(),Component.translatable("friendship.dudunka."+tier)));
            }
        }
        text.add(Component.empty());text.add(Component.translatable("screen.dudunka.album_snapshot"));
        var wrapped=new ArrayList<FormattedCharSequence>();for(var line:text)wrapped.addAll(font.split(line,Math.max(40,panelWidth-24)));
        lines=List.copyOf(wrapped);previous.active=page>0;next.active=page+1<snapshot.entries().size();scroll=Math.min(scroll,maxScroll());
    }
    private Component scope(){return Component.translatable("screen.dudunka.album_scope",snapshot.entries().size(),snapshot.total());}
    private int maxScroll(){return Math.max(0,lines.size()*12-(bottom-top));}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(amount*24)));return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        renderBackground(graphics);graphics.fill(left-4,8,left+panelWidth+4,height-36,0xEE29241D);
        graphics.drawCenteredString(font,title,width/2,16,0xFFE2AD);
        int subtitleY=31;for(var line:font.split(scope(),panelWidth-16)){graphics.drawString(font,line,width/2-font.width(line)/2,subtitleY,0xC7C0AE,false);subtitleY+=10;}
        graphics.enableScissor(left,top,left+panelWidth,bottom);
        int y=top-scroll;for(var line:lines){graphics.drawString(font,line,left+12,y,0xF3E8CF,false);y+=12;}
        graphics.disableScissor();
        if(maxScroll()>0)graphics.drawString(font,Component.literal(scroll<maxScroll()?"↓":"↑"),left+panelWidth-10,bottom-10,0xFFE2AD,false);
        if(!snapshot.entries().isEmpty())graphics.drawCenteredString(font,Component.literal((page+1)+" / "+snapshot.entries().size()),width/2,height-42,0xC7C0AE);
        super.render(graphics,mouseX,mouseY,partial);
    }
}
