import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Removed kotlin.android - using AGP 9.0 built-in Kotlin support
    // The kotlin.compose plugin is still required for Compose compiler
    // See: https://blog.jetbrains.com/kotlin/2026/01/update-your-projects-for-agp9/
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 读取 local.properties
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86_64")
        }

        // Mapbox Access Token (运行时使用)
        val mapboxToken = localProperties.getProperty("MAPBOX_ACCESS_TOKEN", "")
        resValue("string", "mapbox_access_token", mapboxToken)

        // 高德定位 API Key
        val amapKey = localProperties.getProperty("AMAP_API_KEY", "")
        manifestPlaceholders["AMAP_API_KEY"] = amapKey

        // MQTT 配置 (EMQX Cloud)
        val mqttBrokerUrl = localProperties.getProperty("MQTT_BROKER_URL", "")
        val mqttUsername = localProperties.getProperty("MQTT_USERNAME", "")
        val mqttPassword = localProperties.getProperty("MQTT_PASSWORD", "")
        buildConfigField("String", "MQTT_BROKER_URL", "\"$mqttBrokerUrl\"")
        buildConfigField("String", "MQTT_USERNAME", "\"$mqttUsername\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"$mqttPassword\"")

        // 个推推送配置
        val getuiAppId = localProperties.getProperty("GETUI_APP_ID", "")
        val getuiAppKey = localProperties.getProperty("GETUI_APP_KEY", "")
        val getuiAppSecret = localProperties.getProperty("GETUI_APP_SECRET", "")
        manifestPlaceholders["GETUI_APP_ID"] = getuiAppId
        manifestPlaceholders["GETUI_APP_KEY"] = getuiAppKey
        manifestPlaceholders["GETUI_APP_SECRET"] = getuiAppSecret
        buildConfigField("String", "GETUI_APP_ID", "\"$getuiAppId\"")
        buildConfigField("String", "GETUI_APP_KEY", "\"$getuiAppKey\"")
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

    // Mapbox Maps SDK
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.compose)

    // 高德定位 SDK
    implementation(libs.amap.location)

    // 权限处理
    implementation(libs.accompanist.permissions)

    // 图片加载
    implementation(libs.coil.compose)

    // Firebase 已完全移除，使用 MQTT + Room + 个推 替代

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

    // 个推推送
    implementation(libs.getui.sdk)
    implementation(libs.getui.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}