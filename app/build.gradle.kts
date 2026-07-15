import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

fun pixelDoneConfigValue(vararg candidates: Pair<String, String>): String {
    for ((gradleName, localName) in candidates) {
        providers.gradleProperty(gradleName).orNull?.let { return it }
        localProperties.getProperty(localName)?.let { return it }
        System.getenv(gradleName)?.let { return it }
    }
    return ""
}

fun pixelDoneBooleanConfigValue(gradleName: String, localName: String): Boolean =
    pixelDoneConfigValue(gradleName to localName).trim().equals("true", ignoreCase = true)

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val releaseSigningPropertiesFile = rootProject.file("signing/release-signing.properties")
val releaseSigningProperties = Properties()
val releaseSigningRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

if (releaseSigningPropertiesFile.isFile) {
    releaseSigningPropertiesFile.inputStream().use(releaseSigningProperties::load)
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    releaseSigningProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)
        ?: System.getenv(environmentName)?.takeIf(String::isNotBlank)

val releaseSigningStoreFile = releaseSigningValue(
    "storeFile",
    "PIXELDONE_ANDROID_KEYSTORE_PATH",
)
val releaseSigningStorePassword = releaseSigningValue(
    "storePassword",
    "PIXELDONE_ANDROID_STORE_PASSWORD",
)
val releaseSigningKeyAlias = releaseSigningValue(
    "keyAlias",
    "PIXELDONE_ANDROID_KEY_ALIAS",
)
val releaseSigningKeyPassword = releaseSigningValue(
    "keyPassword",
    "PIXELDONE_ANDROID_KEY_PASSWORD",
)
val releaseSigningConfigured = listOf(
    releaseSigningStoreFile,
    releaseSigningStorePassword,
    releaseSigningKeyAlias,
    releaseSigningKeyPassword,
).all { !it.isNullOrBlank() }
val releaseSigningPartiallyConfigured = releaseSigningPropertiesFile.isFile || listOf(
    "PIXELDONE_ANDROID_KEYSTORE_PATH",
    "PIXELDONE_ANDROID_STORE_PASSWORD",
    "PIXELDONE_ANDROID_KEY_ALIAS",
    "PIXELDONE_ANDROID_KEY_PASSWORD",
).any { !System.getenv(it).isNullOrBlank() }

if ((releaseSigningRequested || releaseSigningPartiallyConfigured) && !releaseSigningConfigured) {
    throw org.gradle.api.GradleException(
        "Release signing requires storeFile/storePassword/keyAlias/keyPassword in " +
            "${releaseSigningPropertiesFile.absolutePath} or the corresponding " +
            "PIXELDONE_ANDROID_* environment variables."
    )
}

val supabaseUrl = pixelDoneConfigValue(
    "PIXELDONE_SUPABASE_URL" to "pixeldone.supabaseUrl",
)
val supabasePublishableKey = pixelDoneConfigValue(
    "PIXELDONE_SUPABASE_PUBLISHABLE_KEY" to "pixeldone.supabasePublishableKey",
    "PIXELDONE_SUPABASE_ANON_KEY" to "pixeldone.supabaseAnonKey",
)
val requireCloudConfig = pixelDoneBooleanConfigValue(
    "PIXELDONE_REQUIRE_CLOUD_CONFIG",
    "pixeldone.requireCloudConfig",
)
// Formal PixelDone releases must support the current direct-IP HTTP Supabase endpoint.
val allowInsecureSupabaseHttp = true

if (requireCloudConfig && (supabaseUrl.isBlank() || supabasePublishableKey.isBlank())) {
    throw org.gradle.api.GradleException(
        "PIXELDONE_REQUIRE_CLOUD_CONFIG=true requires PIXELDONE_SUPABASE_URL and PIXELDONE_SUPABASE_PUBLISHABLE_KEY."
    )
}


android {
    namespace = "com.milesxue.pixeldone"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.milesxue.pixeldone"
        minSdk = 26
        targetSdk = 37
        versionCode = 84
        versionName = "3.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigString(supabaseUrl),
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            buildConfigString(supabasePublishableKey),
        )
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(requireNotNull(releaseSigningStoreFile))
                storePassword = requireNotNull(releaseSigningStorePassword)
                keyAlias = requireNotNull(releaseSigningKeyAlias)
                keyPassword = requireNotNull(releaseSigningKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "UPDATE_CHANNEL", "\"beta\"")
            buildConfigField("Boolean", "ALLOW_INSECURE_SUPABASE_HTTP", "true")
            resValue("bool", "allow_insecure_supabase_http", "true")
        }
        release {
            isDebuggable = false
            buildConfigField("String", "UPDATE_CHANNEL", "\"formal\"")
            buildConfigField("Boolean", "ALLOW_INSECURE_SUPABASE_HTTP", allowInsecureSupabaseHttp.toString())
            resValue("bool", "allow_insecure_supabase_http", allowInsecureSupabaseHttp.toString())
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

@org.gradle.work.DisableCachingByDefault(because = "Copies the already-built debug APK to its release asset name.")
abstract class CopyVersionedApkTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val inputApk: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputApk: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun copyApk() {
        inputApk.get().asFile.copyTo(outputApk.get().asFile, overwrite = true)
    }
}

val versionedDebugApkName = "PixelDone-${android.defaultConfig.versionName}-debug.apk"
val debugApkOutputDir = layout.buildDirectory.dir("outputs/apk/debug")
val defaultDebugApk = debugApkOutputDir.map { it.file("app-debug.apk") }
val versionedDebugApk = debugApkOutputDir.map { it.file(versionedDebugApkName) }
val versionedReleaseApkName = "PixelDone-${android.defaultConfig.versionName}-release.apk"
val releaseApkOutputDir = layout.buildDirectory.dir("outputs/apk/release")
val defaultReleaseApk = releaseApkOutputDir.map { it.file("app-release.apk") }
val versionedReleaseApk = releaseApkOutputDir.map { it.file(versionedReleaseApkName) }

val copyVersionedDebugApk = tasks.register<CopyVersionedApkTask>("copyVersionedDebugApk") {
    dependsOn("assembleDebug")
    inputApk.set(defaultDebugApk)
    outputApk.set(versionedDebugApk)
}

val copyVersionedReleaseApk = tasks.register<CopyVersionedApkTask>("copyVersionedReleaseApk") {
    dependsOn("assembleRelease")
    inputApk.set(defaultReleaseApk)
    outputApk.set(versionedReleaseApk)
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyVersionedDebugApk)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyVersionedReleaseApk)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
