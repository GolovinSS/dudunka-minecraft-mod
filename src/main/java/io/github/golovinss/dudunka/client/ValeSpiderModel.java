package io.github.golovinss.dudunka.client;

import com.mojang.blaze3d.vertex.*;
import io.github.golovinss.dudunka.ValeSpider;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Stepped ivory silhouette, eight soft paws and the reference's signature red cap. */
public class ValeSpiderModel extends EntityModel<ValeSpider> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("dudunka", "vale_spider"), "main");
    private final ModelPart root, head, cap;
    private final ModelPart[] legs = new ModelPart[8];
    private final WeakHashMap<ValeSpider, LookState> looks = new WeakHashMap<>();
    public ValeSpiderModel(ModelPart root) {
        this.root = root; head = root.getChild("head"); cap = head.getChild("cap");
        for (int i = 0; i < 8; i++) legs[i] = root.getChild("leg"+i);
    }
    private static PartDefinition box(PartDefinition parent, String name, int u, int v,
            float x, float y, float z, float w, float h, float d) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d), PartPose.ZERO);
    }
    public static LayerDefinition create() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0,21,-.4f));
        box(head,"body",0,0,-3.3f,-2.8f,-2.8f,6.6f,4.5f,6.7f);
        box(head,"round_sides",0,0,-3.75f,-2.05f,-2.3f,7.5f,3.3f,5.5f);
        box(head,"round_belly",0,0,-3.45f,.6f,-2.5f,6.9f,1.4f,6.1f);
        box(head,"round_back",0,0,-3.1f,-2.3f,3.2f,6.2f,3.6f,1.3f);
        box(head,"soft_brow",0,0,-3.1f,-3.2f,-2.3f,6.2f,1,5.8f);
        var cap = head.addOrReplaceChild("cap", CubeListBuilder.create(), PartPose.offsetAndRotation(0,-3.05f,.35f,0,0,-.07f));
        box(cap,"brim",32,0,-3.5f,-.7f,-2.5f,7,1,6.5f);
        box(cap,"round_brim",32,0,-4,-.55f,-2,8,.85f,5.5f);
        box(cap,"brim_edge",32,24,-3.6f,-.15f,-2.75f,7.2f,.5f,6.6f);
        box(cap,"crown",32,0,-2.8f,-1.55f,-1.7f,5.6f,1.3f,4.8f);
        box(cap,"top",32,0,-1.8f,-2.05f,-.7f,3.6f,.65f,3.1f);
        box(cap,"button",64,24,-.6f,-2.3f,.3f,1.2f,.4f,1.1f);
        for (int side : new int[]{-1,1}) {
            float x = side*1.65f;
            var eye = box(head,side<0?"left_eye":"right_eye",80,0,x-1.05f,-1.9f,-2.94f,2.1f,2.05f,.18f);
            box(eye,"cream",96,0,x-.85f,-1.72f,-3.04f,1.65f,1.8f,.13f);
            box(eye,"pupil",80,0,x-.2f,-1.55f,-3.13f,1,1.65f,.12f);
            box(eye,"glint",96,0,x+.1f,-1.48f,-3.22f,.43f,.5f,.1f);
            box(head,side<0?"left_brow":"right_brow",80,16,x-.65f,-2.45f,-2.91f,1.15f,.25f,.12f);
            box(head,side<0?"left_cheek":"right_cheek",104,16,x-.7f,.35f,-2.96f,.45f,.7f,.15f);
            box(head,side<0?"left_blush":"right_blush",104,16,x+.05f,.35f,-2.96f,.3f,.6f,.15f);
            box(head,side<0?"smile_left":"smile_right",80,16,side<0?-.65f:.4f,.45f,-2.95f,.25f,.35f,.14f);
        }
        box(head,"smile",80,16,-.45f,.7f,-2.97f,.95f,.25f,.15f);
        for (int i = 0; i < 8; i++) {
            int side = i<4?-1:1, pair = i%4;
            float x = pair==0?2.25f:3.45f;
            float z = new float[]{-3.1f,-1.2f,1.05f,3.1f}[pair];
            var leg = root.addOrReplaceChild("leg"+i, CubeListBuilder.create().texOffs(0,24)
                    .addBox(-.7f,-.25f,-.7f,1.4f,1.9f,1.4f),PartPose.offset(side*x,21.7f,z));
            box(leg,"paw",0,24,-.9f,1.05f,-1,1.8f,1.15f,1.9f);
            box(leg,"toe",0,24,-.65f,1.65f,-1.1f,1.3f,.65f,1.7f);
        }
        return LayerDefinition.create(mesh,128,64);
    }
    @Override public void setupAnim(ValeSpider entity, float swing, float amount, float age, float yaw, float pitch) {
        float partial = Mth.clamp(age-entity.tickCount,0,1);
        var player = entity.level().getNearestPlayer(entity,3.5);
        float curious = 0;
        if (player != null) {
            var delta = player.getEyePosition(partial).subtract(entity.getPosition(partial).add(0,.25,0));
            double distance = delta.horizontalDistance();
            curious = Mth.clamp((float)(3.5-distance),0,1);
            pitch = Mth.lerp(curious,pitch,(float)(-Math.atan2(delta.y,distance)*Mth.RAD_TO_DEG));
            yaw = Mth.lerp(curious,yaw,Mth.wrapDegrees((float)(Math.atan2(delta.z,delta.x)*Mth.RAD_TO_DEG)-90
                    -Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot)));
        }
        var look = looks.computeIfAbsent(entity, ignored -> new LookState());
        float alpha = 1-(float)Math.exp(-Mth.clamp(age-look.age,0,1)/4);
        look.pitch = Mth.lerp(alpha,look.pitch,pitch); look.yaw = Mth.lerp(alpha,look.yaw,Mth.clamp(yaw,-30,30));
        look.curious = Mth.lerp(alpha,look.curious,curious); look.age = age;
        animate(swing,amount,age,look.yaw,look.pitch,look.curious,entity.emote(),entity.emoteStrength(partial));
    }
    /** Rigid rotations/translations only: breathing never stretches the voxel geometry. */
    public void animate(float swing, float amount, float age, float yaw, float pitch, float curious, int emote, float strength) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = Mth.clamp(amount*3,0,1);
        head.y += Mth.sin(age*.1f)*.055f-curious*.14f;
        head.xRot = Mth.clamp(pitch*Mth.DEG_TO_RAD*.28f,-.25f,.08f);
        head.yRot = Mth.clamp(yaw*Mth.DEG_TO_RAD*.35f,-.18f,.18f);
        cap.z += curious*.35f; cap.y -= curious*.15f;
        for (int i=0;i<8;i++) {
            int side=i<4?-1:1, pair=i%4;
            float phase=swing*1.7f+pair*Mth.PI*.5f+(side<0?0:Mth.PI);
            legs[i].xRot=Mth.sin(phase)*walk*.3f;
            legs[i].zRot=side*(Mth.cos(phase)*walk*.12f+Mth.sin(age*.07f+pair)*.018f);
        }
        if (emote==ValeSpider.GREETING) {
            legs[0].xRot-=strength*.85f;
            legs[0].zRot-=strength*(.4f+Mth.sin(age*.65f)*.16f);
        } else if (emote==ValeSpider.SHY) {
            head.y+=strength*.65f; head.xRot+=strength*.1f;
            for (int i=0;i<8;i++) legs[i].zRot+=(i<4?-1:1)*strength*.15f;
        }
    }
    private static class LookState { float age, pitch, yaw, curious; }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer sink, int light, int overlay, float r,float g,float b,float a) {
        root.render(pose,sink,light,overlay,r,g,b,a);
    }
}
