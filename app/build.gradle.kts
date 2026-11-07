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
        versionCode = 8
        versionName = "0.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // A test that hangs (stuck emulator, deadlock) otherwise stalls the CI job for its whole 30-minute limit with no
        // hint which test it was. With a per-test limit it fails with a stack trace and the test's name instead.
        testInstrumentationRunnerArguments["timeout_msec"] = "180000"
    }
    // CI signs debug and release APKs with one stored key so phone updates keep the same signature.
    // Without FOLDSMS_KEYSTORE_FILE (local builds, forks), Android's per-machine debug key is used.
    val sharedKeystore = System.getenv("FOLDSMS_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }
    signingConfigs {
        if (sharedKeystore != null) create("shared") {
            storeFile = sharedKeystore
            storePassword = System.getenv("FOLDSMS_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("FOLDSMS_KEY_ALIAS") ?: "foldsms"
            keyPassword = System.getenv("FOLDSMS_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        debug { if (sharedKeystore != null) signingConfig = signingConfigs.getByName("shared") }
        // The release build: not debuggable, unused library code and resources removed. It is signed with the same stored key,
        // so it installs over the debug builds already on the phone and the phone keeps its drafts and metadata.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (sharedKeystore != null) "shared" else "debug")
        }
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
