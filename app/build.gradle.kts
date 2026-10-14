plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.riglineaimstriker.junior"
    compileSdk = 36
    ndkVersion = "28.0.12433566"

    defaultConfig {
        applicationId = "com.riglineaimstriker.junior"
        minSdk = 26
        targetSdk = 36
        // versionCode растёт сам: 300 + номер запуска GitHub Actions.
        // Так каждая новая сборка ставится поверх старой без удаления.
        versionCode = 300 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "0.3.0"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Постоянный ключ подписи из репозитория (app/junior.keystore).
    // Раньше CI на каждой сборке создавал случайный debug-ключ, и Android
    // не давал обновить приложение без удаления. Теперь подпись всегда одна.
    val juniorKeystore = file("junior.keystore")
    val ks = System.getenv("JUNIOR_KEYSTORE")
    signingConfigs {
        getByName("debug") {
            storeFile = juniorKeystore
            storePassword = "junior-release"
            keyAlias = "junior"
            keyPassword = "junior-release"
        }
        create("junior") {
            storeFile = juniorKeystore
            storePassword = "junior-release"
            keyAlias = "junior"
            keyPassword = "junior-release"
        }
        // Свой ключ для релиза можно передать через переменные окружения.
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("JUNIOR_KS_PASS")
            keyAlias = System.getenv("JUNIOR_KEY_ALIAS")
            keyPassword = System.getenv("JUNIOR_KEY_PASS")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (ks != null) "release" else "junior")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // GGUF уже сжат квантованием: храним в APK без сжатия (быстрее сборка и копирование).
    androidResources { noCompress += "gguf" }
    packaging { jniLibs { useLegacyPackaging = false } }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    implementation(libs.androidx.documentfile)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
