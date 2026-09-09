plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.skhealth.guardian.mobile"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.skhealth.guardian.mobile"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val lepuAarPresent = listOf(
        file("libs/lepu-blepro-1.3.9.aar"),
        file("libs/lepu-blepro-1.3.7.aar")
    ).any { it.exists() }
    sourceSets.getByName("main").java.srcDir(
        if (lepuAarPresent) "src/lepuSdk/java" else "src/lepuStub/java"
    )
}

val lepuBleAar = listOf(
    file("libs/lepu-blepro-1.3.9.aar"),
    file("libs/lepu-blepro-1.3.7.aar")
).firstOrNull { it.exists() }

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("no.nordicsemi.android:ble:2.10.0")
    if (lepuBleAar != null) {
        implementation(files(lepuBleAar))
        implementation("com.github.michaellee123:LiveEventBus:1.8.14")
    }
}
