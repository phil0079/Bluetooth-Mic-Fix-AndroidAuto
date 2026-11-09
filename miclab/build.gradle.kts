plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.btmicfix.miclab"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.btmicfix.miclab"
        minSdk = 31
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2.$versionCode"
        buildConfigField("String", "SOURCE_REVISION", "\"${System.getenv("GITHUB_SHA")?.take(12) ?: "local"}\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        // CI compiles unsigned; the laptop applies a persistent private test key.
        getByName("release") { isDebuggable = true; isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
