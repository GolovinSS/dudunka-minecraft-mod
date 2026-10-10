package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.Pufik;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PufikRenderer extends MobRenderer<Pufik, PufikModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(DudunkaMod.ID, "textures/entity/pufik.png");
    public PufikRenderer(EntityRendererProvider.Context context) {
        super(context, new PufikModel(context.bakeLayer(PufikModel.LAYER)), .22f);
    }
    @Override public ResourceLocation getTextureLocation(Pufik entity) { return TEXTURE; }
}
