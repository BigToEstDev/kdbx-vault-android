plugins {
    id("android-lib")
}

dependencies {
    api(projects.core.coreDomain)

    api(libs.hilt)
    ksp(libs.hilt.compiler)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
}
