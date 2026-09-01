plugins {
    id("java-lib")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)
}
