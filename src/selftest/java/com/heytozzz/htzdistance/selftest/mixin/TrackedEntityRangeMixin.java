package com.heytozzz.htzdistance.selftest.mixin;

import com.heytozzz.htzdistance.capture.SnapshotFactory;
import com.heytozzz.htzdistance.ghost.GhostKind;
import com.heytozzz.htzdistance.selftest.SelfTestState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Self-test only: while enabled, the integrated server tracks armor stands up to 16 blocks and
 * item frames up to 8, like the target server. Disabled, vanilla ranges apply.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityRangeMixin {
    @Shadow
    @Final
    Entity entity;

    @Inject(method = "getEffectiveRange", at = @At("HEAD"), cancellable = true)
    private void htzdistanceSelftest$serverLikeRange(CallbackInfoReturnable<Integer> cir) {
        if (!SelfTestState.serverLikeRanges) {
            return;
        }
        GhostKind kind = SnapshotFactory.kindOf(entity);
        if (kind == GhostKind.ARMOR_STAND) {
            cir.setReturnValue(16);
        } else if (kind != null) {
            cir.setReturnValue(8);
        }
    }
}
