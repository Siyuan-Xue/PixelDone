plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.codexue.pixeldone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.codexue.pixeldone"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

val semanticDebugApkName = "PixelDone-${android.defaultConfig.versionName}-debug.apk"
val debugApkOutputDir = layout.buildDirectory.dir("outputs/apk/debug")
val semanticDebugApkOutputDir = layout.buildDirectory.dir("outputs/apk/semantic/debug")

val copySemanticDebugApk = tasks.register<Copy>("copySemanticDebugApk") {
    dependsOn("assembleDebug")
    from(debugApkOutputDir.map { it.file("app-debug.apk") })
    into(semanticDebugApkOutputDir)
    rename("app-debug.apk", semanticDebugApkName)
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copySemanticDebugApk)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
