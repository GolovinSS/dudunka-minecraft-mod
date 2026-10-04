package io.github.golovinss.dudunka.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Separate age silhouettes, normalized to the baby's 10.7 model-unit height before render scaling. */
public final class DudunkaGrownModel {
    private final float unit;
    private DudunkaGrownModel(float height){unit=10.7f/height;}
    private void box(PartDefinition parent,String name,int color,float x,float y,float z,float w,float h,float d,float px,float py,float pz){
        parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(color%4*64,color/4*64)
            .addBox(x*unit,y*unit,z*unit,w*unit,h*unit,d*unit),PartPose.offset(px*unit,py*unit,pz*unit));
    }
    public static LayerDefinition create(int stage){
        boolean adult=stage==2;
        float leg=adult?6:4.2f, torso=adult?5:4, headH=adult?5:5.5f, bun=1.6f;
        var maker=new DudunkaGrownModel(leg+torso+headH+bun);
        MeshDefinition mesh=new MeshDefinition();var r=mesh.getRoot();
        // Offset keeps the feet at y=24, matching the unchanged baby and renderer's floor origin.
        float floor=24/maker.unit, shoulder=floor-leg-torso, waist=floor-leg;
        float width=adult?4.8f:4.2f, hw=adult?5.1f:5.5f, hd=adult?4.3f:4.6f;
        maker.box(r,"body",10,-width/2,0,-1.4f,width,torso,2.8f,0,shoulder,0);
        maker.box(r,"bib",2,-width*.34f,.8f,-1.58f,width*.68f,torso-.8f,.25f,0,shoulder,0);
        maker.box(r,"shorts",2,-width/2,-1.4f,-1.5f,width,1.5f,3,0,waist,0);
        for(int i=0;i<2;i++){
            float x=(i==0?-1:1)*width*.25f;
            maker.box(r,"leg"+i,0,-.82f,0,-.8f,1.64f,leg,1.6f,x,waist,0);
            var limb=r.getChild("leg"+i);
            maker.box(limb,"sock",11,-.85f,leg*.45f,-.83f,1.7f,leg*.4f,1.66f,0,0,0);
            maker.box(limb,"sock_band",2,-.87f,leg*.56f,-.85f,1.74f,.4f,1.7f,0,0,0);
            maker.box(limb,"boot",4,-1,leg-1.1f,-1.35f,2,1.1f,2.3f,0,0,0);
            maker.box(limb,"boot_cuff",4,-1.04f,leg-1.5f,-.96f,2.08f,.45f,1.92f,0,0,0);
            float arm=torso+1.3f;
            maker.box(r,"arm"+i,10,-.72f,0,-.72f,1.44f,arm-1,1.44f,(i==0?-1:1)*(width/2+.74f),shoulder+.3f,0);
            var a=r.getChild("arm"+i);
            maker.box(a,"hand",0,-.68f,arm-1,-.68f,1.36f,1,1.36f,0,0,0);
            maker.box(a,"cuff",11,-.8f,arm-1.4f,-.8f,1.6f,.45f,1.6f,0,0,0);
            if(i==1)maker.box(a,"drawing_sheet",11,-1.7f,0,-.85f,3.4f,2.5f,.12f,0,arm-.5f,0);
            if(i==1)DrawingMotifs.add(a.getChild("drawing_sheet"),3.4f*maker.unit,0,-.92f*maker.unit,false);
            if(i==1)maker.box(a,"ring",7,-.75f,arm-.6f,-.75f,1.5f,.24f,1.5f,0,0,0);
            maker.box(r,"strap"+i,5,-.27f,0,-1.65f,.54f,torso, .24f,x,shoulder,0);
            maker.box(r,"buckle"+i,7,-.35f,1.1f,-1.82f,.7f,.55f,.25f,x,shoulder,0);
            maker.box(r,"pocket"+i,12,-.62f,torso-1.25f,-1.8f,1.24f,1,.25f,x,shoulder,0);
        }
        float packH=adult?4.7f:3.3f,packW=adult?3.8f:3.4f;
        maker.box(r,"backpack",5,-packW/2,.35f,1.4f,packW,packH,1.7f,0,shoulder,0);
        var pack=r.getChild("backpack");
        maker.box(pack,"flap",5,-packW/2-.1f,.3f,3.07f,packW+.2f,.9f,.25f,0,0,0);
        maker.box(pack,"front_pocket",5,-packW*.3f,packH*.5f,3.06f,packW*.6f,packH*.4f,.45f,0,0,0);
        if(adult)for(int i=0;i<2;i++)maker.box(pack,"side_pocket"+i,5,-.4f,2.2f,1.9f,.8f,1.8f,1.1f,(i==0?-1:1)*(packW/2+.2f),0,0);
        maker.flower(pack,"bag_flower",0,packH*.7f,3.56f);
        maker.box(r,"necklace",7,-.1f,.2f,-1.77f,.2f,1.35f,.2f,0,shoulder,0);
        maker.flower(r,"pendant",0,shoulder+1.6f,-1.9f);
        maker.box(r,"head",0,-hw/2,-headH,-hd/2,hw,headH,hd,0,shoulder,0);
        var h=r.getChild("head");
        maker.box(h,"hair",1,-hw/2-.1f,-headH,-hd/2-.1f,hw+.2f,1.2f,hd+.2f,0,0,0);
        maker.box(h,"hair_back",1,-hw/2-.12f,-headH+1,hd/2-.6f,hw+.24f,headH-.5f,1.1f,0,0,0);
        maker.box(h,"bun",1,-1.3f,-headH-bun,-.2f,2.6f,bun,2.4f,0,0,0);
        for(int i=0;i<2;i++){
            float side=i==0?-1:1;
            maker.box(h,"ear"+i,0,-.3f,-.5f,-.3f,.6f,1,.6f,side*(hw/2+.12f),-headH*.42f,0);
            maker.box(h,"bang"+i,13,-.6f,-1,-.2f,1.2f,1.9f,.6f,side*1.45f,-headH+.85f,-hd/2-.12f);
            for(int curl=0;curl<3;curl++)maker.box(h,"curl"+i+"_"+curl,curl%2==0?1:13,-.55f,-.6f,-.45f,1.1f,1.2f,.9f,side*(hw/2+.08f),-headH+1.7f+curl*1.25f,.7f);
            float x=(i==0?-1:1)*(hw/2-.4f);
            maker.box(h,"side_hair"+i,1,-.55f,-headH+.6f,-hd/2-.15f,1.1f,headH-.3f,1.2f,x,0,0);
            float gx=i==0?-2.13f:.23f,gy=-headH*.62f,gz=-hd/2-.26f;
            maker.box(h,"eye_white"+i,11,gx+.45f,gy+.25f,gz+.09f,.95f,1.05f,.12f,0,0,0);
            maker.box(h,"eye"+i,15,gx+.7f,gy+.5f,gz-.02f,.55f,.7f,.14f,0,0,0);
            maker.box(h,"pupil"+i,3,gx+.87f,gy+.52f,gz-.05f,.25f,.5f,.1f,0,0,0);
            maker.box(h,"glint"+i,11,gx+.87f,gy+.52f,gz-.09f,.13f,.16f,.1f,0,0,0);
            maker.box(h,"glass_top"+i,3,gx,gy,gz-.12f,1.9f,.16f,.18f,0,0,0);
            maker.box(h,"glass_bottom"+i,3,gx,gy+1.5f,gz-.12f,1.9f,.16f,.18f,0,0,0);
            maker.box(h,"glass_left"+i,3,gx,gy,gz-.12f,.16f,1.66f,.18f,0,0,0);
            maker.box(h,"glass_right"+i,3,gx+1.74f,gy,gz-.12f,.16f,1.66f,.18f,0,0,0);
        }
        maker.box(h,"bridge",3,-.24f,-headH*.62f+.7f,-hd/2-.4f,.48f,.16f,.18f,0,0,0);
        maker.box(h,"smile_left",14,-.48f,-.69f,-hd/2-.11f,.16f,.23f,.12f,0,0,0);
        maker.box(h,"smile_right",14,.32f,-.69f,-hd/2-.11f,.16f,.23f,.12f,0,0,0);
        maker.box(h,"mouth",14,-.35f,-.55f,-hd/2-.11f,.7f,.16f,.12f,0,0,0);
        maker.flower(h,"hair_flower",-hw/2+.5f,-headH+.65f,-hd/2-.25f);
        return LayerDefinition.create(mesh,256,256);
    }
    private void flower(PartDefinition p,String name,float x,float y,float z){
        box(p,name+"_center",7,-.18f,-.18f,0,.36f,.36f,.2f,x,y,z);
        for(int i=0;i<4;i++)box(p,name+"_petal"+i,11,-.2f,-.2f,0,.4f,.4f,.16f,x+(i==0?-.38f:i==1?.38f:0),y+(i==2?-.38f:i==3?.38f:0),z);
    }
}
