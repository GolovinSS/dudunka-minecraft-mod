package io.github.golovinss.dudunka.client;

import com.mojang.blaze3d.vertex.*;
import io.github.golovinss.dudunka.WiseFlyAgaric;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original code-native elder: rigid articulated voxels, one shared face/cap pivot. */
public class WiseFlyAgaricModel extends EntityModel<WiseFlyAgaric> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("dudunka","wise_fly_agaric"),"main");
    private final ModelPart root, torso, head, cap, leftArm, rightArm, leftLeg, rightLeg, book;
    private final Map<WiseFlyAgaric, Look> looks = new WeakHashMap<>();
    public WiseFlyAgaricModel(ModelPart root) {
        this.root=root;torso=root.getChild("torso");head=torso.getChild("head");cap=head.getChild("cap");
        leftArm=torso.getChild("left_arm");rightArm=torso.getChild("right_arm");book=torso.getChild("book");
        leftLeg=root.getChild("left_leg");rightLeg=root.getChild("right_leg");
    }
    private static PartDefinition box(PartDefinition p,String name,int u,int v,float x,float y,float z,float w,float h,float d) {
        return p.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.ZERO);
    }
    public static LayerDefinition create() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var torso=root.addOrReplaceChild("torso",CubeListBuilder.create(),PartPose.offset(0,19,0));
        box(torso,"stem",0,0,-2.7f,-4,-2,5.4f,7,4);
        box(torso,"hem",0,0,-3,1.5f,-2.2f,6,1.4f,4.4f);
        box(torso,"cloak",40,32,-3.1f,-4.2f,.5f,6.2f,6.4f,2);
        box(torso,"belt",80,32,-3.05f,.4f,-2.28f,6.1f,.85f,4.7f);
        box(torso,"buckle",112,48,-.65f,.22f,-2.48f,1.3f,1.2f,.35f);
        box(torso,"buckle_inset",80,32,-.32f,.5f,-2.68f,.64f,.6f,.12f);
        box(torso,"pouch",80,0,-4,.4f,-1.5f,1.7f,2.3f,2);
        box(torso,"pouch_flap",80,32,-4.1f,.3f,-1.68f,1.9f,.9f,.3f);
        box(torso,"pouch_clasp",112,48,-3.45f,.8f,-1.85f,.5f,.5f,.2f);
        // Layered pointed leaves, rather than a rectangular cape silhouette.
        for(int i=0;i<7;i++) {
            float x=-3.25f+i*.9f;
            box(torso,"leaf"+i,40,32,x,-3.8f-(i%2)*.4f,2.25f,.95f,4.8f+(i%3)*.5f,.55f);
            box(torso,"leaf_tip"+i,40,48,x+.2f,1+(i%3)*.5f,2.3f,.55f,.9f,.4f);
        }
        for(int side:new int[]{-1,1}) {
            box(torso,"shoulder_leaf"+side,40,48,side<0?-3.7f:2.4f,-4.2f,-1.9f,1.3f,1.6f,3.8f);
            var arm=torso.addOrReplaceChild(side<0?"left_arm":"right_arm",CubeListBuilder.create().texOffs(0,32)
                    .addBox(-.7f,0,-.8f,1.4f,3.6f,1.6f),PartPose.offset(side*3.25f,-3.2f,-.2f));
            box(arm,"cuff",0,32,-.85f,2,-.95f,1.7f,.8f,1.9f);
            box(arm,"hand",0,0,-.8f,2.7f,-1,1.6f,1.2f,1.8f);
            var leg=root.addOrReplaceChild(side<0?"left_leg":"right_leg",CubeListBuilder.create().texOffs(0,0)
                    .addBox(-.65f,0,-.6f,1.3f,1.5f,1.2f),PartPose.offset(side*1.5f,21.5f,0));
            box(leg,"boot",80,0,-1,1,-1.4f,2,1.5f,2.4f);
            box(leg,"sole",80,32,-1.05f,2.12f,-1.45f,2.1f,.38f,2.5f);
        }
        var staff=torso.getChild("right_arm").addOrReplaceChild("staff",CubeListBuilder.create(),PartPose.offset(1.25f,3.1f,-.55f));
        box(staff,"shaft",80,0,-.35f,-9,-.35f,.7f,14,.7f);
        box(staff,"crook_top",80,0,-.35f,-9.8f,-.35f,2.3f,.8f,.7f);
        box(staff,"crook_side",80,0,1.4f,-9.1f,-.35f,.65f,1.8f,.7f);
        box(staff,"crook_end",80,0,.65f,-7.75f,-.35f,1.1f,.6f,.7f);
        box(staff,"lantern_hook",112,48,1.55f,-7.2f,-.2f,.25f,1.1f,.3f);
        box(staff,"lantern_glass",112,32,1,-6.2f,-.6f,1.4f,1.8f,1.2f);
        for(int side:new int[]{-1,1})box(staff,"lantern_frame"+side,80,32,side<0?.9f:2.25f,-6.25f,-.65f,.25f,2,.25f);
        box(staff,"lantern_roof",80,32,.7f,-6.5f,-.8f,2,.4f,1.6f);
        box(staff,"lantern_base",80,32,.85f,-4.45f,-.7f,1.7f,.35f,1.4f);
        var book=torso.addOrReplaceChild("book",CubeListBuilder.create(),PartPose.offsetAndRotation(0,-1.5f,-3.3f,.35f,0,0));
        for(int side:new int[]{-1,1}) {
            var page=book.addOrReplaceChild("page"+side,CubeListBuilder.create(),PartPose.rotation(0,0,side*.16f));
            box(page,"cover",80,0,side<0?-2.8f:0,0,-1.5f,2.8f,.3f,3);
            box(page,"paper",0,48,side<0?-2.65f:.1f,-.28f,-1.35f,2.55f,.3f,2.7f);
        }
        var head=torso.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.offset(0,-4.8f,-.25f));
        box(head,"face",0,0,-2.6f,-3.2f,-2.1f,5.2f,4.4f,4.2f);
        for(int side:new int[]{-1,1}) {
            box(head,"hair"+side,0,32,side<0?-2.9f:2.2f,-2.9f,-1.9f,.7f,4.3f,3.7f);
            box(head,"eye"+side,112,0,side<0?-1.65f:.75f,-1.8f,-2.28f,.9f,1.35f,.2f);
            box(head,"glint"+side,112,16,side<0?-1.5f:.9f,-1.7f,-2.51f,.3f,.4f,.08f);
            box(head,"brow"+side,0,32,side<0?-1.85f:.65f,-2.35f,-2.35f,1.2f,.4f,.35f);
            box(head,"cheek"+side,120,16,side<0?-2.3f:1.45f,-.25f,-2.28f,.85f,.55f,.2f);
            box(head,"moustache"+side,0,32,side<0?-1.8f:.3f,.25f,-2.65f,1.5f,.65f,.7f);
            box(head,"moustache_tip"+side,0,32,side<0?-2.05f:1.3f,.65f,-2.6f,.75f,.5f,.65f);
        }
        box(head,"nose",0,0,-.45f,-.12f,-2.85f,.9f,.6f,.7f);
        box(head,"mouth",112,0,-.42f,.62f,-2.68f,.84f,.48f,.18f);
        box(head,"beard",0,32,-1.85f,1.15f,-2.4f,3.7f,1.65f,1.1f);
        box(head,"beard_middle",0,32,-1.35f,2.5f,-2.3f,2.7f,1.25f,1);
        box(head,"beard_tip",0,32,-.6f,3.5f,-2.1f,1.2f,1.1f,.8f);
        var cap=head.addOrReplaceChild("cap",CubeListBuilder.create(),PartPose.offset(0,-3.3f,.8f));
        // Receding front rim opens the face sightline; bevel corners create a rounded octagon.
        box(cap,"gills",40,48,-4.6f,-.2f,-2.9f,9.2f,.65f,7.6f);
        for(int tier=0;tier<5;tier++) {
            float width=10.6f-tier*1.65f,depth=8.8f-tier*1.4f,y=-1.1f-tier*1.05f;
            box(cap,"tier"+tier,40,0,-width/2,y,-depth/2+.9f,width,1.15f,depth);
            box(cap,"round"+tier,40,0,-width/2-.45f,y+.15f,-depth/2+1.65f,width+.9f,.9f,depth-1.5f);
            for(int side:new int[]{-1,1}) {
                box(cap,"spot_front"+tier+side,0,32,side*(width/2-1.5f)-.45f,y+.12f,-depth/2+.78f,.9f,.65f,.18f);
                box(cap,"spot_top"+tier+side,0,32,side*(width/2-1.35f)-.45f,y-.12f,1.4f+(tier%2),.9f,.16f,.8f);
                box(cap,"spot_back"+tier+side,0,32,side*(width/2-1.6f)-.45f,y+.15f,depth/2+.85f,.9f,.7f,.18f);
            }
        }
        return LayerDefinition.create(mesh,128,64);
    }
    @Override public void setupAnim(WiseFlyAgaric entity,float swing,float amount,float age,float yaw,float pitch) {
        float partial=Mth.clamp(age-entity.tickCount,0,1),close=0;
        var player=entity.level().getNearestPlayer(entity,3.5);
        if(player!=null) {
            var delta=player.getEyePosition(partial).subtract(entity.getPosition(partial).add(0,.82,0));
            close=Mth.clamp((float)(3.5-delta.horizontalDistance()),0,1);
            pitch=Mth.lerp(close,pitch,(float)(-Math.atan2(delta.y,delta.horizontalDistance())*Mth.RAD_TO_DEG));
            yaw=Mth.wrapDegrees((float)(Math.atan2(delta.z,delta.x)*Mth.RAD_TO_DEG)-90-Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot));
        }
        var look=looks.computeIfAbsent(entity,e->new Look());float alpha=1-(float)Math.exp(-Mth.clamp(age-look.age,0,1)/4);
        look.pitch=Mth.lerp(alpha,look.pitch,pitch);look.yaw=Mth.lerp(alpha,look.yaw,Mth.clamp(yaw,-35,35));
        look.close=Mth.lerp(alpha,look.close,close);look.age=age;
        float strength=entity.emoteStrength(partial);
        look.greet=Mth.lerp(alpha,look.greet,entity.emote()==WiseFlyAgaric.GREETING?strength:0);
        look.think=Mth.lerp(alpha,look.think,entity.emote()==WiseFlyAgaric.THINK?strength:0);
        look.read=Mth.lerp(alpha,look.read,entity.emote()==WiseFlyAgaric.READ?strength:0);
        animate(swing,amount,age,look.yaw,look.pitch,look.close,0,0);
        applyEmotes(look.greet,look.think,look.read);
    }
    public void animate(float swing,float amount,float age,float yaw,float pitch,float close,int emote,float strength) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float step=Mth.sin(swing*1.4f)*Mth.clamp(amount*3,0,1);
        leftLeg.xRot=step*.4f;rightLeg.xRot=-step*.4f;leftArm.xRot=-step*.2f;rightArm.xRot=step*.08f;
        torso.y+=Mth.sin(age*.09f)*.07f;cap.zRot=Mth.sin(age*.06f)*.015f;
        head.yRot=Mth.clamp(yaw*Mth.DEG_TO_RAD,-.4f,.4f);
        head.xRot=Mth.clamp(pitch*Mth.DEG_TO_RAD*.65f,-.55f,.12f);
        cap.z+=close*.7f;cap.y-=close*.5f;
        applyEmotes(emote==WiseFlyAgaric.GREETING?strength:0,
                emote==WiseFlyAgaric.THINK?strength:0,emote==WiseFlyAgaric.READ?strength:0);
    }
    private void applyEmotes(float greet,float think,float read) {
        book.visible=read>.01f;
        head.zRot+=think*.18f;leftArm.xRot-=think*.9f;leftArm.zRot-=think*.3f;
        torso.xRot+=greet*.13f;leftArm.zRot-=greet*.65f;leftArm.xRot-=greet*.9f;
        head.xRot=Mth.lerp(read,head.xRot,.2f);leftArm.xRot-=read*.95f;book.y=book.getInitialPose().y+(1-read)*1.6f;
    }
    private static class Look {float age,pitch,yaw,close,greet,think,read;}
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer sink,int light,int overlay,float r,float g,float b,float a) {
        root.render(pose,sink,light,overlay,r,g,b,a);
    }
}
