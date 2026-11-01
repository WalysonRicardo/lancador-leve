plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.leve.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.leve.launcher"
        minSdk = 24
        // O HY320 roda Android 11 (API 30). Mirar a mesma API evita
        // comportamentos novos que o aparelho nem conhece.
        targetSdk = 30
        // O GitHub Actions passa o número do build: cada APK novo atualiza o anterior.
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
        versionCode = 100 + build
        versionName = "0.2.$build"
    }

    // A chave fica nos Secrets do GitHub, nunca no repositório.
    // O Actions grava o arquivo e passa as senhas por variáveis de ambiente.
    // Sem elas (build no seu PC), o APK sai assinado com a chave de debug.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("fixa") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 encolhe e otimiza o APK: menos RAM e abertura mais rápida.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("fixa") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Cliente ADB em Kotlin puro: o launcher fala com o adbd do próprio
    // projetor (127.0.0.1:5555) para simular as teclas do controle.
    implementation("dev.mobile:dadb:1.2.10")
}
