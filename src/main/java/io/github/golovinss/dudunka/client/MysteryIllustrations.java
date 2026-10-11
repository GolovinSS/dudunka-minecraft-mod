package io.github.golovinss.dudunka.client;

import net.minecraft.client.gui.GuiGraphics;

/** Small album drawings made with native GUI primitives; no image dependency or external assets. */
final class MysteryIllustrations {
    private MysteryIllustrations() {}
    static void draw(GuiGraphics g,int x,int y,int chapter) {
        g.fill(x-65,y+2,x+65,y+58,0xFFF5ECD5);
        if(chapter==1) {
            friend(g,x-35,y+28,0xFFF29AD0);friend(g,x,y+28,0xFFAD81D7);friend(g,x+35,y+28,0xFF68CABC);
        } else if(chapter==4) {
            mushroom(g,x-35,y+18,0xFFF59BCF);mushroom(g,x,y+10,0xFFD57B86);
            friend(g,x+35,y+31,0xFF91DCBE);
            g.fill(x-4,y+6,x+4,y+10,0xFF78DCC9);g.fill(x+23,y+12,x+27,y+16,0xFFEDBC59);
        } else {
            g.fill(x-45,y+44,x+45,y+49,0xFF6B9753);
            if(chapter==2) {
                g.fill(x-52,y+18,x-46,y+44,0xFF846142);g.fill(x-62,y+6,x-36,y+22,0xFF6A9858);
                for(int i=0;i<3;i++)g.fill(x-32+i*9,y+40-i*3,x-28+i*9,y+43-i*3,0xFFB09BCF);
            }
            mushroom(g,x-20,y+25,0xFFE298BA);mushroom(g,x+20,y+25,0xFFB58CD0);
            g.fill(x-25,y+11,x+25,y+18,0xFFB58CD0);g.fill(x-22,y+8,x+22,y+12,0xFFE298BA);
            g.fill(x-16,y+12,x-10,y+16,0xFFFDF3E0);g.fill(x+9,y+11,x+15,y+15,0xFFFDF3E0);
            g.fill(x-24,y+34,x-19,y+43,0xFF759655);
            if(chapter==3) {
                g.fill(x-12,y+18,x+12,y+43,0x88E28ABA);
                for(int i=0;i<4;i++)g.fill(x-32+i*21,y+4+(i%2)*18,x-29+i*21,y+7+(i%2)*18,0xFF68CCBC);
            } else g.fill(x-2,y+37,x+3,y+43,0xFF68CCBC);
        }
    }
    private static void mushroom(GuiGraphics g,int x,int y,int color) {
        g.fill(x-4,y+7,x+4,y+25,0xFFD8CAB0);g.fill(x-12,y,x+12,y+8,color);
        g.fill(x-8,y-4,x+8,y,color);g.fill(x-7,y+1,x-3,y+4,0xFFFDF3E0);
    }
    private static void friend(GuiGraphics g,int x,int y,int color) {
        g.fill(x-9,y-6,x+9,y+15,color);g.fill(x-6,y-10,x+6,y-6,color);
        g.fill(x-5,y-3,x-3,y,0xFF433A33);g.fill(x+3,y-3,x+5,y,0xFF433A33);
    }
}
