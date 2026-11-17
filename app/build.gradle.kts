plugins {
    alias(libs.plugins.android.application)
}

// Single source of the app version: change only this line. versionCode is derived (major * 10000 + minor * 100 + patch)
val appVersionName = "0.9.0"
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