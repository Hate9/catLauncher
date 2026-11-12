/*
catLauncher: a categorised list launcher, forked from tmtLauncher
Copyright (C) 2025 Timothy Choi (tmtLauncher)
Copyright (C) 2026 catLauncher contributors

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Version from the nearest git tag so releases line up with what was tagged:
// v2.0.4 -> versionName "2.0.4", versionCode 20004 (major*10000 + minor*100 + patch).
// Untagged commits get "2.0.4-3-gabc1234"; -PversionName=... / -PversionCode=... override both.
val gitDescribe: String = providers.exec {
    commandLine("git", "describe", "--tags", "--match", "v*", "--always", "--dirty")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("").ifEmpty { "v0.0.0" }
val tagMatch = Regex("v(\\d+)\\.(\\d+)\\.(\\d+)(?:-(\\d+)-g[0-9a-f]+)?(-dirty)?").matchEntire(gitDescribe)
val derivedVersionName = tagMatch?.let { m ->
    val base = "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}"
    base + gitDescribe.removePrefix("v").removePrefix(base)
} ?: "0.0.0-$gitDescribe"
val derivedVersionCode = tagMatch?.let { m ->
    m.groupValues[1].toInt() * 10000 + m.groupValues[2].toInt() * 100 + m.groupValues[3].toInt()
} ?: 1

android {
    namespace = "io.github.hate9.catlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hate9.catlauncher"
        minSdk = 21
        targetSdk = 36
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: derivedVersionCode
        versionName = (findProperty("versionName") as String?) ?: derivedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment (set by the release workflow); without it the
    // release build stays unsigned, which is fine for local builds.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}