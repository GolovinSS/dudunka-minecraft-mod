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
    private int request,pending,pendingTicks,retryTicks;
    private Component status=Component.translatable("screen.dudunka.album_commands");
    private int page,guidePage,recoveryPage,scroll;
    private boolean guide,recovery;
    private List<FormattedCharSequence> lines=List.of();
    private Button previous,next,stay,follow,homeMode,assignFurniture,clearFurniture,returnMember,help;
    private int left,panelWidth,top,bottom;
    public FamilyAlbumScreen(AlbumCommands.Open message){super(Component.translatable("screen.dudunka.album"));session=message.session();snapshot=message.snapshot();guide=snapshot.entries().isEmpty();}
    public static void open(AlbumCommands.Open message){var mc=Minecraft.getInstance();if(mc.player!=null && mc.level!=null)mc.setScreen(new FamilyAlbumScreen(message));}
    public static void update(AlbumCommands.Reply reply) {
        var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof FamilyAlbumScreen screen) || !screen.session.equals(reply.session()) || screen.pending!=reply.request())return;
        UUID selected=screen.snapshot.entries().isEmpty()?null:screen.snapshot.entries().get(screen.page).id();
        screen.snapshot=reply.snapshot();screen.recoveryPage=Math.max(0,Math.min(screen.recoveryPage,screen.snapshot.recovery().size()-1));screen.page=Math.max(0,Math.min(screen.page,screen.snapshot.entries().size()-1));
        for(int i=0;i<screen.snapshot.entries().size();i++)if(screen.snapshot.entries().get(i).id().equals(selected))screen.page=i;
        screen.pending=0;screen.retryTicks=reply.result().retrySeconds()*20;screen.status=Component.translatable("screen.dudunka.result."+reply.result().code().name().toLowerCase(Locale.ROOT),reply.result().retrySeconds());screen.rebuild();
    }
    private void command(boolean waiting) {command(waiting,false);}
    private void command(boolean waiting,boolean atHome) {
        if(pending!=0 || snapshot.entries().isEmpty())return;
        pending=++request;pendingTicks=0;status=Component.translatable("screen.dudunka.album_pending");rebuild();
        AlbumNetwork.command(new AlbumCommands.Command(session,snapshot.entries().get(page).id(),waiting,pending,atHome));
    }
    private void furniture(boolean clear){if(pending!=0 || snapshot.entries().isEmpty())return;pending=++request;pendingTicks=0;status=Component.translatable("screen.dudunka.album_pending");rebuild();AlbumNetwork.furniture(new AlbumCommands.FurnitureCommand(session,snapshot.entries().get(page).id(),clear,pending));}
    private void recover(){if(pending!=0 || retryTicks>0 || snapshot.recovery().isEmpty())return;pending=++request;pendingTicks=0;var target=snapshot.recovery().get(recoveryPage);status=Component.translatable(target.state()==FamilyAlbum.RecoveryState.LIVE?"screen.dudunka.searching":"screen.dudunka.album_pending",target.name());rebuild();AlbumNetwork.recover(new AlbumCommands.RecoveryCommand(session,snapshot.recovery().get(recoveryPage).id(),pending));}
    @Override public void tick(){
        if(retryTicks>0 && --retryTicks%20==0){status=Component.translatable(retryTicks==0?"screen.dudunka.retry_ready":"screen.dudunka.result.cooldown",(retryTicks+19)/20);rebuild();}
        if(pending!=0 && ++pendingTicks>=160){pending=0;status=Component.translatable("screen.dudunka.album_timeout");rebuild();}
    }
    @Override protected void init(){
        panelWidth=Math.min(340,width-16);left=(width-panelWidth)/2;top=93+font.split(scope(),panelWidth-16).size()*10;bottom=height-105;
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->change(-1)).bounds(left,height-30,40,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->change(1)).bounds(left+panelWidth-40,height-30,40,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-45,height-30,90,20).build());
        stay=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.album_stay"),b->command(true)).bounds(left,height-60,panelWidth/3-3,20).build());
        follow=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.album_follow"),b->command(false)).bounds(left+panelWidth/3+2,height-60,panelWidth/3-3,20).build());
        homeMode=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.album_home_mode"),b->command(false,true)).bounds(left+2*panelWidth/3+4,height-60,panelWidth/3-4,20).build());
        assignFurniture=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.assign_furniture"),b->furniture(false)).bounds(left,height-84,panelWidth/2-4,20).build());
        clearFurniture=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.clear_furniture"),b->furniture(true)).bounds(left+panelWidth/2+4,height-84,panelWidth/2-4,20).build());
        returnMember=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.recovery_return"),b->recover()).bounds(left,height-60,panelWidth,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.guide_tab"),b->tab(true)).bounds(left,31,panelWidth/3-3,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.family_tab"),b->tab(false)).bounds(left+panelWidth/3+2,31,panelWidth/3-3,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.recovery_tab"),b->{guide=false;recovery=true;scroll=0;rebuild();}).bounds(left+2*panelWidth/3+4,31,panelWidth/3-4,20).build());
        help=addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.help"),b->minecraft.setScreen(new QuickStartScreen(this))).bounds(width/2-65,55,130,20).build());
        rebuild();
    }
    private void tab(boolean guide){this.guide=guide;recovery=false;scroll=0;rebuild();}
    private void change(int delta){if(recovery){recoveryPage=Math.max(0,Math.min(snapshot.recovery().size()-1,recoveryPage+delta));scroll=0;rebuild();return;}if(guide){guidePage=Math.max(0,Math.min(EggGuide.PAGES-1,guidePage+delta));scroll=0;rebuild();return;}page=Math.max(0,Math.min(snapshot.entries().size()-1,page+delta));scroll=0;rebuild();}
    private void rebuild(){
        top=93+font.split(scope(),panelWidth-16).size()*10;bottom=guide?height-54:height-(recovery?75:99)-Math.max(2,font.split(status,panelWidth-16).size())*10;stay.visible=follow.visible=homeMode.visible=assignFurniture.visible=clearFurniture.visible=!guide && !recovery;returnMember.visible=recovery;
        var text=new ArrayList<Component>();
        if(guide){text.addAll(EggGuide.page(guidePage,snapshot.guideFlags(),snapshot.trailMask()));}
        else if(recovery){
            text.add(Component.translatable("screen.dudunka.recovery_note"));text.add(Component.empty());
            if(snapshot.recovery().isEmpty())text.add(Component.translatable("screen.dudunka.recovery_empty"));
            else{var e=snapshot.recovery().get(recoveryPage);text.add(e.name());text.add(Component.translatable("screen.dudunka.recovery_state."+e.state().name().toLowerCase(Locale.ROOT)));if(e.state()==FamilyAlbum.RecoveryState.LIVE){text.add(Component.literal(e.dimension()));text.add(Component.translatable("screen.dudunka.recovery_pos",e.pos().getX(),e.pos().getY(),e.pos().getZ()));}returnMember.setMessage(Component.translatable(e.state()==FamilyAlbum.RecoveryState.LIVE?"screen.dudunka.recovery_return":"screen.dudunka.recovery_carrier"));}
        }
        else if(snapshot.entries().isEmpty())text.add(Component.translatable("screen.dudunka.album_empty"));
        else {
            var e=snapshot.entries().get(page);
            text.add(e.name());
            text.add(Component.translatable("screen.dudunka.album_kind",Component.translatable("entity.dudunka."+e.kind().id)));
            text.add(Component.translatable("screen.dudunka.album_age",Component.translatable("stage.dudunka."+e.stage())));
            text.add(Component.translatable("screen.dudunka.album_trust",e.trust()));
            text.add(Component.translatable("screen.dudunka.album_mode",Component.translatable(e.staying()?"mode.dudunka.stay":e.homeMode()?"mode.dudunka.home":"mode.dudunka.follow")));
            text.add(Component.empty());
            text.add(Component.translatable("screen.dudunka.album_home",Component.translatable("home.dudunka."+e.home().state().name().toLowerCase(Locale.ROOT))));
            if(e.home().state()==FamilyAlbum.HomeState.READY || e.home().state()==FamilyAlbum.HomeState.INCOMPLETE || e.home().state()==FamilyAlbum.HomeState.UNSAFE) {
                String[] conditions={"roof","bed","chest","light","food"};
                for(int i=0;i<conditions.length;i++)text.add(Component.translatable("screen.dudunka.album_condition",Component.translatable("home.dudunka.condition."+conditions[i]),Component.translatable((e.home().flags()&(1<<i))!=0?"message.dudunka.yes":"message.dudunka.no")));
            }
            text.add(Component.empty());
            text.add(Component.translatable("screen.dudunka.album_furniture",Component.translatable("furniture.dudunka."+e.furniture().state().name().toLowerCase(Locale.ROOT))));
            if(e.furniture().state()!=FurnitureScenes.State.NONE){var pos=e.furniture().pos();text.add(Component.translatable("screen.dudunka.recovery_pos",pos.getX(),pos.getY(),pos.getZ()));}
            text.add(Component.translatable("screen.dudunka.album_activity",Component.translatable("activity.dudunka."+e.activity().name().toLowerCase(Locale.ROOT))));
            if(e.kind()==Kind.DUDUNKA && (e.activity()==Activity.DRAW || e.activity()==Activity.SHOW_DRAWING))text.add(Component.translatable("screen.dudunka.drawing",Component.translatable("drawing.dudunka."+e.variant())));
            if(e.kind()==Kind.MARUSYA && e.activity()==Activity.CURL)text.add(Component.translatable("screen.dudunka.rest_pose",Component.translatable("rest.dudunka."+e.variant())));
            if(e.kind()==Kind.SYUSYA)text.add(Component.translatable("screen.dudunka.feelers",Component.translatable("feelers.dudunka."+e.feelers().name().toLowerCase(Locale.ROOT))));
            text.add(Component.translatable("screen.dudunka.furniture_help"));
            text.add(Component.empty());text.add(Component.translatable("screen.dudunka.album_friends"));
            if(e.friends().isEmpty())text.add(Component.translatable("message.dudunka.friendship_empty"));
            for(var f:e.friends()) {
                String tier=f.score()>=70?"family":f.score()>=30?"close":f.score()>=10?"friends":"acquainted";
                text.add(Component.translatable("message.dudunka.friendship_row",f.name(),f.score(),Component.translatable("friendship.dudunka."+tier)));
            }
        }
        if(!guide){text.add(Component.empty());text.add(Component.translatable("screen.dudunka.album_snapshot"));}
        var wrapped=new ArrayList<FormattedCharSequence>();for(var line:text)wrapped.addAll(font.split(line,Math.max(40,panelWidth-24)));
        help.active=pending==0;
        returnMember.active=pending==0 && retryTicks==0 && !snapshot.recovery().isEmpty();
        lines=List.copyOf(wrapped);previous.active=guide?guidePage>0:recovery?pending==0 && recoveryPage>0:pending==0 && page>0;next.active=guide?guidePage+1<EggGuide.PAGES:recovery?pending==0 && recoveryPage+1<snapshot.recovery().size():pending==0 && page+1<snapshot.entries().size();scroll=Math.min(scroll,maxScroll());
        stay.active=pending==0 && !snapshot.entries().isEmpty() && !snapshot.entries().get(page).staying();
        follow.active=pending==0 && !snapshot.entries().isEmpty() && (snapshot.entries().get(page).staying() || snapshot.entries().get(page).homeMode());
        homeMode.active=pending==0 && !snapshot.entries().isEmpty() && !snapshot.entries().get(page).homeMode() && snapshot.entries().get(page).home().state()==FamilyAlbum.HomeState.READY;
        assignFurniture.active=pending==0 && !snapshot.entries().isEmpty();clearFurniture.active=assignFurniture.active && snapshot.entries().get(page).furniture().state()!=FurnitureScenes.State.NONE;
    }
    private Component scope(){if(recovery)return Component.translatable("screen.dudunka.recovery_scope",snapshot.recovery().size(),snapshot.recoveryTotal());if(guide)return Component.translatable("screen.dudunka.guide_scope");return Component.translatable("screen.dudunka.album_scope",snapshot.entries().size(),snapshot.total());}
    private int maxScroll(){return Math.max(0,lines.size()*12-(bottom-top));}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(amount*24)));return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        renderBackground(graphics);graphics.fill(left-4,8,left+panelWidth+4,height-36,0xEE29241D);
        graphics.drawCenteredString(font,title,width/2,16,0xFFE2AD);
        int subtitleY=85;for(var line:font.split(scope(),panelWidth-16)){graphics.drawString(font,line,width/2-font.width(line)/2,subtitleY,0xC7C0AE,false);subtitleY+=10;}
        graphics.enableScissor(left,top,left+panelWidth,bottom);
        int y=top-scroll;for(var line:lines){graphics.drawString(font,line,left+12,y,0xF3E8CF,false);y+=12;}
        graphics.disableScissor();
        if(maxScroll()>0)graphics.drawString(font,Component.literal(scroll<maxScroll()?"↓":"↑"),left+panelWidth-10,bottom-10,0xFFE2AD,false);
        if(guide)graphics.drawCenteredString(font,Component.literal((guidePage+1)+" / "+EggGuide.PAGES),width/2,height-42,0xC7C0AE);
        else if(recovery && !snapshot.recovery().isEmpty())graphics.drawCenteredString(font,Component.literal((recoveryPage+1)+" / "+snapshot.recovery().size()),width/2,bottom+4,0xC7C0AE);
        else if(!recovery && !snapshot.entries().isEmpty())graphics.drawCenteredString(font,Component.literal((page+1)+" / "+snapshot.entries().size()),width/2,bottom+4,0xC7C0AE);
        int statusY=bottom+16;if(!guide)for(var line:font.split(status,panelWidth-16)){graphics.drawString(font,line,width/2-font.width(line)/2,statusY,0xC7C0AE,false);statusY+=10;}
        super.render(graphics,mouseX,mouseY,partial);
    }
}
