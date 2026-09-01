plugins {
    id("android-compose-app")
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

    implementation(projects.core.coreDomain)
    implementation(projects.core.corePres)
}