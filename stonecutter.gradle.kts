plugins {
    id("dev.kikugie.stonecutter")
    // Both Loom variants; loom-back-compat applies the right one in each version node.
    id("net.fabricmc.fabric-loom-remap") version "1.19.3" apply false
    id("net.fabricmc.fabric-loom") version "1.19.3" apply false
}

stonecutter active "1.21.8"

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
}
