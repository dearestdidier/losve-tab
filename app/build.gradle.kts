import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.orchard.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.losve.tab"
        // Android 5.0. Compose 1.10 and later need 6.0, so the Compose BOM below stays on 1.9.
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // `./gradlew -Pdiag ...`: a build for one reporter's TV whose logcat shows no app lines;
        // see system/DiagLog.kt.
        val diag = project.hasProperty("diag")
        buildConfigField("boolean", "DIAG", diag.toString())
        if (diag) versionNameSuffix = "-diag"
    }

    // A call newer than minSdk fails the release build (lintVital runs with assembleRelease):
    // 1.74 shipped Context.getSystemService(Class), Android 6+, in the guard's onServiceConnected,
    // and the guard crashed on every bind on Android 5 (issues #1010 #1012 #1017 #1018).
    lint {
        fatal += "NewApi"
    }

    // A single tablet build. The TV "guard" flavor (no HOME filter, Home taken over through an
    // accessibility service) is gone together with the home guard itself.
    flavorDimensions += "home"
    productFlavors {
        create("standard") { dimension = "home" }
    }

    // The TVs only accept updates signed with the key Aerial was first installed with. That key
    // lives outside version control in local/ (see docs/HANDOFF.md); without it the build falls
    // back to this machine's debug key, which installs fresh but cannot update existing copies.
    val signingProps = rootProject.file("local/signing.properties")
    signingConfigs {
        if (signingProps.exists()) {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            create("aerial") {
                storeFile = rootProject.file("local/" + p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // java.time and friends on Android 5.x to 7.x.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Compose 1.9.5, the last release that runs on Android 5.0 (1.10 raised minSdk to 23).
    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.core:core-ktx:1.16.0")
    // Baseline Profile install (src/main/baseline-prof.txt) and its compile state in the diagnostics.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    // Aerial films. 1.8 is the last Media3 that runs on Android 5.0 (1.9 raised minSdk to 23).
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-datasource:1.8.1")
    implementation("androidx.media3:media3-database:1.8.1")
    // The LAN arrange page (a tiny web server), opened on the tablet with one button.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}

// The TV build injected .dm dex metadata for its in-app updater (a TV is never idle, so Android's
// background profile compilation never ran there). losve-tab is installed as a normal APK on a
// tablet, so the standard baseline profile inside the APK is enough and that task is gone.
