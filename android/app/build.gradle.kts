plugins {
    id("com.android.application")
}

android {
    namespace = "app.gigflow.driver"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.rideofferreader.driver"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("rorVersionCode") as? String)?.toIntOrNull() ?: 3
        versionName = (project.findProperty("rorVersionName") as? String) ?: "0.2.0"
    }

    // Never commit signing keys. Stable signing identity comes from GitHub
    // Actions secrets, just like NightRider's signed pilot update channel.
    signingConfigs {
        create("distribution") {
            val signingFile = System.getenv("ROR_KEYSTORE_PATH")
            if (!signingFile.isNullOrBlank()) {
                storeFile = file(signingFile)
                storePassword = System.getenv("ROR_STORE_PASSWORD")
                keyAlias = System.getenv("ROR_KEY_ALIAS")
                keyPassword = System.getenv("ROR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // FileProvider for handing the downloaded update APK to the installer.
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
}

