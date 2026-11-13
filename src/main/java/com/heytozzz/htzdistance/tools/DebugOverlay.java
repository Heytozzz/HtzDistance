package com.heytozzz.htzdistance.tools;

import com.heytozzz.htzdistance.HtzDistanceClient;
//? if >=1.21.10 {
/*import com.heytozzz.htzdistance.config.ConfigManager;
import com.heytozzz.htzdistance.config.HtzConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
*///?}

/**
 * Shows {@link DebugStats} on the F3 screen.
 *
 * <p>From 1.21.10 on, the debug screen is made of entries the player can switch on and off
 * (F3 + F6). The mod registers its own entry and switches it on for the F3 screen once; after
 * that the player's choice is kept. Up to 1.21.8 the lines are appended by
 * {@code DebugScreenOverlayMixin}.
 */
public final class DebugOverlay {
    //? if >=1.21.10 {
    /*public static final ResourceLocation ENTRY_ID = ResourceLocation.fromNamespaceAndPath(HtzDistanceClient.MOD_ID, "ghosts");
    *///?}

    private DebugOverlay() {
    }

    public static void register() {
        //? if >=1.21.10 {
        /*DebugScreenEntries.register(ENTRY_ID, new Entry());
        ClientTickEvents.END_CLIENT_TICK.register(DebugOverlay::enableOnce);
        *///?}
    }

    //? if >=1.21.10 {
    /*/^* "Only while F3 is open" (named IN_F3 in 1.21.10, IN_OVERLAY later). ^/
    private static final DebugScreenEntryStatus IN_OVERLAY = java.util.Arrays.stream(DebugScreenEntryStatus.values())
            .filter(status -> status != DebugScreenEntryStatus.ALWAYS_ON && status != DebugScreenEntryStatus.NEVER)
            .findFirst()
            .orElse(DebugScreenEntryStatus.ALWAYS_ON);

    /^* Switches the entry on for the F3 screen the first time the mod runs. ^/
    private static void enableOnce(Minecraft minecraft) {
        HtzConfig config = ConfigManager.get();
        if (config.debugEntryEnabledOnce || minecraft.debugEntries == null) {
            return;
        }
        if (minecraft.debugEntries.getStatus(ENTRY_ID) == DebugScreenEntryStatus.NEVER) {
            minecraft.debugEntries.setStatus(ENTRY_ID, IN_OVERLAY);
            minecraft.debugEntries.save();
        }
        HtzConfig copy = ConfigManager.copy();
        copy.debugEntryEnabledOnce = true;
        ConfigManager.apply(copy);
        HtzDistanceClient.debug("Enabled the F3 entry {}", ENTRY_ID);
    }

    private static final class Entry implements DebugScreenEntry {
        @Override
        public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
            displayer.addToGroup(ENTRY_ID, DebugStats.lines());
        }

        @Override
        public boolean isAllowed(boolean reducedDebugInfo) {
            // Only client-side data, nothing the server would want to hide.
            return true;
        }
    }
    *///?}
}
