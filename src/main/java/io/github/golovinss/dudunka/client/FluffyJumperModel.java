package io.github.golovinss.dudunka.client;

import com.mojang.blaze3d.vertex.*;
import io.github.golovinss.dudunka.FluffyJumper;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original stepped wool sphere: forehead tufts, cheek fluff, tiny paws and a friendly face. */
public class FluffyJumperModel extends EntityModel<FluffyJumper> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("dudunka", "fluffy_jumper"), "main");
    private final ModelPart root, fluff;
    private final ModelPart[] paws = new ModelPart[4];
    public FluffyJumperModel(ModelPart root) {
        this.root = root; fluff = root.getChild("fluff");
        for (int i=0;i<4;i++) paws[i]=fluff.getChild("paw"+i);
    }
    private static void box(PartDefinition parent, String name, int u, int v,
            float x, float y, float z, float w, float h, float d) {
        parent.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.ZERO);
    }
    public static LayerDefinition create() {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        var fluff=root.addOrReplaceChild("fluff",CubeListBuilder.create(),PartPose.offset(0,20,0));
        box(fluff,"core",0,0,-3.2f,-2.5f,-2.9f,6.4f,4.4f,5.8f);
        box(fluff,"round_sides",0,0,-3.7f,-1.6f,-2.5f,7.4f,2.8f,5);
        box(fluff,"round_front_back",0,0,-2.7f,-2.1f,-3.35f,5.4f,3.7f,6.7f);
        box(fluff,"crown",0,0,-2.6f,-3.65f,-2.2f,5.2f,1.5f,4.4f);
        box(fluff,"shoulders",0,0,-3.25f,-2.75f,-2.4f,6.5f,1.3f,4.8f);
        box(fluff,"lower_fluff",0,24,-2.2f,2.2f,-2,4.4f,.8f,4);
        box(fluff,"belly",0,24,-2.8f,1.25f,-2.6f,5.6f,1.55f,5.2f);
        // Irregular cuboid tufts retain the reference's shaggy silhouette in every direction.
        for(int i=0;i<12;i++) {
            float a=i*Mth.TWO_PI/12;
            float x=Mth.cos(a)*3.25f,z=Mth.sin(a)*2.95f;
            box(fluff,"ruff"+i,32,0,x-.55f,-1.35f+(i%3)*.7f,z-.55f,1.1f,1.5f,1.1f);
            box(fluff,"top_tuft"+i,32,0,x*.68f-.45f,-3.6f-(i%3)*.22f,z*.68f-.45f,.9f,1.1f,.9f);
        }
        box(fluff,"forelock",32,0,-.6f,-4.15f,-.8f,1.2f,1.1f,1.1f);
        for(int side:new int[]{-1,1}) {
            float x=side*1.5f;
            box(fluff,side<0?"left_eye":"right_eye",64,0,x-.62f,-1.65f,-3.52f,1.24f,1.65f,.22f);
            box(fluff,"glint"+side,72,0,x-.34f,-1.5f,-3.68f,.43f,.5f,.12f);
            box(fluff,"small_glint"+side,72,0,x+.16f,-.42f,-3.68f,.22f,.25f,.12f);
            box(fluff,"cheek"+side,80,0,x-.66f,.28f,-3.48f,1.3f,.48f,.16f);
            box(fluff,"smile_corner"+side,64,8,side<0?-.66f:.45f,.34f,-3.52f,.22f,.35f,.12f);
        }
        box(fluff,"smile",64,8,-.45f,.56f,-3.52f,.9f,.2f,.12f);
        for(int i=0;i<4;i++) {
            float x=i%2==0?-2.25f:2.25f,z=i<2?-1.9f:1.9f;
            var paw=fluff.addOrReplaceChild("paw"+i,CubeListBuilder.create().texOffs(0,24)
                    .addBox(-.65f,0,-.7f,1.3f,1.35f,1.4f),PartPose.offset(x,2.6f,z));
            box(paw,"toe",0,24,-.7f,.85f,-.9f,1.4f,.55f,1.6f);
        }
        return LayerDefinition.create(mesh,128,64);
    }
    @Override public void setupAnim(FluffyJumper entity,float swing,float amount,float age,float yaw,float pitch) {
        float partial=Mth.clamp(age-entity.tickCount,0,1);
        animate(age,yaw,pitch,entity.hopAge(partial),entity.visualVerticalSpeed(partial),
                entity.landingStrength(partial),entity.greetingStrength(partial));
    }
    /** Small smooth deformations are deliberate squash/stretch, reset for every entity and pose. */
    public void animate(float age,float yaw,float pitch,float hopAge,float vertical,float landing,float greeting) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float airborne=hopAge>=0 && hopAge<16 ? Mth.clamp(Math.abs(vertical)*4,0,1):0;
        float squash=Mth.clamp(landing,0,1)*.13f;
        fluff.xScale=fluff.zScale=1+squash-airborne*.045f;
        fluff.yScale=1-squash+airborne*.09f;
        fluff.y+=Mth.sin(age*.12f)*.055f+4*(1-fluff.yScale);
        fluff.xRot=Mth.clamp(pitch*Mth.DEG_TO_RAD*.24f,-.22f,.12f);
        fluff.yRot=Mth.clamp(yaw*Mth.DEG_TO_RAD*.3f,-.24f,.24f);
        fluff.zRot=Mth.sin(age*.55f)*greeting*.12f;
        for(int i=0;i<4;i++) {
            paws[i].xRot=(i<2?-1:1)*airborne*.4f;
            paws[i].zRot=(i%2==0?-1:1)*greeting*(.18f+Mth.sin(age*.65f)*.13f);
        }
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer out,int light,int overlay,float r,float g,float b,float a) {
        root.render(pose,out,light,overlay,r,g,b,a);
    }
}
