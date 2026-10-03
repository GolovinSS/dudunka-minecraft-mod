package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;

@Mod.EventBusSubscriber(modid=DudunkaMod.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){for(Kind k:Kind.values())e.registerLayerDefinition(FamilyModel.layer(k),()->FamilyModel.create(k));}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){for(Kind k:Kind.values())e.registerEntityRenderer(DudunkaMod.TYPES.get(k).get(),c->new FamilyRenderer(c,k));}
    private static class FamilyRenderer extends MobRenderer<Companion,FamilyModel>{
        FamilyRenderer(EntityRendererProvider.Context c,Kind k){super(c,new FamilyModel(c.bakeLayer(FamilyModel.layer(k))),.2f);}
        @Override public ResourceLocation getTextureLocation(Companion e){return new ResourceLocation(DudunkaMod.ID,"textures/entity/palette.png");}
        @Override protected void scale(Companion e,PoseStack p,float partial){float s=e.growthScale();p.scale(s,s,s);}
    }
}
