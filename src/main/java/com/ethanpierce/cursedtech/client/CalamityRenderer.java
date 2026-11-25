package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.CursedTech;
import com.ethanpierce.cursedtech.entity.CalamityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** A tall robed sorcerer with glowing eyes and markings. */
public class CalamityRenderer extends HumanoidMobRenderer<CalamityEntity, PlayerModel<CalamityEntity>> {
    private static final ResourceLocation TEXTURE = CursedTech.id("textures/entity/calamity.png");
    private static final RenderType EYES = RenderType.eyes(CursedTech.id("textures/entity/calamity_eyes.png"));

    public CalamityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.6F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(CalamityEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(CalamityEntity entity, PoseStack pose, float partialTick) {
        pose.scale(1.12F, 1.12F, 1.12F);
    }
}
