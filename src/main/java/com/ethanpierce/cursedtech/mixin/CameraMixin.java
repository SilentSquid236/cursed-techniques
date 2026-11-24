package com.ethanpierce.cursedtech.mixin;

import com.ethanpierce.cursedtech.client.CinematicCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@link CinematicCamera} take over the camera during a shot. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void cursedtech$cinematic(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                      float partialTick, CallbackInfo ci) {
        CinematicCamera.Pose pose = CinematicCamera.pose(partialTick);
        if (pose == null) return;
        this.setRotation(pose.yaw(), pose.pitch());
        this.setPosition(pose.x(), pose.y(), pose.z());
    }
}
