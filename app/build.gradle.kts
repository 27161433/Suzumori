import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 簽章資訊只放在本機的 keystore.properties（已列入 .gitignore）。
// 檔案不存在時，release 建置不會簽章。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.nakisuzu.suzumori"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nakisuzu.suzumori"
        minSdk = 33
        // 不能改。targetSdk 23 以上的 App 寫入非公開的 Settings.System key
        // （MILLET_NO_RESTRICT_APP、com.miui.game.allowlist）時，SettingsProvider
        // 會拋出 IllegalArgumentException；targetSdk 22 以下只會記錄警告並允許寫入。
        // 本 App 的核心功能完全依賴這個相容行為。
        targetSdk = 22
        versionCode = 8
        versionName = "1.1.1"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // 介面只有英文（預設）、繁體中文與簡體中文，與 res/xml/locales_config.xml 一致。
    // 依賴套件裡其他語言的字串一併移除，避免畫面混雜沒有翻譯的語言。
    androidResources {
        localeFilters += listOf("en", "zh-rTW", "zh-rCN")
    }

    lint {
        // targetSdk 固定為 22 是刻意的設計（見 defaultConfig 的說明），
        // 以下檢查只是在提醒 targetSdk 過舊，針對性停用，其他檢查維持預設。
        disable += "ExpiredTargetSdkVersion"
        disable += "OldTargetApi"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
