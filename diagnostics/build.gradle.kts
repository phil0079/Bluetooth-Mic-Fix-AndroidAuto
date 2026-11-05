plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btmicfix.diagnostics"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.btmicfix.diagnostics"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-diagnostics"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.car.app:app:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
