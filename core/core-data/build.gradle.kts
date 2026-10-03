plugins {
    id("android-lib")
    // @Serializable of the ffi envelope and its dto-s needs the compiler plugin here, not only in domain
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.coreDomain)

    api(libs.hilt)
    ksp(libs.hilt.compiler)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)

    // String.toUri for the uris the storage access framework hands around
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
