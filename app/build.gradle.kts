plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.agendawa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agendawa.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "2.7.1"
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}
