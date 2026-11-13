package com.heytozzz.htzdistance.mixin;

// Only used up to 1.21.8; later versions register a debug screen entry (see DebugOverlay).
//? if <1.21.10 {
import com.heytozzz.htzdistance.tools.DebugStats;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Appends the ghost counts and costs to the left column of the F3 screen. */
@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void htzdistance$addGhostStats(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = cir.getReturnValue();
        lines.add("");
        lines.addAll(DebugStats.lines());
    }
}
//?}
