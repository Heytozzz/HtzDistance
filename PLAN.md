# HtzDistance — Plan

Client-only Fabric mod that keeps rendering armor stands, item frames and glow item
frames as non-interactive "ghosts" after the server stops tracking them, until the real
entity comes back into range.

---

## 1. Research results (verified 2026-11-04)

### 1.1 Target Minecraft versions (from `meta.fabricmc.net`, stable only)

| MC        | Latest Fabric API     | Obfuscated? | Java | World render hook in Fabric API |
|-----------|-----------------------|-------------|------|---------------------------------|
| 1.21.8    | 0.136.1+1.21.8        | yes         | 21   | `rendering.v1.WorldRenderEvents` (old API) |
| 1.21.10   | 0.138.4+1.21.10       | yes         | 21   | `rendering.v1.world.WorldRenderEvents` (new, extraction/submit split) |
| 1.21.11   | 0.141.6+1.21.11       | yes         | 21   | `rendering.v1.world.WorldRenderEvents` |
| 26.1–26.1.2 | 0.155.3+26.1.2      | **no**      | 25   | `rendering.v1.level.LevelRenderEvents` (renamed) |
| 26.2      | 0.161.0+26.2          | no          | 25   | `rendering.v1.level.LevelRenderEvents` |
| 26.3      | 0.165.2+26.3          | no          | 25   | `rendering.v1.level.LevelRenderEvents` |

(26.4 Fabric API builds exist but are out of scope.)

### 1.2 What changes across the range

- **Mappings / desobfuscation:** 26.1+ ships with Mojang's names in the jar; Loom plugin
  `net.fabricmc.fabric-loom` (no remapping, no `mappings` line). For 1.21.x we use
  `net.fabricmc.fabric-loom-remap` with `loom.officialMojangMappings()`. Result: **the
  same class/method names on every version** (Mojmap == official names), so most code is
  shared. Remaining differences are real Mojang renames between versions (handled with
  Stonecutter conditionals).
- **Entity save/load:** `ValueOutput` / `ValueInput` (`TagValueOutput` / `TagValueInput`)
  exist since 1.21.6, so the whole range uses the same snapshot API. 
- **Entity rendering:** 1.21.8 still uses the immediate `EntityRenderDispatcher.render(...)`
  with a `MultiBufferSource`. **1.21.9+** (so 1.21.10+ for us) moved to render-state extraction +
  `SubmitNodeCollector` (ordered command queue). This is the biggest code split:
  two `GhostRenderer` implementations (≤1.21.8 immediate, ≥1.21.10 submit).
- **Render event:** three different Fabric APIs. We hide this behind a tiny `RenderHook`
  adapter per version range. (1.21.9 has no render event at all → **dropped from scope**.)
- **Java:** 21 bytecode for 1.21.x, 25 for 26.x. Your JDK 25 compiles both (`--release`).

### 1.3 Occlusion culling library

- EntityCulling (tr7zw Protective License) uses **`com.logisticscraft:occlusionculling`**
  (0.0.8-SNAPSHOT), shaded and relocated into `dev.tr7zw.entityculling`.
- LogisticsCraft/OcclusionCulling is **MIT** (checked the LICENSE file) → we can bundle it.
  Artifact: `com.logisticscraft:occlusionculling:0.0.9-SNAPSHOT` on
  `repo.codemc.io/repository/maven-snapshots/`.
- We write **our own** `DataProvider` (block opacity lookup on the client level), our own
  worker thread and our own scheduling — no EntityCulling code.
- No conflict with EntityCulling: their copy is relocated into their own package and our
  ghosts never enter the level. We bundle the library with Fabric **jar-in-jar** (`include`),
  pinned to the exact snapshot build `0.0.8-20240302.230836-1` (the same version EntityCulling
  uses). If another mod bundles it un-relocated, Fabric Loader keeps a single copy; the API is
  tiny and stable.

### 1.4 Config library

- **YACL** has builds for every target (3.8.1+1.21.6 covers 1.21.8; 3.8.x for
  1.21.9/10/11; 3.9.x for 26.1/26.2/26.3). Cloth Config also exists but YACL is more
  actively updated for 26.x.
- **Mod Menu** for the entry point (compileOnly, optional at runtime).

### 1.5 Tracking-range facts (vanilla, unchanged by Spigot/Paper)

The server's tracker shows an entity when the **horizontal (X/Z) Euclidean distance**
player→entity is `<= range` (Spigot/Paper only change the `range` value per category).
Your measurements fit: armor stand ≈ 16, item frame ≈ 8. Defaults:
`armorStand = 16`, `itemFrame = 8`, `glowItemFrame = 8`, `margin = 1.5`.

---

## 2. Build system proposal: **Stonecutter 0.9.x** (one source tree)

**Why Stonecutter instead of one copied module per version (like htzinv):**
- One `src/` for all 6 targets; per-version differences are inline comments
  (`//? if >=1.21.10 {` … `//?}`) that Stonecutter swaps when building each version.
  Bug fixes happen once.
- Since all targets use the same names (Mojmap/official), the conditional blocks are few:
  render hook, renderer (immediate vs submit), and a few renames.
- Builds every version with one command (`chiseledBuild`) and gives one jar per version.
- Handles the two Loom plugins: the 1.21.x nodes apply `fabric-loom-remap`, the 26.x
  nodes apply `fabric-loom` (decided in the shared build script from the node version).
  If Stonecutter can't do that cleanly I fall back to two build scripts selected per node
  — I'll verify this in Step 1 before writing mod code.

**Jars produced** (1.21.9 dropped by decision):
`1.21.8`, `1.21.10`, `1.21.11`, `26.1.2` (declares `>=26.1 <=26.1.2`),
`26.2`, `26.3`.

---

## 3. Architecture

```
com.heytozzz.htzdistance
├─ HtzDistanceClient           ClientModInitializer: wiring, lifecycle events
├─ config/                     HtzConfig (JSON, Gson), YACL screen, ModMenu entry
├─ capture/
│   ├─ GhostSnapshot           type, pos, yRot/xRot, direction, CompoundTag state, map data
│   ├─ SnapshotFactory         Entity → snapshot via TagValueOutput
│   └─ RangeClassifier         "left range" vs "destroyed" decision + learning mode
├─ ghost/
│   ├─ GhostStore              per server+dimension list, spatial index, limits
│   ├─ GhostReconciler         add-entity match (<0.5 blocks), in-range timeout, cleanup
│   └─ GhostInstanceCache      client-only Entity instances built from snapshots (not in level)
├─ render/
│   ├─ RenderHook (per version) Fabric event → GhostRenderer
│   └─ GhostRenderer (2 impls)  immediate (≤1.21.8) / submit (≥1.21.10), tint/transparency
├─ culling/
│   ├─ LevelDataProvider       our DataProvider for OcclusionCulling
│   └─ CullingWorker           background thread, publishes visible-set
├─ persist/                    Bobby-style: .minecraft/htzdistance/<server>/<dimension>/ghosts.nbt
└─ mixin/
    ├─ ClientPacketListenerMixin  handleRemoveEntities (HEAD) + handleAddEntity (TAIL)
```

Key rules:
- **Capture** at `HEAD` of `handleRemoveEntities` (runs on the main thread after
  `PacketUtils.ensureRunningOnSameThread`; we inject after that call so we're on the client
  thread and the entities still exist).
- **Out of range vs destroyed:** horizontal distance `d` at removal; save as ghost only if
  `d >= threshold - margin`. Otherwise it was destroyed/killed → drop.
- **Ghosts are never added to `ClientLevel`** → no picking, no `interact` packets, no ticking.
  Instances are constructed with the level reference only to satisfy constructors.
- **Reconciliation:** real add of same type within 0.5 blocks removes the ghost;
  if player is within `threshold - margin` for N seconds (default 5 s) and nothing
  reappears → ghost removed; full clear on dimension change / disconnect.
- **Maps:** store the map id + 128×128 color array (+ decorations skipped); on load,
  inject into the client level's map data only if the client doesn't already have it.
- **Learning mode:** records removal distances where the player was moving away and the
  distance was the max seen; uses a robust estimate (median of recent samples) per type.

---

## 4. Detailed checklist

### Step 1 — Project scaffold (Stonecutter + 1.21.8 node only)
- [x] `git init`, `.gitignore`, MIT LICENSE, README stub
- [x] Gradle wrapper (version compatible with Loom 1.19 + JDK 25)
- [x] `settings.gradle.kts` with Stonecutter, only node `1.21.8` active for now
- [x] `build.gradle.kts` (loom-back-compat + Mojang mappings, Fabric API, Mod Menu; YACL added in Step 8)
- [x] `fabric.mod.json` (environment client), mixins json, empty `HtzDistanceClient`
- [x] `./gradlew build` OK, `runClient` starts → commit

### Step 2 — Config core (no UI yet)
- [x] `HtzConfig` POJO + JSON load/save (thresholds per type, margin, enable per type,
      max render distance, max ghosts, persistence, culling, tint/alpha, learning mode,
      in-range timeout)

### Step 3 — Capture
- [x] `ClientPacketListenerMixin#handleRemoveEntities` capture
- [x] `SnapshotFactory` (TagValueOutput with registry access) for the 3 types
- [x] `RangeClassifier` (horizontal distance, margin)
- [x] Debug log line per capture/drop (English), toggleable

### Step 4 — Ghost store + reconciliation
- [x] `GhostStore` with limits (max count, evict farthest)
- [x] Add-entity match (<0.5 blocks, same type)
- [x] In-range timeout removal
- [x] Clear on dimension change / disconnect

### Step 5 — Rendering (1.21.8)
- [x] Rebuild client-only entity from snapshot (`EntityType.create` + `load(TagValueInput)`),
      cached per ghost
- [x] `WorldRenderEvents` hook → `EntityRenderDispatcher.render` with camera offset, light
- [x] Max render distance; tint/transparency option
- [x] Automated client game test with server-like ranges (16/8) + screenshots
- [ ] In-game test on your server (user)

### Step 6 — Maps in item frames
- [ ] Capture map colors at removal; restore into client map data when missing

### Step 7 — Occlusion culling
- [x] Add OcclusionCulling via jar-in-jar (MIT license ships inside its jar)
- [x] `LevelDataProvider`, `CullingWorker` thread, visible-set publication, toggle

### Step 8 — Config screen
- [x] YACL screen with all options + Mod Menu entry, English lang file (+ Spanish)
      (YACL is optional at runtime; it does not require Fabric Language Kotlin)

### Step 9 — Persistence (optional)
- [x] Bobby-style per server/dimension NBT save on disconnect/dimension change, load on join
      (`<game dir>/htzdistance/<server>/<dimension>/ghosts.dat`, async writes, 5-min autosave,
      files from another data version are ignored)

### Step 10 — Learning mode
- [ ] Sample collection, estimate, apply to thresholds, show learned values in config

### Step 11 — Multiversion
- [ ] 1.21.10 / 1.21.11 (new `world.WorldRenderEvents`, submit renderer)
- [ ] 26.1.x / 26.2 / 26.3 (non-remap Loom, `level.LevelRenderEvents`, Java 25)
- [ ] `chiseledBuild` produces all jars; smoke test each in a dev client

### Step 12 — Polish
- [ ] README (features, config, compatibility notes with EntityCulling), CI optional
