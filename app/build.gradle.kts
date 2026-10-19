plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.wakesteps"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.wakesteps"
        minSdk = 28
        targetSdk = 36
        // Bump both together: a new feature is the next minor (1.13), a small fix or update the next
        // patch (1.12.1). versionCode is the version with two digits for minor and patch (1.12.1 ->
        // 11201, 1.13 -> 11300), so the two always match and every release installs over the
        // previous one. Before 1.12.1 it was major * 100 + minor (1.12 -> 112); before 1.4, 1-6.
        versionName = "1.12.1"
        versionCode = 11201
    }

    signingConfigs {
        // One key for every build, kept in the repo: Android only installs an update signed
        // with the same key as the installed app, and Google sign-in is tied to its fingerprint.
        // (It's the key all builds so far were signed with.)
        getByName("debug") {
            storeFile = rootProject.file("keystore/wakesteps.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the same key so a release build can be sideloaded directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Optional: point Robolectric at pre-downloaded android-all jars (offline / proxied machines).
            providers.gradleProperty("robolectricDepsDir").orNull?.let { dir ->
                test.systemProperty("robolectric.offline", "true")
                test.systemProperty("robolectric.dependency.dir", dir)
            }
        }
    }
}

base {
    // APK files come out as awake-steps-<version>-<buildType>.apk
    archivesName.set("awake-steps-${android.defaultConfig.versionName}")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
