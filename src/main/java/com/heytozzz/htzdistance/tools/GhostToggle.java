package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.HtzDistanceClient;
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
 * Hides or shows all ghosts at runtime (key, default H, or {@code /htzdistance toggle}).
 * Ghosts keep being captured and reconciled while hidden; only drawing stops. The state is not
 * saved: ghosts are visible again after a restart.
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
        hidden = value;
        HtzDistanceClient.debug("Ghosts {}", hidden ? "hidden" : "shown");
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
