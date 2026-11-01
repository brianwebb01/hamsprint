import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing is optional: CI supplies it through environment variables,
// a local build can use keystore.properties, and F-Droid builds unsigned and
// signs with its own key.
val signing: Map<String, String>? = run {
    val env = System.getenv()
    val file = rootProject.file("keystore.properties")
    when {
        env["HAMSPRINT_KEYSTORE"] != null -> mapOf(
            "storeFile" to env.getValue("HAMSPRINT_KEYSTORE"),
            "storePassword" to env.getValue("HAMSPRINT_KEYSTORE_PASSWORD"),
            "keyAlias" to env.getValue("HAMSPRINT_KEY_ALIAS"),
            "keyPassword" to env.getValue("HAMSPRINT_KEY_PASSWORD"),
        )
        file.exists() -> Properties().apply { file.inputStream().use(::load) }
            .let { p -> p.stringPropertyNames().associateWith { p.getProperty(it) } }
        else -> null
    }
}

android {
    namespace = "org.brianwebb.hamsprint"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.brianwebb.hamsprint"
        minSdk = 26
        targetSdk = 36
        // Bumped by .github/workflows/release.yml; keep as literals so F-Droid can read them.
        versionCode = 3
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = rootProject.file(signing.getValue("storeFile"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("keyAlias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            vcsInfo.include = false
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // F-Droid: no Google-encrypted dependency blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
