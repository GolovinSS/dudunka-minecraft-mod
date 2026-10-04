package io.github.golovinss.dudunka.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Generated from the same 12x8 motifs as the table's textures; regenerate with tools/generate_character_moments.py. */
public final class DrawingMotifs {
    private DrawingMotifs(){}
    private static final String[][] PIXELS={
        {".....PP.....","...PPPPPP...",".....YY.....","...PPPPPP...",".....PP.....",".....G......","...GGGGG....",".....G......"},
        {"...K....K...","...KK..KK...","..KKKKKKKK..","..KYYKKYYK..","..KKKKKKKK..","...KPPPPK...","....KKKK....","............"},
        {".....BBBB...","....BTTTTB..","...BTBBTTB..","...BTBTBTB..","...BTTBBTB..","....BBBBB...","..TTTTTTTT..",".TTTTTTTTTT."},
    };
    public static void add(PartDefinition sheet,float width,float y,float z,boolean baby){
        float unit=width/12;
        for(int v=0;v<3;v++){
            var group=sheet.addOrReplaceChild("motif"+v,CubeListBuilder.create(),PartPose.ZERO);
            for(int row=0;row<8;row++)for(int col=0;col<12;col++){
                char c=PIXELS[v][row].charAt(col);if(c=='.')continue;
                int tile=switch(c){case 'P'->baby?2:9;case 'G'->baby?6:12;case 'K'->3;case 'Y'->7;case 'T'->6;default->5;};
                group.addOrReplaceChild("pixel_"+row+"_"+col,CubeListBuilder.create().texOffs(tile%4*64,tile/4*64)
                    .addBox(-width/2+col*unit,y+.08f+row*unit,z,unit,unit,.035f),PartPose.ZERO);
            }
        }
    }
}
