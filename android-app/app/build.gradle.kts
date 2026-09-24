plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.knowmo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.knowmo.app"
        minSdk = 26
        targetSdk = 34
        // CI tag 发布时通过 -PversionName / -PversionCode 覆盖；本地构建用默认值
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.2.0"
    }

    // CI 通过环境变量注入 release keystore；本地无 keystore 时回退 debug 签名，
    // 保证 assembleRelease 在两种环境都能跑通（见 workflow 的 tag 发布流程）
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_FILE")
            if (!storeFilePath.isNullOrEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                // 本地无 release keystore：借用 debug 签名便于本地验证——但产物不可覆盖安装正式版
                // APK，静默回退会让人误装，构建时显式警告
                logger.lifecycle("WARNING: KEYSTORE_FILE 未配置，release 构建使用 debug 签名，产物无法覆盖安装正式版 APK")
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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

    // 官方词库打进 APK assets（2026-09-23 修复 #1：首次使用门槛）：wordbanks/ 仍是唯一数据源，
    // 构建/CI 校验照旧在仓库根的 CSV 上做；App 端「一键导入官方词库」从 assets 读同一批文件
    //（见 CustomBanks.importOfficial），家属不再需要去 GitHub 下载再传到手机。
    sourceSets {
        getByName("main") {
            assets.srcDirs("../../wordbanks")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.material3:material3")
    // Material 核心图标集（Star/Check/Close/KeyboardArrowUp/Info），显式声明避免依赖传递变化
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.foundation:foundation")
}
