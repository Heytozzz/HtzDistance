plugins {
    // Picks the right Loom variant for the current Minecraft version (remap on 1.21.x, plain on 26.1+).
    id("dev.kikugie.loom-back-compat")
}

// Stonecutter sets the group per node; do not set it here.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    /** Restricts the given [groups] to a single maven to speed up resolution. */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://maven.terraformersmc.com/releases", "TerraformersMC", "com.terraformersmc")
    strictMaven("https://repo.codemc.io/repository/maven-snapshots", "CodeMC", "com.logisticscraft")
    strictMaven("https://maven.isxander.dev/releases", "Xander Maven", "dev.isxander")
    strictMaven("https://maven.quiltmc.org/repository/release", "Quilt", "org.quiltmc.parsers")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings on obfuscated versions, so every version uses the same names.
    loomx.applyMojangMappings()

    // Use mod* configurations even on 26.1+; loom-back-compat converts them.
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Occlusion culling library (MIT), bundled in the jar via jar-in-jar.
    // Pinned to an exact snapshot build for reproducible builds.
    val occlusionCulling = "com.logisticscraft:occlusionculling:${property("deps.occlusionculling")}"
    implementation(occlusionCulling)
    include(occlusionCulling)

    // Mod Menu is optional at runtime: compiled against for the config button, present in dev runs.
    modCompileOnly("com.terraformersmc:modmenu:${sc.properties.get<String>("deps.modmenu")}")
    modLocalRuntime("com.terraformersmc:modmenu:${sc.properties.get<String>("deps.modmenu")}")

    // YACL builds the config screen; optional at runtime (the JSON file works without it).
    // Compile against YACL alone; dev runs need its libraries as well, because Loom does not
    // load the copies nested inside its jar (players get them from that jar).
    val yacl = "dev.isxander:yet-another-config-lib:${sc.properties.get<String>("deps.yacl")}"
    modCompileOnly(yacl) { isTransitive = false }
    modLocalRuntime(yacl)
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    // Only the manual dev runs share ./run; the client game test keeps its own (cleared) directory.
    runConfigs.matching { it.name == "client" || it.name == "server" }.configureEach {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

// Self-test mod (src/selftest): a separate jar dropped into a real launcher instance to measure
// performance and check ghost rotations with the player's own mod list. Never part of the release
// jar, and it does nothing unless a trigger file exists in the game directory.
val selftest: SourceSet = sourceSets.create("selftest") {
    compileClasspath += sourceSets["main"].compileClasspath + sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].runtimeClasspath + sourceSets["main"].output
}
val selftestDevJar = tasks.register<Jar>("selftestDevJar") {
    from(selftest.output)
    archiveBaseName = "htzdistance-selftest"
    archiveVersion = sc.current.version
    archiveClassifier = "dev"
    destinationDirectory = layout.buildDirectory.dir("devlibs")
}
if (sc.current.parsed < "26.1") {
    // Obfuscated versions: remap the jar (and its mixin) to intermediary like the main jar.
    tasks.register<net.fabricmc.loom.task.RemapJarTask>("selftestJar") {
        inputFile = selftestDevJar.flatMap { it.archiveFile }
        archiveBaseName = "htzdistance-selftest"
        archiveVersion = sc.current.version
        destinationDirectory = layout.buildDirectory.dir("libs")
    }
} else {
    val selftestName = "htzdistance-selftest-${sc.current.version}.jar"
    tasks.register<Copy>("selftestJar") {
        from(selftestDevJar.flatMap { it.archiveFile })
        into(layout.buildDirectory.dir("libs"))
        rename { selftestName }
    }
}

// Automated in-game tests (src/gametest): singleplayer worlds driven by Fabric's client game test API.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "htzdistance-gametest"
        enableGameTests = false
        enableClientGameTests = true
        eula = false
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", project.version.toString())
            register("minecraft", sc.properties["mod.mc_compat"])
            register("loader", Regex("\\d+\\.\\d+").find(sc.properties.get<String>("deps.fabric_loader"))!!.value)
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        // Client mixins differ per version: the buffer-source styling and render type accessors
        // only exist for the immediate renderer (up to 1.21.8).
        val clientMixins = buildList {
            add("ClientPacketListenerMixin")
            if (sc.current.parsed < "1.21.10") {
                add("render.BufferSourceMixin")
                add("render.CompositeRenderTypeAccessor")
                add("render.CompositeStateAccessor")
                add("render.EmptyTextureStateShardInvoker")
            }
        }.joinToString(",\n    ") { "\"$it\"" }
        inputs.property("client_mixins", clientMixins)
        filesMatching("htzdistance.mixins.json") { expand("java" to mixinJava, "client_mixins" to clientMixins) }
    }

    // Ship the license inside the jar.
    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from(rootProject.file("LICENSE")) { rename { "${it}_$name" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jars and copies them to build/libs/<mod version>/"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}
