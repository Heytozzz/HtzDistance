# HtzDistance

Client-only Fabric mod that keeps showing **armor stands**, **item frames**, **glow item
frames** and **paintings** after the server stops sending them because you walked out of its entity tracking
range. Right before the client deletes such an entity, the mod takes a snapshot of it and keeps
drawing it as a **hologram** until the real entity comes back into range.

Holograms are never added to the world: they cannot be targeted, clicked or hit, and they never
cause any packet to be sent to the server.

## Features

- Captures the full entity state: armor stand equipment, pose, size, arms, base plate,
  invisibility, custom name; item frame item, item rotation, facing, invisible frame.
- **Maps** in item frames keep their picture (map colors are captured too).
- **Out of range vs destroyed**: an entity removed while you were well inside its tracking range
  was destroyed and is not kept. If you stand inside the real range and the entity does not come
  back within a few seconds, its hologram is removed.
- **Reconciliation**: when the real entity is sent again, the hologram at that spot disappears.
- **Occlusion culling** like EntityCulling: asynchronous ray tracing on a background thread with
  the [LogisticsCraft OcclusionCulling](https://github.com/LogisticsCraft/OcclusionCulling)
  library (MIT); holograms hidden behind blocks are skipped.
- **Learning mode** (optional): measures the real disappearance distance of each entity type on
  each server and uses it instead of the configured values.
- **Persistence** (on by default): holograms are saved per server and dimension in
  `<game dir>/htzdistance/<server>/<dimension>/ghosts.dat`, like Bobby.
- Limits for max hologram count and max render distance.
- **Styles**: *Normal*; *Tinted*, as if seen through tinted glass (tint color and tint opacity);
  *Translucent* (opacity). The **glowing outline** is a separate option with its own color
  (1.21.10+), usable with any style.
- **Toggle Mod key** (default `H`, rebindable in Controls): turns the whole mod off and on and
  shows "Htz: Holograms ON/OFF" above the hotbar. Off, it costs nothing at all: no capture, no
  reconciliation, no drawing, and the culling thread sleeps. It is the `enabled` option, so the
  choice is saved and kept after a restart. Holograms stay in memory; when the mod is turned on
  again, holograms whose real entity came back meanwhile are removed.
- **Open Config key** (default `J`): opens the config screen (needs YACL).
- **F3 stats**: hologram counts per type, holograms drawn and the average cost per frame and per
  culling pass. From 1.21.10 on it is the `htzdistance:holograms` entry of the debug options
  (F3 + F6), switched on for the F3 screen the first time the mod runs.
- **`/htzdistance` client command**:

  | Command                               | Effect                                                         |
  |---------------------------------------|----------------------------------------------------------------|
  | `/htzdistance` or `stats`             | Shows the same stats as F3 in chat                             |
  | `/htzdistance toggle`                 | Turns the mod off or on, like the key                          |
  | `/htzdistance clear`                  | Forgets the holograms of the current dimension                    |
  | `/htzdistance clear <radius>`         | Forgets the holograms within that many blocks of you              |
  | `/htzdistance clear all`              | Forgets the holograms of every dimension of this server, saved files included |
  | `/htzdistance config`                 | Lists every option of the config file with its value           |
  | `/htzdistance config <option> <value>`| Changes and saves an option, e.g. `itemFrame.threshold 9`, `hologramStyle tint`, `tintColor #A0C8FF` |
  | `/htzdistance config reset`           | Restores the defaults                                          |

- **Languages**: English and Spanish (every Spanish variant: es_es, es_mx, es_ar, es_cl, es_ec,
  es_uy, es_ve).
- Config screen through Mod Menu + YACL (both optional); the config file is
  `config/htzdistance.json`.

## Supported versions

| Minecraft        | Jar            | Fabric API | Glowing outline |
|------------------|----------------|------------|-----------------|
| 1.21.8           | `+1.21.8`      | required   | no              |
| 1.21.10          | `+1.21.10`     | required   | yes             |
| 1.21.11          | `+1.21.11`     | required   | yes             |
| 26.1 – 26.1.2    | `+26.1.2`      | required   | yes             |
| 26.2             | `+26.2`        | required   | yes             |
| 26.3             | `+26.3`        | required   | yes             |

1.21.9 is not supported (its Fabric API has no world render event; 1.21.10 is the hotfix).
From 1.21.10 on, Minecraft draws entities from deferred render states, so holograms are drawn by
vanilla together with the real entities. The tinted and translucent styles are applied while a
hologram's state is submitted: model tints are multiplied, render types are swapped for
translucent ones, and items and the frame block are drawn from their quads so every vertex can
be recolored (without the enchantment glint). Up to 1.21.8 the same is done in the buffer source.

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
| Painting tracking range       | 8       |
| Range margin                  | 1.5     |
| "Destroyed" timeout           | 5 s     |
| Max hologram render distance     | 96      |
| Max holograms per dimension      | 1024    |
| Occlusion culling             | on      |
| Persistence                   | on      |
| Learning mode                 | off     |

## Compatibility

- **EntityCulling**: no conflict. Holograms are not in the world, so EntityCulling never sees them;
  EntityCulling ships its own relocated copy of the culling library.
- **Bobby / Distant Horizons**: independent; holograms follow the normal render distance setting of
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
(about 2,000 item frames, 144 maps), measures real entities vs holograms (culling on/off) vs nothing,
compares every hologram's rendered rotation with the real entity (all 45-degree steps, all frame
facings and item rotations), writes `htzdistance-selftest-report.txt` plus screenshots and quits.

Results with the author's modpacks (2,484 entities), with hologram render states reused across
frames (re-extracted about once per second):

| Instance                                                 | Hologram cost, culling on / off | Before caching | FPS real / holograms | Capture             |
|----------------------------------------------------------|------------------------------|----------------|-------------------|---------------------|
| 26.3, 267 mods (Sodium, EntityCulling, Bobby, EMF/ETF)   | 1.6 / 3.1 ms per frame       | 18.1 / 29.0 ms | 13 / 20           | 0.05 ms per entity  |
| 1.21.11, 172 mods (Sodium, Iris, EntityCulling, EMF/ETF) | 1.5 / 1.7 ms per frame       | 1.7 / 2.7 ms   | capped (59 / 59)  | 0.03 ms per entity  |

Holograms are now cheaper than the real entities they replace.

Focused bench (`bench` in the trigger file): one wall of 140 item frames with random items,
26.3 with 267 mods (Sodium, EntityCulling, ImmediatelyFast, EMF/ETF, MyTotemDoll). Frame time
is the render work per frame, without the frame limiter wait:

| Case                                           | Frame time | Cost of the frames |
|------------------------------------------------|------------|--------------------|
| Mod paused (nothing drawn)                     | 2.2 ms     | -                  |
| Real item frames (vanilla)                     | 7.9 ms     | 5.7 ms             |
| Holograms, items resolved again every frame (old) | 7.6 ms     | 5.4 ms             |
| Holograms, item states restored from a copy (now) | 4.4 ms     | 2.2 ms             |

Item render states of reused hologram states are restored from a shallow copy taken at extraction
(fields added by other mods included) instead of being resolved again every frame, which keeps
MyTotemDoll dolls stable at a fraction of the cost. Occlusion culling runs on its own
thread (about 6-15 ms per pass for 2,484 holograms, never blocking a frame) and roughly halves the
number of holograms drawn in that scene.

## License

MIT, see [LICENSE](LICENSE). Bundles LogisticsCraft OcclusionCulling (MIT).
