package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.ValeSpider;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public class ValeSpiderRenderer extends MobRenderer<ValeSpider,ValeSpiderModel> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dudunka","textures/entity/vale_spider.png");
    public ValeSpiderRenderer(EntityRendererProvider.Context context) {
        super(context,new ValeSpiderModel(context.bakeLayer(ValeSpiderModel.LAYER)),.25f);
    }
    @Override public ResourceLocation getTextureLocation(ValeSpider entity) { return TEXTURE; }
}
