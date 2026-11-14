package com.heytozzz.htzdistance.selftest;

import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import com.heytozzz.htzdistance.render.BenchFlags;

import java.util.List;

/** Settings the bench measures one at a time on top of the defaults. */
final class BenchVariants {
    static final List<String> NAMES = List.of("items resolved every frame", "no item restore", "no submit", "no state reuse", "culling off");

    private BenchVariants() {
    }

    static void apply(String name, boolean on) {
        switch (name) {
            case "items resolved every frame" -> BenchFlags.forceItemResolve = on;
            case "no item restore" -> BenchFlags.skipItemRefresh = on;
            case "no submit" -> BenchFlags.skipSubmit = on;
            case "no state reuse" -> update(config -> config.reuseRenderStates = !on);
            case "culling off" -> update(config -> config.occlusionCulling = !on);
            default -> throw new IllegalArgumentException(name);
        }
    }

    private static void update(java.util.function.Consumer<HtzConfig> change) {
        HtzConfig config = ConfigManager.get();
        change.accept(config);
        ConfigManager.apply(config);
    }
}
