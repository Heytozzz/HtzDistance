package com.heytozzz.htzdistance.ghost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies a server + dimension pair. Ghosts are kept and persisted per key.
 *
 * @param server    "mp/&lt;address&gt;" for multiplayer, "sp/&lt;world name&gt;" for singleplayer
 * @param dimension dimension id, e.g. "minecraft:overworld"
 */
public record WorldKey(String server, String dimension) {

    @Nullable
    public static WorldKey of(Minecraft minecraft, @Nullable ClientLevel level) {
        if (level == null) {
            return null;
        }
        return new WorldKey(serverId(minecraft), level.dimension().location().toString());
    }

    private static String serverId(Minecraft minecraft) {
        IntegratedServer integrated = minecraft.getSingleplayerServer();
        if (integrated != null) {
            return "sp/" + integrated.getWorldData().getLevelName();
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null) {
            return "mp/" + server.ip.toLowerCase(java.util.Locale.ROOT);
        }
        return "unknown";
    }

    public boolean sameServer(@Nullable WorldKey other) {
        return other != null && server.equals(other.server);
    }

    @Override
    public String toString() {
        return server + " " + dimension;
    }
}
