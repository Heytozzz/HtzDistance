# HtzDistance

Client-only Fabric mod that keeps showing **armor stands**, **item frames** and **glow item
frames** after the server stops sending them because you walked out of its entity tracking
range. Right before the client deletes such an entity, the mod takes a snapshot of it and keeps
drawing it as a **ghost** until the real entity comes back into range.

Ghosts are never added to the world: they cannot be targeted, clicked or hit, and they never
cause any packet to be sent to the server.

## Features

- Captures the full entity state: armor stand equipment, pose, size, arms, base plate,
  invisibility, custom name; item frame item, item rotation, facing, invisible frame.
- **Maps** in item frames keep their picture (map colors are captured too).
- **Out of range vs destroyed**: an entity removed while you were well inside its tracking range
  was destroyed and is not kept. If you stand inside the real range and the entity does not come
  back within a few seconds, its ghost is removed.
- **Reconciliation**: when the real entity is sent again, the ghost at that spot disappears.
- **Occlusion culling** like EntityCulling: asynchronous ray tracing on a background thread with
  the [LogisticsCraft OcclusionCulling](https://github.com/LogisticsCraft/OcclusionCulling)
  library (MIT); ghosts hidden behind blocks are skipped.
- **Learning mode** (optional): measures the real disappearance distance of each entity type on
  each server and uses it instead of the configured values.
- **Persistence** (optional): ghosts are saved per server and dimension in
  `<game dir>/htzdistance/<server>/<dimension>/ghosts.dat`, like Bobby.
- Limits for max ghost count and max render distance.
- Config screen through Mod Menu + YACL (both optional); the config file is
  `config/htzdistance.json`.

## Supported versions

| Minecraft        | Jar            | Fabric API | Ghost style (tint / translucent)           |
|------------------|----------------|------------|--------------------------------------------|
| 1.21.8           | `+1.21.8`      | required   | real tint and real translucency            |
| 1.21.10          | `+1.21.10`     | required   | glowing outline in the tint color          |
| 1.21.11          | `+1.21.11`     | required   | glowing outline in the tint color          |
| 26.1 – 26.1.2    | `+26.1.2`      | required   | glowing outline in the tint color          |
| 26.2             | `+26.2`        | required   | glowing outline in the tint color          |
| 26.3             | `+26.3`        | required   | glowing outline in the tint color          |

1.21.9 is not supported (its Fabric API has no world render event; 1.21.10 is the hotfix).
From 1.21.10 on, Minecraft draws entities from deferred render states, so ghosts are drawn by
vanilla together with the real entities and the non-normal styles use the vanilla outline.

Optional: [Mod Menu](https://modrinth.com/mod/modmenu) and
[YACL](https://modrinth.com/mod/yacl) for the config screen.

## Default settings

The defaults match measurements on the target server (Spigot/Paper tracking ranges are measured
horizontally, X/Z only):

| Setting                       | Default |
|-------------------------------|---------|
| Armor stand tracking range    | 16      |
| Item frame tracking range     | 8       |
| Glow item frame tracking range| 8       |
| Range margin                  | 1.5     |
| "Destroyed" timeout           | 5 s     |
| Max ghost render distance     | 96      |
| Max ghosts per dimension      | 1024    |
| Occlusion culling             | on      |
| Persistence                   | off     |
| Learning mode                 | off     |

## Compatibility

- **EntityCulling**: no conflict. Ghosts are not in the world, so EntityCulling never sees them;
  EntityCulling ships its own relocated copy of the culling library.
- **Bobby / Distant Horizons**: independent; ghosts follow the normal render distance setting of
  the mod.

## Building

```
./gradlew build                 # every version, jars in versions/<mc>/build/libs/
./gradlew :1.21.8:build         # one version
./gradlew :1.21.8:runClient     # dev client
./gradlew :1.21.8:runClientGameTest   # automated in-game tests
```

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) (one source tree, one jar per
Minecraft version) with `loom-back-compat`, so obfuscated (1.21.x, Mojang mappings) and
unobfuscated (26.x) versions share the same code.

The client game tests create singleplayer worlds and use a test-only mixin that gives the
integrated server the target server's ranges (16 blocks for armor stands, 8 for item frames).
They cover capture, reconciliation, destroyed-entity detection, the in-range timeout, map
capture, styles, occlusion culling, persistence, learning mode and the config screen.

## Self-test in a real launcher instance

`src/selftest` builds a separate test mod (`./gradlew :1.21.11:selftestJar`, output in
`versions/<mc>/build/libs/htzdistance-selftest-<mc>.jar`). It is never part of the release jar
and does nothing unless `htzdistance-selftest.trigger` exists in the game directory. With the
trigger it creates a flat world, builds an armory (408 equipped armor stands) and a warehouse
(about 2,000 item frames, 144 maps), measures real entities vs ghosts (culling on/off) vs nothing,
compares every ghost's rendered rotation with the real entity (all 45-degree steps, all frame
facings and item rotations), writes `htzdistance-selftest-report.txt` plus screenshots and quits.

Results with the author's modpacks (2,484 entities), with ghost render states reused across
frames (re-extracted about once per second):

| Instance                                                 | Ghost cost, culling on / off | Before caching | FPS real / ghosts | Capture             |
|----------------------------------------------------------|------------------------------|----------------|-------------------|---------------------|
| 26.3, 267 mods (Sodium, EntityCulling, Bobby, EMF/ETF)   | 1.6 / 3.1 ms per frame       | 18.1 / 29.0 ms | 13 / 20           | 0.05 ms per entity  |
| 1.21.11, 172 mods (Sodium, Iris, EntityCulling, EMF/ETF) | 1.5 / 1.7 ms per frame       | 1.7 / 2.7 ms   | capped (59 / 59)  | 0.03 ms per entity  |

Ghosts are now cheaper than the real entities they replace. Occlusion culling runs on its own
thread (about 6-15 ms per pass for 2,484 ghosts, never blocking a frame) and roughly halves the
number of ghosts drawn in that scene.

## License

MIT, see [LICENSE](LICENSE). Bundles LogisticsCraft OcclusionCulling (MIT).
