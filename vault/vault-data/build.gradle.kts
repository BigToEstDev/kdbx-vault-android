plugins {
    id("android-lib")
}

dependencies {
    api(projects.vault.vaultDomain)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(projects.core.coreDomain))
}
