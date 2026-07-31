plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    kotlin("plugin.compose")
    id("com.google.dagger.hilt.android")
}

apply(plugin = "com.google.devtools.ksp")

kotlin {
    jvmToolchain(JAVA_VERSION.majorVersion.toInt())
    compilerOptions {
        jvmTarget.set(JVM_TARGET)
        freeCompilerArgs.addAll(compilerArgs)
    }
}

hilt {
    enableAggregatingTask = true
}

android {
    namespace = makeNamespace()

    compileSdk = COMPILE_SDK
    buildToolsVersion = BUILD_TOOLS

    defaultConfig {
        minSdk = MIN_SDK
    }

    compileOptions {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = false
    }

    lint {
        checkDependencies = true
    }
}
