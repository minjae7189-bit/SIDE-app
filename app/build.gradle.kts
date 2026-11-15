import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Pleos Playground에서 발급받은 TTS 서버 자격증명. local.properties 또는 환경변수에서 읽고, 저장소에는 올리지 않는다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(key: String): String = (localProps.getProperty(key) ?: System.getenv(key) ?: "")
    .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "app.side"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.side"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${secret("PLEOS_CLIENT_ID")}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${secret("PLEOS_CLIENT_SECRET")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 공모전 시연용 APK를 Releases에 올리기 위해 debug 키로 서명한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // 단위 테스트에서 assets/career_test_demo.json을 실제로 읽어 보려고(안드로이드 org.json은 JVM 테스트에서 동작하지 않는다).
    testImplementation("org.json:json:20231013")

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)
    implementation(libs.pleos.navi)
}
