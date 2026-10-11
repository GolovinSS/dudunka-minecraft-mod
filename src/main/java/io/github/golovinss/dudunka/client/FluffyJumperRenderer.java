package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.FluffyJumper;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public class FluffyJumperRenderer extends MobRenderer<FluffyJumper,FluffyJumperModel> {
    private static final ResourceLocation[] TEXTURES = java.util.stream.IntStream.range(0,3)
            .mapToObj(i->ResourceLocation.fromNamespaceAndPath("dudunka","textures/entity/fluffy_jumper_"+i+".png"))
            .toArray(ResourceLocation[]::new);
    public FluffyJumperRenderer(EntityRendererProvider.Context context) {
        super(context,new FluffyJumperModel(context.bakeLayer(FluffyJumperModel.LAYER)),.2f);
    }
    @Override public ResourceLocation getTextureLocation(FluffyJumper entity) { return TEXTURES[entity.colorVariant()]; }
}
