package io.github.golovinss.dudunka.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Age-specific body, feelers and stepped Achatina cone. Render scale stays 0.55. */
public final class SyusyaGrownModel {
    private SyusyaGrownModel(){}
    private static void box(PartDefinition p,String name,int color,float x,float y,float z,float w,float h,float d,PartPose pose){
        p.addOrReplaceChild(name,CubeListBuilder.create().texOffs(color%4*64,color/4*64).addBox(x,y,z,w,h,d),pose);
    }
    public static LayerDefinition create(int stage){
        boolean adult=stage==2;MeshDefinition mesh=new MeshDefinition();var r=mesh.getRoot();
        float width=adult?4.5f:3.6f,front=adult?-6:-4.7f,back=adult?6.9f:4.6f,tailEnd=adult?8.46f:6.04f;
        var floor=PartPose.offset(0,24,0);
        box(r,"body",0,-width/2,-.85f,front,width,.85f,back-front,floor);
        box(r,"body_ridge",0,-width*.4f,-1.3f,front+.35f,width*.8f,.5f,back-front-.7f,floor);
        box(r,"tail",0,-width*.3f,-.55f,back-.3f,width*.6f,.55f,tailEnd-back+.3f,floor);
        box(r,"tail_tip",0,-width*.15f,-.3f,tailEnd-.6f,width*.3f,.3f,.6f,floor);
        float hz=adult?-5.15f:-4.05f,neck=adult?1.6f:1.3f;
        box(r,"neck",0,-width*.32f,-neck-.4f,hz-.55f,width*.64f,neck+.4f,1.8f,floor);
        box(r,"head",0,-1.35f,-1.1f,-1.3f,2.7f,1.2f,2.2f,PartPose.offset(0,24-neck,hz));
        var head=r.getChild("head");float stalk=adult?4.2f:2.8f;
        for(int i=0;i<2;i++){
            float side=i==0?-1:1;
            box(head,"stalk"+i,0,-.19f,-stalk-1,-.18f,.38f,stalk,.38f,PartPose.offset(side*.95f,0,-.75f));
            // Named stalk joints remain compatible with the existing gentle sway animation.
            var feeler=head.getChild("stalk"+i);
            box(feeler,"eye_cap",0,-.32f,-stalk-1.3f,-.32f,.64f,.55f,.64f,PartPose.ZERO);
            box(feeler,"pupil",3,-.18f,-stalk-1.2f,-.36f,.36f,.36f,.1f,PartPose.ZERO);
            box(feeler,"glint",11,-.12f,-stalk-1.17f,-.39f,.1f,.1f,.06f,PartPose.ZERO);
            box(head,"lower_feeler"+i,0,side<0?-(adult?.9f:.65f)+.15f:-.15f,-.15f,-.18f,adult?.9f:.65f,.3f,.36f,PartPose.offset(side*1.2f,-.35f,-.9f));
        }
        box(head,"mouth",14,-.35f,-.25f,-1.35f,.7f,.18f,.1f,PartPose.ZERO);
        // Rounded main whorl: nested horizontal slices, all bands share the same shell parent.
        box(r,"shell",5,-(adult?2.65f:2.1f),-2.5f,adult?-2.7f:-2.1f,adult?5.3f:4.2f,1.5f,adult?6.8f:5.1f,floor);
        var shell=r.getChild("shell");
        int tiers=adult?5:4;
        for(int i=0;i<tiers;i++){
            float half=(adult?2.9f:2.25f)-i*.31f;
            float low=-2.3f-i*.8f;
            box(shell,"whorl"+i,i%2==0?5:1,-half,low,(adult?-2.9f:-2.1f)+i*.28f,half*2,.85f,adult?6.6f-i*.7f:5-i*.55f,PartPose.ZERO);
        }
        // The adult rear cone has more whorls and a distinctly longer rising apex.
        int cones=adult?5:2;
        for(int i=0;i<cones;i++){
            float half=(adult?1.85f:1.3f)-i*(adult?.27f:.35f);
            float z=(adult?1.5f:1.8f)+i*(adult?.7f:.55f);
            float y=(adult?-4.5f:-3.8f)-i*.5f;
            box(shell,"cone"+i,i%2==0?1:5,-half,y,z,half*2,1.5f,adult?1.5f:1.1f,PartPose.ZERO);
        }
        // Readable spiral relief on both sides, instead of a flat square on the shell.
        for(int side:new int[]{-1,1}){
            float x=side*(adult?2.92f:2.27f),cz=adult?.15f:.1f,cy=adult?-3.1f:-2.9f;
            float[][] segments={{-.9f,-.9f,1.8f,.28f},{.65f,-.8f,.28f,1.6f},{-.6f,.55f,1.5f,.28f},{-.65f,-.4f,.28f,1.2f},{-.6f,-.4f,.95f,.28f},{.1f,-.4f,.28f,.75f}};
            for(int i=0;i<segments.length;i++){
                float[] v=segments[i];box(shell,"spiral_"+side+"_"+i,12,-.12f,v[1],v[0],.24f,v[3],v[2],PartPose.offset(x,cy,cz));
            }
        }
        return LayerDefinition.create(mesh,256,256);
    }
}
