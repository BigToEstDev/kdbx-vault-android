plugins {
    id("android-compose-lib")
}

android {
    buildFeatures {
        resValues = false
        viewBinding = true
    }
}

dependencies {
    api(projects.core.coreDomain)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    api(libs.hilt.navigation.compose)
    api(libs.hilt.lifecycle.viewmodel.compose)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)

    api(libs.orbit.core)
    api(libs.orbit.viewmodel)
    api(libs.orbit.compose)

    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.compose.ui.tooling.preview)
    debugApi(libs.androidx.compose.ui.tooling)
}