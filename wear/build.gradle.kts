plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val samsungAar = file("libs/samsung-health-sensor-api.aar")

android {
    namespace = "com.skhealth.guardian.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.skhealth.guardian.wear"
        minSdk = 30
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
        buildConfigField("boolean", "HAS_SAMSUNG_SDK", samsungAar.exists().toString())
    }
    buildFeatures { buildConfig = true }
    sourceSets["main"].java.srcDirs("src/main/java")
    if (samsungAar.exists()) sourceSets["main"].java.srcDir("src/samsung/java")
    else sourceSets["main"].java.srcDir("src/mock/java")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    if (samsungAar.exists()) implementation(files(samsungAar))
}
