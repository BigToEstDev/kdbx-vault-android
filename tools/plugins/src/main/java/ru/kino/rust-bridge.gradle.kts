import java.io.File
import java.util.Properties

/**
 * Builds the Rust bridge (`rust/pass-ffi`) with cargo-ndk and drops the resulting `.so` into
 * `src/main/jniLibs`, from where the Android plugin packages it into the apk.
 *
 * The task runs before the build that needs it, so `assembleDebug` alone is enough to get a fresh
 * library - forgetting to rebuild the native side by hand is exactly the kind of bug that costs an
 * afternoon. `buildInfo()` in the bridge reports the build timestamp for the same reason.
 *
 * The ndk path is resolved here rather than taken from the environment: `cargo-ndk` needs
 * `ANDROID_NDK_HOME`, and requiring every developer to set it is a trap. `local.properties` and the
 * usual environment variables are all accepted, and the failure message says what to do.
 */

// Kept in sync with the crate's own Cargo.toml. Both ABIs are built: arm64 for every current device,
// armeabi-v7a for older 32-bit ones.
val rustAbis = listOf("arm64-v8a", "armeabi-v7a")

// The ndk that cargo-ndk links against. Pinned on purpose: a silent bump of the ndk changes the
// compiler and the linker under our feet.
val rustNdkVersion = "28.2.13676358"

val crateDir = rootProject.layout.projectDirectory.dir("rust/pass-ffi").asFile

// Each build type gets its own directory under build/, wired into that variant's jniLibs below. A single
// shared directory (src/main/jniLibs) would mean the last cargo run wins: build release, then debug, and
// the apk quietly carries whichever .so was produced last.
fun jniLibsDirOf(buildType: String): File =
    layout.buildDirectory.dir("rustJniLibs/$buildType").get().asFile

fun resolveNdkDir(): File {
    val candidates = mutableListOf<String?>()

    // An explicitly set ndk wins: a developer who points at another one means it
    candidates += System.getenv("ANDROID_NDK_HOME")
    candidates += System.getenv("ANDROID_NDK_ROOT")

    val localProperties = rootProject.file("local.properties")
    val sdkFromProperties = if (localProperties.exists()) {
        Properties().apply { localProperties.inputStream().use { load(it) } }.getProperty("sdk.dir")
    } else {
        null
    }

    for (sdk in listOf(sdkFromProperties, System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))) {
        if (sdk != null) candidates += File(sdk, "ndk/$rustNdkVersion").path
    }

    val found = candidates.filterNotNull().map(::File).firstOrNull { it.isDirectory }
    return found ?: error(
        """
        The Android NDK $rustNdkVersion was not found, so the Rust bridge cannot be built.
        Install it through the SDK manager, or point ANDROID_NDK_HOME at the ndk you want to use.
        Looked at: ${candidates.filterNotNull().joinToString()}
        """.trimIndent()
    )
}

fun registerCargoTask(buildType: String, release: Boolean) = tasks.register<Exec>(
    "cargoBuild${buildType.replaceFirstChar { it.uppercase() }}"
) {
    group = "rust"
    description = "Builds pass-ffi for ${rustAbis.joinToString()} ($buildType)"

    val jniLibsDir = jniLibsDirOf(buildType)

    workingDir = crateDir

    // Rebuilt when the crate or the core changes; the core is a path dependency, so cargo sees it too
    inputs.dir(crateDir.resolve("src"))
    inputs.file(crateDir.resolve("Cargo.toml"))
    inputs.file(crateDir.resolve("build.rs"))
    inputs.dir(rootProject.file("../pass-rust-core/src"))
    outputs.dir(jniLibsDir)

    doFirst {
        environment("ANDROID_NDK_HOME", resolveNdkDir().absolutePath)
        // cargo-ndk lays the .so out per abi under the output directory
        jniLibsDir.mkdirs()
    }

    val abiArgs = rustAbis.flatMap { listOf("-t", it) }
    val buildArgs = if (release) listOf("build", "--release") else listOf("build")
    commandLine(listOf("cargo", "ndk") + abiArgs + listOf("-o", jniLibsDir.absolutePath) + buildArgs)
}

val cargoBuildDebug = registerCargoTask("debug", release = false)
val cargoBuildRelease = registerCargoTask("release", release = true)

// preDebugBuild / preReleaseBuild are the earliest per-variant hooks that exist for every variant, so
// the native library is in place before anything looks at jniLibs
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(cargoBuildDebug) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(cargoBuildRelease) }

// Configured through the AGP extension by type, because a precompiled script plugin that does not apply
// the Android plugin itself has no `android { }` accessor.
plugins.withId("com.android.application") {
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        // Package only the ABIs we actually build: a leftover .so of a dropped abi would otherwise
        // travel in the apk unnoticed
        defaultConfig {
            ndk {
                abiFilters += rustAbis
            }
        }

        // Each variant picks up the libraries built for its own build type
        sourceSets.getByName("debug").jniLibs.srcDir(jniLibsDirOf("debug"))
        sourceSets.getByName("release").jniLibs.srcDir(jniLibsDirOf("release"))
    }
}
