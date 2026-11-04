package com.heytozzz.htzdistance.config.screen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu entry point. The config screen needs YACL; without it Mod Menu shows no config button
 * and the config can still be edited in {@code config/htzdistance.json}.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return parent -> null;
        }
        // Only referenced here, so YACL classes are never loaded when YACL is missing.
        return HtzConfigScreen::create;
    }
}
