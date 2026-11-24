// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

// Builds the debug and release APKs and collects them in build/dist/ (names come from app/build.gradle.kts).
tasks.register<Sync>("buildApks") {
    group = "build"
    description = "Builds debug and release APKs into build/dist/"
    dependsOn(":app:assembleDebug", ":app:assembleRelease")
    from(project(":app").layout.buildDirectory.dir("outputs/apk")) {
        include("debug/*.apk", "release/*.apk")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("dist"))
}
