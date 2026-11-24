package com.ethanpierce.cursedtech.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the post-processing loader for inverted impact frames. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("loadEffect")
    void cursedtech$loadEffect(ResourceLocation id);
}
