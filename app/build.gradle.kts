plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.deepseek.safehub"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.deepseek.safehub"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    compileOnly(files("libs/api-82.jar"))
}
