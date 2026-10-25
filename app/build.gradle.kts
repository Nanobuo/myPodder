import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Credenziali Podcast Index: variabili d'ambiente (CI) oppure local.properties (build locale).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String =
    System.getenv(name) ?: localProps.getProperty(name) ?: (findProperty(name) as String?) ?: ""

android {
    namespace = "com.nanobuo.mypodder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nanobuo.mypodder"
        minSdk = 26
        targetSdk = 36
        versionCode = 10500
        versionName = "1.5.0"

        buildConfigField("String", "PODCASTINDEX_KEY", "\"${secret("PODCASTINDEX_KEY")}\"")
        buildConfigField("String", "PODCASTINDEX_SECRET", "\"${secret("PODCASTINDEX_SECRET")}\"")
    }

    // Chiave di debug fissa (pubblica, come ogni chiave di debug): ogni build di CI ha la stessa
    // firma, così l'APK si aggiorna sopra la versione già installata invece di andare in conflitto.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Chiave di release: arriva dai Secrets di GitHub (o da local.properties), mai dalla repo.
        val releaseStore = secret("RELEASE_KEYSTORE_FILE")
        if (releaseStore.isNotBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD").ifBlank { secret("RELEASE_KEYSTORE_PASSWORD") }
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.reorderable)
    implementation(libs.materialkolor)
    implementation(libs.documentfile)

    testImplementation(libs.junit)
}
