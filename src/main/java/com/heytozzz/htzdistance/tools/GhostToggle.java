package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.culling.CullingWorker;
import com.heytozzz.htzdistance.ghost.GhostManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?}
//? if >=1.21.10 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Pauses or resumes the whole mod at runtime (key, default H, or {@code /htzdistance toggle}).
 * While paused the mod does no work at all: nothing is captured, reconciled, drawn or traced,
 * and the culling thread sleeps. Stored ghosts stay in memory; on resume, ghosts whose real
 * entity came back meanwhile are removed. The state is not saved: the mod runs again after a
 * restart.
 */
public final class GhostToggle {
    private static boolean hidden;
    private static KeyMapping key;

    private GhostToggle() {
    }

    public static void register() {
        //? if >=1.21.10 {
        /*KeyMapping.Category category = KeyMapping.Category.register(
                ResourceLocation.fromNamespaceAndPath(HtzDistanceClient.MOD_ID, "main"));
        *///?} else {
        String category = "key.categories.htzdistance";
        //?}
        KeyMapping mapping = new KeyMapping("key.htzdistance.toggle", InputConstants.KEY_H, category);
        //? if >=26.1 {
        /*key = KeyMappingHelper.registerKeyMapping(mapping);
        *///?} else {
        key = KeyBindingHelper.registerKeyBinding(mapping);
        //?}
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                toggle();
                notify(client);
            }
        });
    }

    public static KeyMapping key() {
        return key;
    }

    public static boolean isHidden() {
        return hidden;
    }

    public static void setHidden(boolean value) {
        if (hidden == value) {
            return;
        }
        hidden = value;
        if (hidden) {
            CullingWorker.get().clear();
        } else {
            GhostManager.get().resume();
        }
        HtzDistanceClient.debug("HtzDistance {}", hidden ? "paused" : "resumed");
    }

    /** Flips the state and returns the new one ({@code true} = hidden). */
    public static boolean toggle() {
        setHidden(!hidden);
        return hidden;
    }

    public static Component stateMessage() {
        return Component.translatable(hidden ? "htzdistance.message.hidden" : "htzdistance.message.shown");
    }

    private static void notify(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        //? if >=26.1 {
        /*player.sendOverlayMessage(stateMessage());
        *///?} else {
        player.displayClientMessage(stateMessage(), true);
        //?}
    }
}
