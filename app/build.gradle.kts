plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.axiomstead.foldsms"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.axiomstead.foldsms"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // CI signs debug APKs with one stored key so phone updates keep the same signature.
    // Without FOLDSMS_KEYSTORE_FILE (local builds, forks), Android's per-machine debug key is used.
    val sharedKeystore = System.getenv("FOLDSMS_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }
    signingConfigs {
        if (sharedKeystore != null) create("sharedDebug") {
            storeFile = sharedKeystore
            storePassword = System.getenv("FOLDSMS_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("FOLDSMS_KEY_ALIAS") ?: "foldsms"
            keyPassword = System.getenv("FOLDSMS_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        debug { if (sharedKeystore != null) signingConfig = signingConfigs.getByName("sharedDebug") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

dependencies {
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(bom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
