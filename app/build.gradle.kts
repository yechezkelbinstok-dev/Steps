plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** The one place to bump the version. */
val appVersion = "1.9"

android {
    namespace = "app.wakesteps"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.wakesteps"
        minSdk = 28
        targetSdk = 36
        versionName = appVersion
        // Derived from the version (1.4 -> 104) so the two always match and every release
        // installs over the previous one. Builds before 1.4 used 1-6.
        versionCode = appVersion.split(".").let { (major, minor) -> major.toInt() * 100 + minor.toInt() }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so a release build can be sideloaded directly.
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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
