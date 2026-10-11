package io.github.golovinss.dudunka.client;

import com.mojang.blaze3d.vertex.*;
import io.github.golovinss.dudunka.ValeFirefly;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

public class ValeFireflyModel extends EntityModel<ValeFirefly> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("dudunka","vale_firefly"),"main");
    private final ModelPart root, left, right;
    public ValeFireflyModel(ModelPart root) { super(net.minecraft.client.renderer.RenderType::entityTranslucent); this.root=root; left=root.getChild("left_wing"); right=root.getChild("right_wing"); }
    public static LayerDefinition create() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        // Model pixels: a stepped rounded body, a friendly forward face, and thin paired wings.
        root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0,0).addBox(-1.3f,-1.1f,-.2f,2.6f,2.2f,2)
                .texOffs(0,5).addBox(-1,-1.3f,0,2,2.6f,1.6f), PartPose.offset(0,22.4f,0));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(12,0).addBox(-1,-.9f,-1.5f,2,1.8f,1.4f), PartPose.offset(0,22.4f,0));
        for (int side : new int[]{-1,1}) {
            root.addOrReplaceChild(side<0?"left_eye":"right_eye", CubeListBuilder.create().texOffs(22,0).addBox(-.2f,-.3f,-.15f,.4f,.6f,.3f),PartPose.offset(side*.5f,22.2f,-1.5f));
            root.addOrReplaceChild(side<0?"left_wing":"right_wing", CubeListBuilder.create().texOffs(12,6).addBox(side<0?-2:0,-.1f,-.6f,2,.2f,2.2f), PartPose.offset(side*.8f,21.7f,.1f));
            root.addOrReplaceChild(side<0?"left_antenna":"right_antenna", CubeListBuilder.create().texOffs(24,4).addBox(-.1f,-.7f,-.1f,.2f,.7f,.2f),PartPose.offset(side*.6f,21.6f,-1));
        }
        return LayerDefinition.create(mesh,32,16);
    }
    public void animate(float age, float phase) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float flap = (float)Math.sin(age*1.8+phase)*.65f;
        left.zRot=-.3f-flap; right.zRot=.3f+flap;
    }
    @Override public void setupAnim(ValeFirefly entity,float swing,float amount,float age,float yaw,float pitch) {
        animate(age, (entity.getUUID().getLeastSignificantBits()&65535)*.001f);
    }
    public static float glow(float age, long seed) {
        double phase=(seed&65535)*.001;
        return .45f+.55f*(float)Math.pow(.5+.5*Math.sin(age*.055+phase),2);
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer sink,int light,int overlay,float r,float g,float b,float a) {
        root.render(pose,sink,light,overlay,r,g,b,a);
    }
}
