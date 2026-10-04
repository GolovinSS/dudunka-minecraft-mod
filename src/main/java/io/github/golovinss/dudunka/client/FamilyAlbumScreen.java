package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Server-authoritative commands, with paging and scrolling for small windows/long names. */
public final class FamilyAlbumScreen extends Screen {
    private FamilyAlbum.Snapshot snapshot;
    private final UUID session;
    private int request,pending,pendingTicks;
    private String status="screen.dudunka.album_commands";
    private int page,guidePage,scroll;
    private boolean guide;
    private List<FormattedCharSequence> lines=List.of();
    private Button previous,next,stay,follow;
    private int left,panelWidth,top,bottom;
    public FamilyAlbumScreen(AlbumCommands.Open message){super(Component.translatable("screen.dudunka.album"));session=message.session();snapshot=message.snapshot();guide=snapshot.entries().isEmpty();}
    public static void open(AlbumCommands.Open message){var mc=Minecraft.getInstance();if(mc.player!=null && mc.level!=null)mc.setScreen(new FamilyAlbumScreen(message));}
    public static void update(AlbumCommands.Reply reply) {
        var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof FamilyAlbumScreen screen) || !screen.session.equals(reply.session()) || screen.pending!=reply.request())return;
        UUID selected=screen.snapshot.entries().isEmpty()?null:screen.snapshot.entries().get(screen.page).id();
        screen.snapshot=reply.snapshot();screen.page=Math.max(0,Math.min(screen.page,screen.snapshot.entries().size()-1));
        for(int i=0;i<screen.snapshot.entries().size();i++)if(screen.snapshot.entries().get(i).id().equals(selected))screen.page=i;
        screen.pending=0;screen.status=reply.accepted()?"screen.dudunka.album_applied":"screen.dudunka.album_rejected";screen.rebuild();
    }
    private void command(boolean waiting) {
        if(pending!=0 || snapshot.entries().isEmpty())return;
        pending=++request;pendingTicks=0;status="screen.dudunka.album_pending";rebuild();
        AlbumNetwork.command(new AlbumCommands.Command(session,snapshot.entries().get(page).id(),waiting,pending));
    }
    @Override public void tick(){if(pending!=0 && ++pendingTicks>=60){pending=0;status="screen.dudunka.album_timeout";rebuild();}}
    @Override protected void init(){
        panelWidth=Math.min(340,width-16);left=(width-panelWidth)/2;top=69+font.split(scope(),panelWidth-16).size()*10;bottom=height-105;
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->change(-1)).bounds(left,height-30,40,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->change(1)).bounds(left+panelWidth-40,height-30,40,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-45,height-30,90,20).build());
        stay=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.album_stay"),b->command(true)).bounds(left,height-60,panelWidth/2-4,20).build());
        follow=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.album_follow"),b->command(false)).bounds(left+panelWidth/2+4,height-60,panelWidth/2-4,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.guide_tab"),b->tab(true)).bounds(left,31,panelWidth/2-4,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.family_tab"),b->tab(false)).bounds(left+panelWidth/2+4,31,panelWidth/2-4,20).build());
        rebuild();
    }
    private void tab(boolean guide){this.guide=guide;scroll=0;rebuild();}
    private void change(int delta){if(guide){guidePage=Math.max(0,Math.min(EggGuide.PAGES-1,guidePage+delta));scroll=0;rebuild();return;}page=Math.max(0,Math.min(snapshot.entries().size()-1,page+delta));scroll=0;rebuild();}
    private void rebuild(){
        top=69+font.split(scope(),panelWidth-16).size()*10;bottom=guide?height-42:height-105;stay.visible=follow.visible=!guide;
        var text=new ArrayList<Component>();
        if(guide){text.addAll(EggGuide.page(guidePage,snapshot.guideFlags()));}
        else if(snapshot.entries().isEmpty())text.add(Component.translatable("screen.dudunka.album_empty"));
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
        if(!guide){text.add(Component.empty());text.add(Component.translatable("screen.dudunka.album_snapshot"));}
        var wrapped=new ArrayList<FormattedCharSequence>();for(var line:text)wrapped.addAll(font.split(line,Math.max(40,panelWidth-24)));
        lines=List.copyOf(wrapped);previous.active=guide?guidePage>0:pending==0 && page>0;next.active=guide?guidePage+1<EggGuide.PAGES:pending==0 && page+1<snapshot.entries().size();scroll=Math.min(scroll,maxScroll());
        stay.active=pending==0 && !snapshot.entries().isEmpty() && !snapshot.entries().get(page).staying();
        follow.active=pending==0 && !snapshot.entries().isEmpty() && snapshot.entries().get(page).staying();
    }
    private Component scope(){if(guide)return Component.translatable("screen.dudunka.guide_scope");return Component.translatable("screen.dudunka.album_scope",snapshot.entries().size(),snapshot.total());}
    private int maxScroll(){return Math.max(0,lines.size()*12-(bottom-top));}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(amount*24)));return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        renderBackground(graphics);graphics.fill(left-4,8,left+panelWidth+4,height-36,0xEE29241D);
        graphics.drawCenteredString(font,title,width/2,16,0xFFE2AD);
        int subtitleY=61;for(var line:font.split(scope(),panelWidth-16)){graphics.drawString(font,line,width/2-font.width(line)/2,subtitleY,0xC7C0AE,false);subtitleY+=10;}
        graphics.enableScissor(left,top,left+panelWidth,bottom);
        int y=top-scroll;for(var line:lines){graphics.drawString(font,line,left+12,y,0xF3E8CF,false);y+=12;}
        graphics.disableScissor();
        if(maxScroll()>0)graphics.drawString(font,Component.literal(scroll<maxScroll()?"↓":"↑"),left+panelWidth-10,bottom-10,0xFFE2AD,false);
        if(guide)graphics.drawCenteredString(font,Component.literal((guidePage+1)+" / "+EggGuide.PAGES),width/2,height-42,0xC7C0AE);
        else if(!snapshot.entries().isEmpty())graphics.drawCenteredString(font,Component.literal((page+1)+" / "+snapshot.entries().size()),width/2,height-102,0xC7C0AE);
        int statusY=height-90;if(!guide)for(var line:font.split(Component.translatable(status),panelWidth-16)){graphics.drawString(font,line,width/2-font.width(line)/2,statusY,0xC7C0AE,false);statusY+=10;}
        super.render(graphics,mouseX,mouseY,partial);
    }
}
