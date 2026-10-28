import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
// Local credentials are optional and never embedded in source or BuildConfig.
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
android {
    namespace = "com.vxplayer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vxplayer"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
    buildFeatures { compose = true }
    if (releaseSigningFile.exists()) {
        signingConfigs.create("localRelease") {
            fun required(name: String): String = releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }
                ?: throw GradleException("keystore.properties is missing required field: $name")
            storeFile = rootProject.file(required("storeFile"))
            require(storeFile?.isFile == true) { "Release signing keystore was not found." }
            storePassword = required("storePassword")
            keyAlias = required("keyAlias")
            keyPassword = required("keyPassword")
            releaseSigningProperties.getProperty("storeType")?.let { storeType = it }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("localRelease")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.effect)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.gson)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
