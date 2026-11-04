package com.heytozzz.htzdistance.mixin;

import com.heytozzz.htzdistance.ghost.GhostManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    /**
     * Runs right after the packet was moved to the client thread and before any entity is
     * removed, so the entities are still alive and their state can be captured.
     */
    @Inject(
            method = "handleRemoveEntities",
            at = @At(
                    value = "INVOKE",
                    // No descriptor on purpose: the third parameter type changes between versions.
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread",
                    shift = At.Shift.AFTER
            )
    )
    private void htzdistance$beforeRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        GhostManager.get().onRemoveEntities(packet.getEntityIds());
    }
}
