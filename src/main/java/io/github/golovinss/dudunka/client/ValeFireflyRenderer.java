package io.github.golovinss.dudunka.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.golovinss.dudunka.ValeFirefly;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class ValeFireflyRenderer extends MobRenderer<ValeFirefly,ValeFireflyModel> {
    public static ResourceLocation texture(int variant, boolean glow) {
        String name = switch (variant) { case 1 -> "turquoise"; case 2 -> "violet"; default -> "golden"; };
        return ResourceLocation.fromNamespaceAndPath("dudunka","textures/entity/vale_firefly_"+name+(glow?"_glow":"")+".png");
    }
    public ValeFireflyRenderer(EntityRendererProvider.Context context) {
        super(context,new ValeFireflyModel(context.bakeLayer(ValeFireflyModel.LAYER)),0);
        addLayer(new Glow(this));
    }
    @Override protected int getBlockLightLevel(ValeFirefly entity, net.minecraft.core.BlockPos pos) {
        return Math.max(8, super.getBlockLightLevel(entity, pos));
    }
    @Override public ResourceLocation getTextureLocation(ValeFirefly entity) { return texture(entity.variant(),false); }
    private static class Glow extends RenderLayer<ValeFirefly,ValeFireflyModel> {
        Glow(ValeFireflyRenderer parent) { super(parent); }
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,ValeFirefly entity,
                float swing,float amount,float partial,float age,float yaw,float pitch) {
            if (entity.isInvisible()) return;
            float pulse=ValeFireflyModel.glow(age,entity.getUUID().getLeastSignificantBits());
            // Vanilla eyes pipeline: additive emissive texture, no lightmap/world-light updates.
            getParentModel().renderToBuffer(pose,buffers.getBuffer(RenderType.eyes(texture(entity.variant(),true))),
                    LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pulse,pulse,pulse,1);
        }
    }
}
