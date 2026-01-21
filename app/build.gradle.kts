import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// 读取 local.properties
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

// 动态生成版本号（基于日期）
// versionCode: YYYYMMDD 格式，如 20260121
// versionName: YYYY.MM.DD 格式，如 2026.01.21
val buildDate = Date()
val versionCodeFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
val versionNameFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
val generatedVersionCode = versionCodeFormat.format(buildDate).toInt()
val generatedVersionName = versionNameFormat.format(buildDate)

android {
    namespace = "me.ikate.findmy"
    compileSdk = 36

    // Enable resValues feature for this module (replaces deprecated global setting)
    buildFeatures {
        resValues = true
    }

    defaultConfig {
        applicationId = "me.ikate.findmy"
        minSdk = 36
        targetSdk = 36
        versionCode = generatedVersionCode
        versionName = generatedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64")
        }

        // 腾讯地图 API Key 和签名密钥
        val tencentMapKey = localProperties.getProperty("TENCENT_MAP_KEY", "")
        val tencentMapSk = localProperties.getProperty("TENCENT_MAP_SK", "")
        manifestPlaceholders["TENCENT_MAP_KEY"] = tencentMapKey
        buildConfigField("String", "TENCENT_MAP_KEY", "\"$tencentMapKey\"")
        buildConfigField("String", "TENCENT_MAP_SK", "\"$tencentMapSk\"")

        // MQTT 配置 (EMQX Cloud)
        val mqttBrokerUrl = localProperties.getProperty("MQTT_BROKER_URL", "")
        val mqttUsername = localProperties.getProperty("MQTT_USERNAME", "")
        val mqttPassword = localProperties.getProperty("MQTT_PASSWORD", "")
        buildConfigField("String", "MQTT_BROKER_URL", "\"$mqttBrokerUrl\"")
        buildConfigField("String", "MQTT_USERNAME", "\"$mqttUsername\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"$mqttPassword\"")

        // 推送 Webhook URL (Cloudflare Worker) - 已弃用，保留兼容
        val pushWebhookUrl = localProperties.getProperty("PUSH_WEBHOOK_URL", "")
        buildConfigField("String", "PUSH_WEBHOOK_URL", "\"$pushWebhookUrl\"")

        // Firebase Cloud Functions URL
        // 格式: https://asia-northeast1-{project-id}.cloudfunctions.net
        val firebaseFunctionsUrl = localProperties.getProperty("FIREBASE_FUNCTIONS_URL", "")
        buildConfigField("String", "FIREBASE_FUNCTIONS_URL", "\"$firebaseFunctionsUrl\"")
    }

    signingConfigs {
        create("release") {
            // 使用 debug 密钥进行签名（自用应用）
            storeFile = signingConfigs.getByName("debug").storeFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
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
    }
    buildToolsVersion = "36.1.0"
}

// Room schema 导出目录 (KSP 配置需要放在 android 块外面)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

configurations.all {
    resolutionStrategy {
        // 强制使用存在的 concurrent-futures 版本
        force("androidx.concurrent:concurrent-futures:1.3.0")
        force("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // 腾讯地图 SDK（地图 + 定位）
    implementation(libs.tencent.map.vector.sdk)
    implementation(libs.tencent.location.sdk)

    // 权限处理
    implementation(libs.accompanist.permissions)

    // 图片加载
    implementation(libs.coil.compose)

    // Firebase 推送 (FCM) + Firestore
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Biometric (身份验证)
    implementation(libs.androidx.biometric)

    // Security Crypto (加密存储)
    implementation(libs.androidx.security.crypto)

    // MQTT 客户端 (Eclipse Paho)
    implementation(libs.paho.mqtt.client)

    // Room 数据库
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // JSON 序列化
    implementation(libs.gson)

    // Koin 依赖注入
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Google Play Services (Activity Recognition)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}