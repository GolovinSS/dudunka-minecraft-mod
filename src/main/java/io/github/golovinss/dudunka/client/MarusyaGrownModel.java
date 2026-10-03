package io.github.golovinss.dudunka.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Different Maine Coon silhouettes; body length grows inside geometry, render scale is fixed. */
public final class MarusyaGrownModel {
    private MarusyaGrownModel(){}
    private static void box(PartDefinition p,String name,int color,float x,float y,float z,float w,float h,float d,PartPose pose){
        p.addOrReplaceChild(name,CubeListBuilder.create().texOffs(color%4*64,color/4*64).addBox(x,y,z,w,h,d),pose);
    }
    public static LayerDefinition create(int stage){
        boolean adult=stage==2;MeshDefinition mesh=new MeshDefinition();var r=mesh.getRoot();
        float width=adult?5.8f:4.3f,front=adult?-4.4f:-3,back=adult?6.4f:5.1f;
        float leg=adult?4.8f:3.8f,top=adult?-9:-7.8f,bottom=-leg+.45f;
        var floor=PartPose.offset(0,24,0);
        box(r,"body",0,-width/2,top,front,width,bottom-top,back-front,floor);
        // Side tufts remain near-black; volume comes from the layered outline.
        for(int side:new int[]{-1,1})for(int i=0;i<(adult?4:2);i++){
            float x=side*(width/2+.12f);
            box(r,"flank_"+side+"_"+i,2,-.45f,-1.3f,-.7f,.9f,2.4f,1.4f,PartPose.offset(x,24+top+2,front+1.6f+i*(adult?2:2.8f)));
        }
        float collarW=adult?6.7f:4.7f,collarH=adult?5.8f:3.7f;
        box(r,"chest_fur",2,-collarW/2,-collarH/2,-1.25f,collarW,collarH,2.5f,PartPose.offset(0,24+top+2,front-.2f));
        var collar=r.getChild("chest_fur");
        for(int i=0;i<(adult?5:3);i++){
            float x=(i-(adult?2:1))*(adult?1.05f:1.15f);
            box(collar,"ruff"+i,i%2==0?2:1,-.65f,-.5f,-.4f,1.3f,adult?2.2f:1.25f,.8f,PartPose.offset(x,collarH/2-.65f,-1.2f));
        }
        float headW=adult?5.2f:4.8f,headH=adult?4.5f:4.7f,headD=adult?4.1f:3.8f;
        box(r,"head",0,-headW/2,-headH,-headD/2,headW,headH,headD,PartPose.offset(0,adult?14.5f:16.4f,front-.4f));
        var head=r.getChild("head");
        for(int i=0;i<2;i++){
            float side=i==0?-1:1;
            box(head,"ear"+i,0,-.8f,-(adult?2.6f:2),-.5f,1.6f,adult?2.7f:2.1f,1.1f,PartPose.offset(side*(headW/2-.65f),-headH+.2f,0));
            var ear=head.getChild("ear"+i);
            box(ear,"inner",9,-.48f,-(adult?2.25f:1.7f),-.58f,.96f,adult?1.8f:1.3f,.12f,PartPose.ZERO);
            box(ear,"taper",0,-.55f,-(adult?3.2f:2.45f),-.4f,1.1f,.8f,.85f,PartPose.ZERO);
            box(ear,"tuft",0,-.22f,-(adult?4:2.95f),-.22f,.44f,adult?1:.65f,.44f,PartPose.ZERO);
            box(head,"eye"+i,7,-.65f,-.65f,-.13f,1.3f,1.3f,.2f,PartPose.offset(side*1.2f,-headH*.48f,-headD/2-.04f));
            var eye=head.getChild("eye"+i);
            box(eye,"pupil",3,-.17f,-.55f,-.18f,.34f,1.1f,.1f,PartPose.ZERO);
            box(eye,"glint",11,-.3f,-.5f,-.22f,.22f,.24f,.08f,PartPose.ZERO);
            box(head,"cheek"+i,2,-.8f,-.7f,-.65f,1.6f,1.4f,1.3f,PartPose.offset(side*(headW/2-.1f),-1.2f,-headD/2+.25f));
            if(adult)box(head,"cheek_tuft"+i,1,-.6f,-.35f,-.4f,1.2f,.7f,.8f,PartPose.offset(side*(headW/2+.35f),-.55f,-headD/2+.35f));
            box(head,"muzzle"+i,2,-.62f,-.4f,-.4f,1.24f,.8f,.8f,PartPose.offset(side*.6f,-.55f,-headD/2-.18f));
        }
        box(head,"nose",1,-.34f,-.35f,-.18f,.68f,.4f,.36f,PartPose.offset(0,-.85f,-headD/2-.6f));
        for(int i=0;i<4;i++){
            float paw=adult?1.85f:1.25f,x=(i%2==0?-1:1)*width*.29f,z=i<2?front+.7f:back-.9f;
            box(r,"leg"+i,0,-paw*.4f,0,-paw*.4f,paw*.8f,leg,paw*.8f,PartPose.offset(x,24-leg,z));
            var limb=r.getChild("leg"+i);
            box(limb,"paw",0,-paw/2,leg-.85f,-paw*.65f,paw,.85f,paw*1.25f,PartPose.ZERO);
            for(int toe=0;toe<3;toe++)box(limb,"toe"+toe,1,-paw*.13f,leg-.7f,-paw*.73f,paw*.26f,.7f,.2f,PartPose.offset((toe-1)*paw*.3f,0,0));
            if(adult)box(limb,"leg_fur",2,-paw*.48f,.35f,-paw*.45f,paw*.96f,1.3f,paw*.9f,PartPose.ZERO);
        }
        float thick=adult?2.35f:1.6f;
        box(r,"tail",0,-thick/2,-.6f,-.2f,thick,1.6f,adult?3:2.3f,PartPose.offset(0,24+top+1.8f,back-.5f));
        var tail=r.getChild("tail");
        box(tail,"middle",2,-thick*.55f,-2,adult?2:1.4f,thick*1.1f,2.2f,adult?3:2.4f,PartPose.ZERO);
        box(tail,"upper",0,-thick*.53f,adult?-3.8f:-3,adult?4.1f:3,thick*1.06f,2.3f,adult?2.5f:1.8f,PartPose.ZERO);
        box(tail,"tip",2,-thick*.48f,adult?-5.5f:-4.2f,adult?5.7f:4.1f,thick*.96f,adult?2.7f:2.1f,adult?2:1.6f,PartPose.ZERO);
        return LayerDefinition.create(mesh,256,256);
    }
}
