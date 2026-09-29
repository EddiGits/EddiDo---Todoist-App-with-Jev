import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Renders screens to PNG on the build machine (no emulator needed): ./gradlew recordPaparazziDebug
    id("app.cash.paparazzi") version "1.3.5"
}

val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String = "") = secrets.getProperty(name)?.trim().orEmpty().ifEmpty { default }

android {
    namespace = "com.eddigits.eddido"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eddigits.eddido"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "2.2.2"

        buildConfigField("String", "OPENROUTER_API_KEY", "\"${secret("OPENROUTER_API_KEY")}\"")
        buildConfigField("String", "TYPESAFE_API_KEY", "\"${secret("TYPESAFE_API_KEY")}\"")
        buildConfigField("String", "JEV_MODEL", "\"${secret("JEV_MODEL", "jev-latest")}\"")
        // Off by default: Jev decides, code computes (as in Shapeshift). Set USE_OPENROUTER=true to add the chat model back.
        buildConfigField("boolean", "USE_OPENROUTER", secret("USE_OPENROUTER", "false"))
        buildConfigField("String", "OPENROUTER_MODEL", "\"${secret("OPENROUTER_MODEL", "typesafe/jev-router")}\"")
    }

    signingConfigs {
        create("release") {
            val store = secret("RELEASE_STORE_FILE")
            if (store.isNotEmpty()) {
                storeFile = rootProject.file(store)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("RELEASE_STORE_FILE").isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM tests (Android's copy is only stubs there).
    testImplementation("org.json:json:20240303")
}
