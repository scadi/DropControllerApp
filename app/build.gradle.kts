import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing data: keystore.properties in the repo root (ignored by git) or DROP_* environment variables.
// Without any of them the release APK stays unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

fun signingValue(propertyName: String, envName: String): String? =
    keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "DROP_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "DROP_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "DROP_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "DROP_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null


// Single source of the app version: change only this line. versionCode is derived (major * 10000 + minor * 100 + patch)
val appVersionName = "0.9.1"
val appVersionCode = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    major * 10000 + minor * 100 + patch
}

android {
    namespace = "com.example.dropcontroller"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.scadi.dropcontrollerv3"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Release signing is not configured (keystore.properties / DROP_* variables): " +
                        "the release APK will be unsigned.")
            }
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

    // APK file name: dropController-V3-<version>.apk (release) or dropController-V3-<version>-<build type>.apk
    applicationVariants.all {
        val suffix = if (buildType.name == "release") "" else "-${buildType.name}"
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "dropController-V3-$appVersionName$suffix.apk"
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// ----- Custom Gradle tasks (jobs) -----
// Tasks such as assembleDebug, testDebugUnitTest, lintDebug are created
// automatically by the Android Gradle Plugin. We add convenience wrappers
// with groups and descriptions that depend on the AGP tasks via dependsOn.

tasks.register("buildDebug") {
    group = "build"
    description = "Build debug APK (./gradlew buildDebug)"
    dependsOn("assembleDebug")
}

tasks.register("buildRelease") {
    group = "build"
    description = "Build release APK (./gradlew buildRelease)"
    dependsOn("assembleRelease")
}

tasks.register("runTests") {
    group = "test"
    description = "Run JVM unit tests (./gradlew runTests)"
    dependsOn("testDebugUnitTest")
}

tasks.register("runLint") {
    group = "lint"
    description = "Run Android Lint (./gradlew runLint)"
    dependsOn("lintDebug")
}

tasks.register("verify") {
    group = "verification"
    description = "Full verification: assembleDebug + lintDebug + testDebugUnitTest"
    dependsOn("assembleDebug", "lintDebug", "testDebugUnitTest")
}