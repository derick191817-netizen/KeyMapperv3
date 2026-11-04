plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.keymapper"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.example.keymapper"
        minSdk = 26
        targetSdk = 34
        versionCode = 82
        versionName = "0.14.66"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildTypes {
        debug {
            // Mantém símbolos úteis para diagnóstico no APK de desenvolvimento.
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // O plugin Compose para Kotlin 2.4.x administra a versão do compilador.
    // Não definir kotlinCompilerExtensionVersion aqui evita conflito de versões.
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Kotlin 2.4+: use the typed compilerOptions DSL for JVM bytecode targeting.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Shizuku: API de chamada e ContentProvider de integração.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    // Cliente ADB Android independente do Shizuku.
    // Fixar diretamente o AAR Android 2.1.4 evita a variante 2.1.5, compilada para Java 25
    // (class major 69), que o Jetifier/AGP 8.5.2 não consegue analisar.
    implementation("com.flyfishxu:kadb-android:2.1.4")
    // mDNS Android publicado e validado no Maven Central.
    implementation("com.flyfishxu:kadb-mdns-android:2.1.3")
    // Kadb 2.1.4 declara Bouncy Castle 1.84, anterior às correções de segurança 1.85.
    // Fixar a família inteira em 1.86 mantém os módulos criptográficos alinhados.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation("org.bouncycastle:bcutil-jdk18on:1.86")

    testImplementation("junit:junit:4.13.2")
}
