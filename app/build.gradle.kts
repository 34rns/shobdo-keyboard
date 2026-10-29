import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// version.properties at the repository root drives every release. The release script
// rewrites those two lines, so no version numbers are duplicated in Gradle files.
val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "0.1.0")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE", "1").trim().toInt()

// Signing material never lives in the repository. The release keystore and its password
// file sit in the maintainer's home directory; CI and contributors get an unsigned build.
// Environment variables win, so a CI job can inject its own keystore.
val keystorePropertiesFile = File(System.getProperty("user.home"), "keystores/shobdo-release.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(prop)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("SHOBDO_KEYSTORE_FILE", "storeFile")
    ?: File(System.getProperty("user.home"), "keystores/shobdo-release.jks").absolutePath
val releaseStorePassword = signingValue("SHOBDO_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("SHOBDO_KEY_ALIAS", "keyAlias") ?: "shobdo"
val releaseKeyPassword = signingValue("SHOBDO_KEY_PASSWORD", "keyPassword")
val releaseSigningReady = File(releaseStorePath).exists() && !releaseStorePassword.isNullOrBlank()

android {
    namespace = "app.shobdo.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.shobdo.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources {
            localeFilters += listOf("en", "bn")
        }
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = File(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseStorePassword
                // Scheme versions left to AGP: v2 for minSdk 26 and v3 for key rotation.
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    sourceSets {

    }
}

// Unit tests read the shipped JSON straight from the module so the fixtures test the real
// assets, not a copy.
tasks.withType<Test>().configureEach {
    systemProperty("shobdo.assets", layout.projectDirectory.dir("src/main/assets").asFile.absolutePath)
    systemProperty("shobdo.testResources", layout.projectDirectory.dir("src/test/resources").asFile.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
