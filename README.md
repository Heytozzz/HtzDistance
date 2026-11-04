# HtzDistance

Client-only Fabric mod that keeps rendering armor stands, item frames and glow item frames
as non-interactive "ghosts" after the server stops tracking them (they left the server's
entity tracking range), until the real entity comes back into range.

Status: work in progress. See [PLAN.md](PLAN.md).

## Building

```
./gradlew build
```

The project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build one jar per
Minecraft version from a single source tree.
