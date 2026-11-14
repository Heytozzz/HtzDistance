package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.config.screen.HtzConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?}

/**
 * Opens the config screen (key, default J). The screen needs YACL; without it the key only
 * points to the command and the config file.
 */
public final class ConfigKey {
    private static KeyMapping key;

    private ConfigKey() {
    }

    public static void register() {
        KeyMapping mapping = new KeyMapping("key.htzdistance.config", InputConstants.KEY_J, GhostToggle.category());
        //? if >=26.1 {
        /*key = KeyMappingHelper.registerKeyMapping(mapping);
        *///?} else {
        key = KeyBindingHelper.registerKeyBinding(mapping);
        //?}
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                open(client);
            }
        });
    }

    public static KeyMapping key() {
        return key;
    }

    /** Opens the config screen over the current one (normally none: the key works in game). */
    public static void open(Minecraft client) {
        if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            if (client.player != null) {
                //? if >=26.1 {
                /*client.player.sendSystemMessage(Component.translatable("htzdistance.message.noConfigScreen"));
                *///?} else {
                client.player.displayClientMessage(Component.translatable("htzdistance.message.noConfigScreen"), false);
                //?}
            }
            return;
        }
        // Only referenced here, so YACL classes are never loaded when YACL is missing.
        setScreen(client, HtzConfigScreen.create(screen(client)));
    }

    //? if >=26.2 {
    /*/^* The open screen; it lives in the GUI since 26.2. ^/
    public static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }
    *///?} else {
    /** The open screen; it lives in the GUI since 26.2. */
    public static Screen screen(Minecraft client) {
        return client.screen;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }
    //?}
}
