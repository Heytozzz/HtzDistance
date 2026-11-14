package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
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
 * Turns the whole mod off and on (key, default H, {@code /htzdistance toggle}, or the "enabled"
 * option). The state is the {@code enabled} config option, so it is saved and survives restarts.
 * While off the mod does no work at all: nothing is captured, reconciled, drawn or traced, and
 * the culling thread sleeps. Stored holograms stay in memory; when the mod is turned on again,
 * holograms whose real entity came back meanwhile are removed.
 */
public final class GhostToggle {
    /** Last state seen, to react when the option is changed elsewhere (config screen, command). */
    private static boolean lastEnabled = true;
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
        lastEnabled = ConfigManager.get().enabled;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                toggle();
                notify(client);
            }
            checkChange();
        });
    }

    public static KeyMapping key() {
        return key;
    }

    /** {@code true} while the mod is turned off. */
    public static boolean isHidden() {
        return !ConfigManager.get().enabled;
    }

    /** Turns the mod off ({@code true}) or on, and saves the choice. */
    public static void setHidden(boolean value) {
        if (isHidden() == value) {
            return;
        }
        HtzConfig copy = ConfigManager.copy();
        copy.enabled = !value;
        ConfigManager.apply(copy);
        checkChange();
    }

    /** Flips the state and returns the new one ({@code true} = off). */
    public static boolean toggle() {
        setHidden(!isHidden());
        return isHidden();
    }

    public static Component stateMessage() {
        return Component.translatable(isHidden() ? "htzdistance.message.hidden" : "htzdistance.message.shown");
    }

    /** Stops or resumes the background work when the state changed. */
    private static void checkChange() {
        boolean enabled = ConfigManager.get().enabled;
        if (enabled == lastEnabled) {
            return;
        }
        lastEnabled = enabled;
        if (enabled) {
            GhostManager.get().resume();
        } else {
            CullingWorker.get().clear();
        }
        HtzDistanceClient.debug("HtzDistance turned {}", enabled ? "on" : "off");
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
