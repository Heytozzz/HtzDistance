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
    /*
     * Full descriptor of PacketUtils#ensureRunningOnSameThread for this version. It must be
     * complete: without a descriptor Loom cannot remap the target to intermediary names and the
     * injection fails on obfuscated versions outside the dev environment.
     */
    //? if >=1.21.10 {
    /*private static final String ENSURE_SAME_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";
    *///?} else {
    private static final String ENSURE_SAME_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V";
    //?}

    /**
     * Runs right after the packet was moved to the client thread and before any entity is
     * removed, so the entities are still alive and their state can be captured.
     */
    @Inject(
            method = "handleRemoveEntities",
            at = @At(
                    value = "INVOKE",
                    target = ENSURE_SAME_THREAD,
                    shift = At.Shift.AFTER
            )
    )
    private void htzdistance$beforeRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        //? if >=26.3 {
        /*GhostManager.get().onRemoveEntities(packet.entityIds());
        *///?} else {
        GhostManager.get().onRemoveEntities(packet.getEntityIds());
        //?}
    }
}
