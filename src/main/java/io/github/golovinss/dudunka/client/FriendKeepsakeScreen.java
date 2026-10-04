package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Reads only the held keepsake's bounded identity; no packets or progress mutations. */
public final class FriendKeepsakeScreen extends Screen {
    private final Kind kind;private final FriendRequests.Task task;
    private FriendKeepsakeScreen(Kind kind,FriendRequests.Task task){super(Component.translatable("keepsake.dudunka.name."+kind.id));this.kind=kind;this.task=task;}
    public static void open(Kind kind,FriendRequests.Task task){Minecraft.getInstance().setScreen(new FriendKeepsakeScreen(kind,task));}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-45,height-26,90,20).build());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        renderBackground(g);int w=Math.min(300,width-20),left=(width-w)/2;g.fill(left,12,left+w,height-34,0xFFF5ECD5);g.drawCenteredString(font,title,width/2,22,0x593D28);
        int top=42;
        if(kind==Kind.DUDUNKA){int size=Math.min(72,Math.max(16,height/4));g.blit(new ResourceLocation(DudunkaMod.ID,"textures/block/cozy_drawing.png"),width/2-size/2,top,0,0,size,size,size,size);top+=size+10;}
        var text=Component.translatable("keepsake.dudunka."+kind.id+"."+task.name().toLowerCase(java.util.Locale.ROOT));g.enableScissor(left+8,top,left+w-8,height-40);
        for(var line:font.split(text,w-24)){g.drawString(font,line,left+12,top,0x593D28,false);top+=12;}g.disableScissor();super.render(g,x,y,partial);
    }
}
