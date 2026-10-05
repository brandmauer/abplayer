import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Подпись релиза: если рядом с проектом лежит keystore.properties — подписываем своим ключом,
// иначе (чтобы APK всё равно устанавливался) — стандартным отладочным ключом.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.brandmauer.abplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.brandmauer.abplayer"
        minSdk = 25
        targetSdk = 36
        versionCode = 17
        versionName = "1.0.16"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // R8: удаляет неиспользуемый код библиотек и сжимает ресурсы
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // имя файла: <имя проекта>.apk (его же копирует задача ниже на сетевой диск)
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "${rootProject.name}.apk"
        }
    }

    tasks.whenTaskAdded {
        if (name == "assembleRelease") {
            doLast {
                val apkName = "${rootProject.name}.apk"
                val apkFile = layout.buildDirectory.file("outputs/apk/release/$apkName").get().asFile
                val targetFolder = file("//SERVER/ftp")
                val targetFile = file("${targetFolder.absolutePath}/$apkName")

                if (apkFile.exists()) {
                    if (!targetFolder.exists()) {
                        targetFolder.mkdirs()
                    }
                    apkFile.copyTo(targetFile, overwrite = true)
                    println("SUCCESS: Релиз скопирован на сетевой диск: ${targetFile.absolutePath}")
                } else {
                    println("ERROR: Файл $apkName не найден по пути: ${apkFile.absolutePath}")
                }
            }
        }
    }

}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Воспроизведение: ExoPlayer + сессия/уведомление (foreground-сервис)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    // HLS-манифесты (.m3u8 как поток сегментов) для онлайн-радио
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("com.google.guava:guava:33.0.0-android")

    // Загрузка обложек альбомов (сохранённых как файлы в filesDir/covers) в Compose
    implementation("io.coil-kt:coil-compose:2.6.0")
}
