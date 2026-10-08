import java.io.File
import java.util.Properties

/**
 * versionCode source of truth: app/version.properties (bumped by CI, never by
 * hand). Falls back to 1001 if the file is missing or unparsable.
 */
fun loadVersionCode(versionFile: File): Int {
    if (!versionFile.exists()) return 1001
    val props = Properties()
    versionFile.inputStream().use { props.load(it) }
    return props.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1001
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.devrinth.launchpad"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    defaultConfig {
        applicationId = "com.devrinth.launchpad"
        minSdk = 26
        targetSdk = 35
        // versionCode comes from -PversionCodeOverride=N (CI pins it from the
        // commit trailer) or falls back to app/version.properties for local
        // IDE syncs. CI refuses to build without a trailer (except merges).
        versionCode = (project.findProperty("versionCodeOverride") as String?)?.toIntOrNull()
            ?: loadVersionCode(file("version.properties"))
        versionName = "1.3.0-custom"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Pinned debug keystore (app/debug.keystore, standard android/android
    // credentials) so every CI/local debug build shares one signature and
    // installs as an update instead of demanding an uninstall. Debug only --
    // release signing is untouched.
    signingConfigs {
        // AGP already creates a "debug" config -- just repoint it at the
        // pinned keystore instead of creating a second one.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    // Room components
    implementation("androidx.room:room-runtime:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.room:room-ktx:2.7.1")

    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.notkamui.libs:keval:1.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.airbnb.android:lottie:6.6.0")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}