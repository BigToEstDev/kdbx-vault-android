plugins {
    id("android-compose-app")
    // Builds rust/pass-ffi with cargo-ndk and puts the .so into src/main/jniLibs
    id("rust-bridge")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.kino.dev"

    defaultConfig {
        applicationId = "ru.kino.dev"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

//    buildTypes {
//        release {
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
//        }
//    }
}

dependencies {
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    compileOnly(libs.errorprone.annotations)

    implementation(projects.core.coreDomain)
    implementation(projects.core.corePres)
    // The native core: its Hilt module binds NativeCore to the jni implementation
    implementation(projects.core.coreData)
    // Feature data modules: nothing here calls them, but Hilt only sees the bindings of what the app depends on
    implementation(projects.vault.vaultData)
}