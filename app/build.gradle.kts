import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun config(name: String) = providers.environmentVariable(name)
    .orElse(localProperties.getProperty(name, ""))
    .get()

android {
    namespace = "jp.familoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.familoc"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-poc"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"${config("FAMILOC_API_BASE_URL")}\"")
        buildConfigField("String", "DEVICE_TOKEN", "\"${config("FAMILOC_DEVICE_TOKEN")}\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
}

