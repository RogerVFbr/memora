plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.memora"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "br.com.memora"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha.1"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Memora Dev")
        }
        release { resValue("string", "app_name", "Memora") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        warningsAsErrors = true
        // Versions are deliberately pinned; upgrades require compatibility review.
        disable += "GradleDependency"
    }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
dependencyLocking { lockAllConfigurations() }
