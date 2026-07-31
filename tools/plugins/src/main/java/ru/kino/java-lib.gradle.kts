plugins {
    kotlin("jvm")
    id("java-library")
}

kotlin {
    jvmToolchain(JAVA_VERSION.majorVersion.toInt())
    compilerOptions {
        jvmTarget.set(JVM_TARGET)
        freeCompilerArgs.addAll(compilerArgs)
    }
}
