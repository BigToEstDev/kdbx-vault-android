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

    // Remembers which database files were opened. Preferences rather than proto: one json string is the
    // whole schema, and a schema file would be more machinery than the list deserves
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}
