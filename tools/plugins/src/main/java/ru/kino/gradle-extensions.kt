import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val JAVA_VERSION = JavaVersion.VERSION_17
val JVM_TARGET = JvmTarget.JVM_17
const val PUBLISH_MIN_SDK = 29
const val MIN_SDK = 29
const val COMPILE_SDK = 37
const val BUILD_TOOLS = "36.0.0"

val compilerArgs = listOf(
    "-Xcontext-parameters",
)

fun Project.getSafeName(): String = name.replace('-', '.')

fun Project.makeNamespace(): String = "$group.${getSafeName()}"