# HtzDistance

Servers stop sending armor stands, item frames and paintings once you are a few blocks away. On Spigot and Paper that is usually 16 blocks for armor stands and only 8 for frames and paintings, so a decorated base, a map wall or a shop full of displays empties out as soon as you step back to look at it.

HtzDistance fixes that on the client side. Right before the game deletes one of these entities because it left the tracking range, the mod takes a snapshot of it and keeps drawing it as a hologram in the same place. When you walk back and the server sends the real entity again, the hologram is removed and the real one takes over.

It is client-only. Nothing is sent to the server, and holograms are not part of the world: they cannot be hit, clicked or targeted, so they are safe to use on any server.

## What it keeps

- **Armor stands**: equipment (armor trims included), held items, pose, size, arms, base plate, invisibility and custom name.
- **Item frames and glow item frames**: the item, its rotation, the facing and invisible frames. Maps keep their picture.
- **Paintings**: every variant and size.

## How it works

- **Out of range or destroyed.** If an entity disappears while you were well inside its range, it was broken or removed, not left behind, so no hologram is kept. If you stand where the real entity should be visible and it does not come back within a few seconds, the hologram is removed as well.
- **Persistence.** Holograms are saved per server and per dimension, so after reconnecting or changing worlds the far parts of your base are still there. This can be turned off.
- **Learning mode** (optional). Instead of using the configured ranges, the mod measures the real distance at which each entity type disappears on the server you are playing on.
- **Occlusion culling.** Holograms behind walls are not drawn. The visibility checks run on a separate thread and never hold up a frame.
- **Limits.** A maximum render distance and a maximum number of holograms per dimension keep large builds under control.

## Styles

Holograms can look exactly like the real entities or stand out from them:

- **Normal**: identical to the real entity.
- **Tinted**: as if seen through tinted glass. Both the color and the strength of the tint can be changed.
- **Translucent**: see-through, with adjustable opacity.

The **glowing outline** is a separate setting with its own color and works with any of the three styles (1.21.10 and later).

## Controls

- **H** turns the whole mod on or off. While it is off it does no work at all. The choice is saved across restarts.
- **J** opens the config screen.

Both keys can be changed in the Controls menu.

## Configuration

Everything can be changed from the config screen (through Mod Menu and YACL) or with the `/htzdistance config` command, and is stored in `config/htzdistance.json`.

- Tracking range for each entity type (16 for armor stands, 8 for frames and paintings by default), and whether each type gets holograms at all
- Range margin and the timeout used to tell destroyed entities apart
- Maximum render distance (96 blocks) and maximum hologram count (1024)
- Style, tint color, tint opacity, translucent opacity, glowing outline and its color
- Persistence, occlusion culling and learning mode

The `/htzdistance` command also shows statistics, clears holograms in the current dimension, within a radius or on the whole server, and resets the settings. The same statistics (hologram counts and cost per frame) are available on the F3 screen.

## Performance

Holograms reuse their render data instead of rebuilding it every frame. In testing with large builds (hundreds of equipped armor stands and around two thousand item frames) they were cheaper to draw than the real entities they replace.

## Compatibility

- Minecraft 1.21.8, 1.21.10, 1.21.11, 26.1, 26.2 and 26.3 (1.21.9 is not supported)
- Requires Fabric API. Mod Menu and YACL are optional and only needed for the config screen.
- Works alongside Sodium, Iris, EntityCulling, Bobby and Distant Horizons.
- Available in English and Spanish.

## More details

Build instructions, default values, how rendering works on each version and performance measurements are in [docs/TECHNICAL.md](docs/TECHNICAL.md).

## About the development

The code of this mod was written with the help of artificial intelligence. The idea, the design of every feature, the planning and the testing decisions are my own.

## License

MIT, see [LICENSE](LICENSE).
