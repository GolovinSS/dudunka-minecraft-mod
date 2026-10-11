package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.WiseFlyAgaric;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public class WiseFlyAgaricRenderer extends MobRenderer<WiseFlyAgaric,WiseFlyAgaricModel> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("dudunka","textures/entity/wise_fly_agaric.png");
    public WiseFlyAgaricRenderer(EntityRendererProvider.Context context) {
        super(context,new WiseFlyAgaricModel(context.bakeLayer(WiseFlyAgaricModel.LAYER)),.3f);
    }
    @Override public ResourceLocation getTextureLocation(WiseFlyAgaric entity) { return TEXTURE; }
}
