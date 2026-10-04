package io.github.golovinss.dudunka.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Five short client-only pages; returning keeps the original album and selection. */
public final class QuickStartScreen extends Screen {
    private final FamilyAlbumScreen album;
    private int page,scroll,left,panelWidth,bottom;
    private List<FormattedCharSequence> lines=List.of();
    private Button previous,next;
    public QuickStartScreen(FamilyAlbumScreen album){super(Component.translatable("screen.dudunka.help"));this.album=album;}
    @Override protected void init(){
        panelWidth=Math.min(340,width-16);left=(width-panelWidth)/2;bottom=height-54;
        previous=addRenderableWidget(Button.builder(Component.literal("<"),b->change(-1)).bounds(left,height-30,40,20).build());
        next=addRenderableWidget(Button.builder(Component.literal(">"),b->change(1)).bounds(left+panelWidth-40,height-30,40,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.dudunka.help_back"),b->onClose()).bounds(width/2-65,height-30,130,20).build());
        rebuild();
    }
    private void change(int delta){page=Math.max(0,Math.min(4,page+delta));scroll=0;rebuild();}
    private void rebuild(){lines=font.split(Component.translatable("screen.dudunka.help.page."+page),Math.max(40,panelWidth-24));previous.active=page>0;next.active=page<4;scroll=Math.min(scroll,maxScroll());}
    private int maxScroll(){return Math.max(0,lines.size()*12-(bottom-40));}
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(amount*24)));return true;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){album.tick();}
    @Override public void onClose(){minecraft.setScreen(album);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        renderBackground(graphics);graphics.fill(left-4,8,left+panelWidth+4,height-36,0xEE29241D);
        graphics.drawCenteredString(font,title,width/2,16,0xFFE2AD);
        graphics.enableScissor(left,40,left+panelWidth,bottom);
        int y=40-scroll;for(var line:lines){graphics.drawString(font,line,left+12,y,0xF3E8CF,false);y+=12;}graphics.disableScissor();
        if(maxScroll()>0)graphics.drawString(font,Component.literal(scroll<maxScroll()?"↓":"↑"),left+panelWidth-10,bottom-10,0xFFE2AD,false);
        graphics.drawCenteredString(font,Component.literal((page+1)+" / 5"),width/2,height-42,0xC7C0AE);
        super.render(graphics,mouseX,mouseY,partial);
    }
}
