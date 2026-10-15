plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.reportalo24.generador"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reportalo24.generador"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.8.0"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("AGENDAWA_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("AGENDAWA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AGENDAWA_KEY_ALIAS")
                keyPassword = System.getenv("AGENDAWA_KEY_PASSWORD")
            } else {
                // Llave fija del repositorio: así cada versión nueva se instala encima de la anterior.
                // Antes de vender la app, reemplazarla por una llave privada guardada como secreto.
                storeFile = rootProject.file("signing/r24-apps.jks")
                storePassword = "r24apps2026"
                keyAlias = "r24apps"
                keyPassword = "r24apps2026"
            }
            // Firma clásica (v1) además de la moderna (v2): algunos instaladores de ciertas marcas
            // rechazan la APK como "paquete no válido" si solo trae la v2.
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("com.google.guava:guava:33.3.1-android")
}
