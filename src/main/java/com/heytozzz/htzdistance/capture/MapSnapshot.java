package com.heytozzz.htzdistance.capture;

/**
 * Copy of the client-side data of a filled map shown in an item frame, so the ghost can still
 * show the map after a reconnect (persistence) or if the client dropped the map data.
 *
 * @param mapId  numeric map id ({@code MapId#id()})
 * @param scale  map scale (0-4)
 * @param locked whether the map is locked
 * @param colors 128x128 color indices, copied (never shared with the live map)
 */
public record MapSnapshot(int mapId, byte scale, boolean locked, byte[] colors) {
    public static final int COLOR_COUNT = 128 * 128;
}
