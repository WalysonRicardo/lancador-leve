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

    // Chave fixa: todo APK sai com a mesma assinatura, então dá para
    // atualizar por cima sem desinstalar (e sem perder a ordem da home).
    signingConfigs {
        create("fixa") {
            storeFile = file("lancador-leve.jks")
            storePassword = "lancadorleve"
            keyAlias = "lancador"
            keyPassword = "lancadorleve"
        }
    }

    buildTypes {
        release {
            // R8 encolhe e otimiza o APK: menos RAM e abertura mais rápida.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("fixa")
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
