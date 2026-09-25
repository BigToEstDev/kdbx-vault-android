package ru.kino.dev.home

data class HomeState(
    val clicksCount: Int = 0,
    /** Version and build time of the native bridge, once it has answered. */
    val nativeBuildInfo: String? = null,
    val generatedPassword: String? = null,
    /** `kind: message` of the last failure reported by the core, see CoreException. */
    val nativeError: String? = null,
    /** A native call is in flight: the buttons show it instead of looking dead. */
    val isNativeBusy: Boolean = false,
)
