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

    // Plain renames, applied to the whole source tree.
    replacements {
        string(current.parsed >= "1.21.11") {
            replace("import net.minecraft.Util;", "import net.minecraft.util.Util;")
            replace("ResourceLocation", "Identifier")
            replace("dimension().location()", "dimension().identifier()")
        }
    }
}
