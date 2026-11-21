plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    // Reads app/google-services.json (committed; see CLAUDE.md) and generates default_web_client_id
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.javargasp.tiominutermvi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.javargasp.tiominutermvi"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Published content (content/v1 on Firebase Hosting), synced at start; HTTPS only
        buildConfigField("String", "CONTENT_BASE_URL", "\"https://tiominuter-e43f7.web.app/content/v1/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The content lives at the repository root (content/v1, also published to Firebase
    // Hosting). The APK bundles it at the same asset paths as before: exercises.json,
    // animations/<name>.gif, ... (see content/README.md).
    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf("../content/v1"))
        }
    }
    androidResources {
        // manifest.json is generated for Hosting by scripts/build_manifest.py: never in the APK
        ignoreAssetsPatterns += "!manifest.json"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.exifinterface)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // Identity only (Phase 1): Firebase Auth + Sign in with Google through Credential Manager
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
// Kotlin ↔ Dart parity files (shared/golden, CLAUDE.md "Parity"): the golden tests check them in
// every unit-test run; -Pgolden.update=true (scripts/update_golden.sh) rewrites them instead.
tasks.withType<Test>().configureEach {
    val update = providers.gradleProperty("golden.update").orElse("false")
    inputs.property("goldenUpdate", update)
    inputs.dir(rootProject.file("shared/golden")).withPropertyName("golden").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("golden.update", update.get())
}
