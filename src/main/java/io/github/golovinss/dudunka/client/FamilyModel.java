package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import java.util.*;

public class FamilyModel extends EntityModel<Companion> {
    public static ModelLayerLocation layer(Kind k){return new ModelLayerLocation(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID,k.id),"main");}
    private final ModelPart root,head;
    private final List<ModelPart> legs=new ArrayList<>();
    public FamilyModel(ModelPart root){this.root=root;this.head=root.getChild("head");for(String n:List.of("leg0","leg1","leg2","leg3"))if(root.hasChild(n))legs.add(root.getChild(n));}
    private static void cube(PartDefinition parent,String name,int color,float x,float y,float z,float w,float h,float d,PartPose pose){
        parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs((color%4)*64,(color/4)*64).addBox(x,y,z,w,h,d),pose);
    }
    public static ModelLayerLocation layer(Kind k,int stage){
        return stage==0 || !hasAgeModels(k)?layer(k):new ModelLayerLocation(new net.minecraft.resources.ResourceLocation(DudunkaMod.ID,k.id+"_"+stage),"main");
    }
    public static boolean hasAgeModels(Kind k){return true;}
    public static LayerDefinition create(Kind k,int stage){
        if(stage>0 && k==Kind.DUDUNKA)return DudunkaGrownModel.create(stage);
        if(stage>0 && k==Kind.MARUSYA)return MarusyaGrownModel.create(stage);
        if(stage>0 && k==Kind.SYUSYA)return SyusyaGrownModel.create(stage);
        return create(k);
    }
    public static LayerDefinition create(Kind k){
        MeshDefinition mesh=new MeshDefinition();PartDefinition r=mesh.getRoot();
        if(k==Kind.DUDUNKA){
            cube(r,"body",2,-1.8f,-5.8f,-1.2f,3.6f,3.8f,2.4f,PartPose.offset(0,24,0));
            cube(r,"backpack",5,-1.5f,-5.5f,1.2f,3,3,1.4f,PartPose.offset(0,24,0));
            cube(r,"leg0",4,-.7f,0,-.8f,1.4f,2,1.6f,PartPose.offset(-.9f,22,0));
            cube(r,"leg1",4,-.7f,0,-.8f,1.4f,2,1.6f,PartPose.offset(.9f,22,0));
            cube(r,"arm0",0,-.6f,0,-.6f,1.2f,3.3f,1.2f,PartPose.offset(-2.4f,18.4f,0));
            cube(r,"arm1",0,-.6f,0,-.6f,1.2f,3.3f,1.2f,PartPose.offset(2.4f,18.4f,0));
            cube(r.getChild("arm1"),"drawing_sheet",6,-1.2f,2.5f,-.85f,2.4f,2,.12f,PartPose.ZERO);
            cube(r.getChild("arm1").getChild("drawing_sheet"),"flower",2,-.4f,3,-.92f,.8f,.7f,.05f,PartPose.ZERO);
            cube(r.getChild("arm1").getChild("drawing_sheet"),"stem",6,-.1f,3.5f,-.92f,.2f,.5f,.05f,PartPose.ZERO);
            cube(r.getChild("arm1"),"ring",7,-.72f,2.6f,-.72f,1.44f,.3f,1.44f,PartPose.ZERO);
            cube(r,"head",0,-2.3f,-4.6f,-2.1f,4.6f,4.6f,4.2f,PartPose.offset(0,18,0));
            PartDefinition h=r.getChild("head");
            cube(h,"hair",1,-2.4f,-4.7f,-2.2f,4.8f,1.4f,4.4f,PartPose.ZERO);
            cube(h,"hair_back",1,-2.35f,-3.5f,1.5f,4.7f,4,1,PartPose.ZERO);
            cube(h,"eye0",3,-1.35f,-2.7f,-2.18f,.4f,.6f,.1f,PartPose.ZERO);
            cube(h,"eye1",3,.95f,-2.7f,-2.18f,.4f,.6f,.1f,PartPose.ZERO);
            for(int a=0;a<2;a++){
                float x=a==0?-1.9f:.25f;
                cube(h,"glass_top"+a,3,x,-3.1f,-2.35f,1.65f,.18f,.18f,PartPose.ZERO);
                cube(h,"glass_bottom"+a,3,x,-1.75f,-2.35f,1.65f,.18f,.18f,PartPose.ZERO);
                cube(h,"glass_left"+a,3,x,-3.1f,-2.35f,.18f,1.53f,.18f,PartPose.ZERO);
                cube(h,"glass_right"+a,3,x+1.47f,-3.1f,-2.35f,.18f,1.53f,.18f,PartPose.ZERO);
            }
            cube(h,"bridge",3,-.3f,-2.7f,-2.35f,.6f,.18f,.18f,PartPose.ZERO);
        }else if(k==Kind.MARUSYA){
            cube(r,"body",3,-2,-6,-2,4,4,6,PartPose.offset(0,24,0));
            cube(r,"chest_fur",8,-2.3f,-6.7f,-2.7f,4.6f,4.6f,2,PartPose.offset(0,24,0));
            cube(r,"head",3,-2,-3.3f,-2,4,3.3f,3.4f,PartPose.offset(0,17,-2));
            var h=r.getChild("head");
            cube(h,"ear0",3,-2,-5,-.5f,1.4f,2,1,PartPose.ZERO);
            cube(h,"ear1",3,.6f,-5,-.5f,1.4f,2,1,PartPose.ZERO);
            cube(h,"eye0",7,-1.5f,-2,-2.12f,1,.65f,.15f,PartPose.ZERO);
            cube(h,"eye1",7,.5f,-2,-2.12f,1,.65f,.15f,PartPose.ZERO);
            cube(h,"nose",9,-.35f,-1,-2.2f,.7f,.4f,.3f,PartPose.ZERO);
            for(int i=0;i<4;i++)cube(r,"leg"+i,3,-.6f,0,-.6f,1.2f,3,1.2f,PartPose.offset(i%2==0?-1.4f:1.4f,21,i<2?-1:3));
            cube(r,"tail",3,-1,-1,0,2,2,7,PartPose.offsetAndRotation(0,19,3,-.65f,0,0));
        }else{
            cube(r,"body",6,-2,-1,-4,4,1,8,PartPose.offset(0,24,0));
            cube(r,"shell",5,-2.1f,-4.5f,-1.2f,4.2f,4,4.5f,PartPose.offset(0,24,0));
            cube(r,"shell_spiral",1,-2.22f,-3.5f,.1f,.18f,2,2,PartPose.offset(0,24,0));
            cube(r,"head",6,-1.5f,-1.4f,-1.8f,3,1.4f,2,PartPose.offset(0,23.7f,-3));
            var h=r.getChild("head");
            cube(h,"stalk0",6,-1.1f,-3,-1, .3f,2,.3f,PartPose.ZERO);
            cube(h,"stalk1",6,.8f,-3,-1,.3f,2,.3f,PartPose.ZERO);
            cube(h,"eye0",3,-1.2f,-3.2f,-1.1f,.5f,.5f,.5f,PartPose.ZERO);
            cube(h,"eye1",3,.7f,-3.2f,-1.1f,.5f,.5f,.5f,PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,256,256);
    }
    @Override public void setupAnim(Companion e,float limbSwing,float amount,float age,float yaw,float pitch){
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot=yaw*Mth.DEG_TO_RAD;head.xRot=pitch*Mth.DEG_TO_RAD;
        if(e.activity() != Activity.SIT && e.activity() != Activity.CAMP_REST && e.activity() != Activity.SLEEP && e.activity() != Activity.CURL && e.activity() != Activity.STRETCH && e.activity() != Activity.SCRATCH && e.activity() != Activity.DRAW && e.activity() != Activity.SHOW_DRAWING && e.activity() != Activity.WAIT_FOR_SYUSYA)
            for(int i=0;i<legs.size();i++)legs.get(i).xRot=Mth.cos(limbSwing*(amount > .4f ? 1.1f : .8f)+(i%2==0?0:Mth.PI))*amount;
        AnimationPoses.apply(root, e.kind, e.activity(), age);
        if(e.kind == Kind.DUDUNKA && amount > .4f && (e.activity() == Activity.IDLE || e.activity() == Activity.CAKE_RUN)) root.y += Math.abs(Mth.sin(limbSwing * 1.1f)) * amount * .4f;
    }
    @Override public void renderToBuffer(PoseStack p,VertexConsumer v,int light,int overlay,float red,float green,float blue,float alpha){root.render(p,v,light,overlay,red,green,blue,alpha);}
}
