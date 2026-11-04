package com.heytozzz.htzdistance.render;

import com.heytozzz.htzdistance.HtzDistanceClient;
import com.heytozzz.htzdistance.capture.MapSnapshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Makes captured map contents available to the vanilla map renderer.
 *
 * <p>The item frame renderer looks maps up by id in the client level. While connected, the
 * client usually still has the data; after a reconnect (persisted ghosts) it does not, because
 * the server only sends map data for frames that are in range. In that case the captured colors
 * are registered under the same id. Real data sent later by the server simply replaces it.
 */
public final class GhostMapSupport {
    private GhostMapSupport() {
    }

    public static void ensureMapData(ClientLevel level, @Nullable MapSnapshot map) {
        if (map == null || map.colors().length != MapSnapshot.COLOR_COUNT) {
            return;
        }
        MapId id = new MapId(map.mapId());
        if (level.getMapData(id) != null) {
            return;
        }
        MapItemSavedData data = MapItemSavedData.createForClient(map.scale(), map.locked(), level.dimension());
        System.arraycopy(map.colors(), 0, data.colors, 0, MapSnapshot.COLOR_COUNT);
        level.overrideMapData(id, data);
        HtzDistanceClient.debug("Restored map #{} for a ghost item frame", map.mapId());
    }

    /** Latest colors the client has for this map, or {@code null} if it has none. */
    @Nullable
    public static byte[] currentColors(ClientLevel level, int mapId) {
        MapItemSavedData data = level.getMapData(new MapId(mapId));
        if (data == null || data.colors == null || data.colors.length != MapSnapshot.COLOR_COUNT) {
            return null;
        }
        return data.colors.clone();
    }
}
