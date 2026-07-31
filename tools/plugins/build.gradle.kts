plugins {
    alias(libs.plugins.kotlin.jvm)
    `kotlin-dsl`
//    id("org.gradle.kotlin.kotlin-dsl") version "6.5.2"
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.hilt.gradle)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.android.gradle)
    implementation(libs.android.gradle.api)
}
