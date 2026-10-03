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
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){for(Kind k:Kind.values())e.registerLayerDefinition(FamilyModel.layer(k),()->FamilyModel.create(k));
        for(Kind kind:Kind.values())if(FamilyModel.hasAgeModels(kind))for(int stage=1;stage<=2;stage++){final int age=stage;e.registerLayerDefinition(FamilyModel.layer(kind,age),()->FamilyModel.create(kind,age));}}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){for(Kind k:Kind.values())e.registerEntityRenderer(DudunkaMod.TYPES.get(k).get(),c->new FamilyRenderer(c,k));}
    private static class FamilyRenderer extends MobRenderer<Companion,FamilyModel>{
        private final FamilyModel[] ages;
        FamilyRenderer(EntityRendererProvider.Context c,Kind k){
            super(c,new FamilyModel(c.bakeLayer(FamilyModel.layer(k))),.2f);
            ages=FamilyModel.hasAgeModels(k)?new FamilyModel[]{model,new FamilyModel(c.bakeLayer(FamilyModel.layer(k,1))),new FamilyModel(c.bakeLayer(FamilyModel.layer(k,2)))}:new FamilyModel[]{model};
        }
        @Override public void render(Companion e,float yaw,float partial,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
            model=ages.length==1?ages[0]:ages[Math.max(0,Math.min(2,e.stage()))];
            super.render(e,yaw,partial,pose,buffers,light);
        }
        @Override public ResourceLocation getTextureLocation(Companion e){return new ResourceLocation(DudunkaMod.ID,"textures/entity/"+(FamilyModel.hasAgeModels(e.kind) && e.stage()>0?e.kind.id+(e.stage()==1?"_teen.png":"_adult.png"):"palette.png"));}
        @Override protected void scale(Companion e,PoseStack p,float partial){float s=e.growthScale();p.scale(s,s,s);}
    }
}
