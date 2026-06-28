plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.circuitcurios.copyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.circuitcurios.copyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
