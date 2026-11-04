package com.heytozzz.htzdistance.test.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test-only: makes the integrated server use the tracking ranges measured on the target server
 * (16 blocks for armor stands, 8 for item frames) so ghosts can be tested in singleplayer.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityRangeMixin {
    @Shadow
    @Final
    Entity entity;

    @Inject(method = "getEffectiveRange", at = @At("HEAD"), cancellable = true)
    private void htzdistanceTest$serverLikeRange(CallbackInfoReturnable<Integer> cir) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.ARMOR_STAND) {
            cir.setReturnValue(16);
        } else if (type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME) {
            cir.setReturnValue(8);
        }
    }
}
